package de.tbhelfer.vermessung

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.roundToInt

/**
 * Zusätzliche Sensor-Hilfen für die Automatik.
 * - Schrittzähler nur als kurze Fortschritts-Hilfe
 * - Kompass/Heading nur als schwacher Richtungs-Filter
 */
object SensorLiveQuelle {

    fun hatAktivitaetsBerechtigung(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    }

    fun schrittZaehlerVerfuegbar(context: Context): Boolean {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return false
        return sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }

    fun kompassVerfuegbar(context: Context): Boolean {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return false
        return sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
    }

    fun schrittZaehlerFlow(context: Context): Flow<Float> = callbackFlow {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sm == null) {
            close(IllegalStateException("SensorManager fehlt"))
            return@callbackFlow
        }
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor == null) {
            close(IllegalStateException("Schrittzähler nicht verfügbar"))
            return@callbackFlow
        }
        if (!hatAktivitaetsBerechtigung(context)) {
            close(IllegalStateException("ACTIVITY_RECOGNITION fehlt"))
            return@callbackFlow
        }

        var basis: Float? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val raw = event.values.firstOrNull() ?: return
                val start = basis ?: raw.also { basis = it }
                val seitStart = (raw - start).coerceAtLeast(0f)
                trySend(((seitStart * 10f).roundToInt() / 10f)).isSuccess
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sm.unregisterListener(listener) }
    }

    fun headingFlow(context: Context): Flow<Float> = callbackFlow {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sm == null) {
            close(IllegalStateException("SensorManager fehlt"))
            return@callbackFlow
        }
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) {
            close(IllegalStateException("Rotation Vector nicht verfügbar"))
            return@callbackFlow
        }

        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                var azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                if (azimuthDeg < 0f) azimuthDeg += 360f
                trySend(((azimuthDeg * 10f).roundToInt() / 10f)).isSuccess
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sm.unregisterListener(listener) }
    }
}
