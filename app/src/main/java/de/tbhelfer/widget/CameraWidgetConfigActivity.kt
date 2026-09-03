package de.tbhelfer.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Sofort-Konfiguration: erlaubt maximal ein Kamera-Widget und schließt direkt wieder. */
class CameraWidgetConfigActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(
            RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val manager = AppWidgetManager.getInstance(this)
        val otherIds = manager.getAppWidgetIds(ComponentName(this, CameraWidgetProvider::class.java))
            .filter { it != widgetId }
        if (otherIds.isNotEmpty()) {
            Toast.makeText(this, "Es kann nur ein TB-Helfer-Kamera-Widget angelegt werden.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        CameraWidgetProvider.updateOne(this, manager, widgetId)
        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        )
        finish()
    }
}
