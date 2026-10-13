package de.tbhelfer.server

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.tbhelfer.R
import de.tbhelfer.einstellungen.SyncBenachrichtigungSpeicher
import de.tbhelfer.ui.HauptActivity
import java.util.concurrent.atomic.AtomicInteger

/**
 * Gerätespezifische Benachrichtigungen für neue gemeinsame Baustellendaten.
 *
 * TEXT_TON: normale Android-Benachrichtigung mit Text und Ton.
 * NUR_TON: nur System-Benachrichtigungston, ohne sichtbare Meldung.
 * AUS: keine Ausgabe.
 */
object CollabBenachrichtigungen {
    private const val CHANNEL_ID = "tbhelfer_collab_text_ton"
    private const val CHANNEL_NAME = "Baustellen-Synchronisierung"
    private val notificationId = AtomicInteger(4200)

    data class Ereignis(
        val entityType: String,
        val changedByName: String?
    )

    fun anzeigen(context: Context, ereignis: Ereignis) {
        when (SyncBenachrichtigungSpeicher.laden(context)) {
            SyncBenachrichtigungSpeicher.Modus.AUS -> Unit
            SyncBenachrichtigungSpeicher.Modus.NUR_TON -> spieleTon(context)
            SyncBenachrichtigungSpeicher.Modus.TEXT_TON -> zeigeTextUndTon(context, ereignis)
        }
    }

    private fun zeigeTextUndTon(context: Context, ereignis: Ereignis) {
        channelSicherstellen(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // Android blockiert sichtbare Meldungen ohne Laufzeitfreigabe. Der Nutzer soll
            // trotzdem nicht unbemerkt bleiben, deshalb fällt TEXT_TON auf Ton zurück.
            spieleTon(context)
            return
        }

        val actor = ereignis.changedByName?.trim().orEmpty().ifBlank { "Nutzer" }
        val text = when (ereignis.entityType.uppercase()) {
            "DEFECT" -> "Neuer Mangel von $actor"
            "ACCEPTANCE" -> "Neue Abnahme von $actor"
            "BTB" -> "Neuer BTB-Eintrag von $actor"
            else -> "Neue Baustellenänderung von $actor"
        }

        val intent = Intent(context, HauptActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check_24)
            .setContentTitle("TB-Helfer")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId.incrementAndGet(), notification)
    }

    private fun channelSicherstellen(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Neue Mängel, Abnahmen und BTB-Einträge"
            }
        )
    }

    private fun spieleTon(context: Context) {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context.applicationContext, uri)?.play()
        }
    }
}
