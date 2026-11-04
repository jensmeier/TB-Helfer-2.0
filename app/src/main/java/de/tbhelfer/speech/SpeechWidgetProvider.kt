package de.tbhelfer.speech

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import de.tbhelfer.R

class SpeechWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val manager = AppWidgetManager.getInstance(context)
            onUpdate(context, manager, manager.getAppWidgetIds(android.content.ComponentName(context, SpeechWidgetProvider::class.java)))
        }
    }
    override fun onUpdate(c:Context,manager:AppWidgetManager,ids:IntArray) {
        ids.forEach { id ->
            val views=RemoteViews(c.packageName,R.layout.widget_speech)
            val intent=SpeechCaptureActivity.recordingIntent(c).setData(Uri.parse("tbhelfer://speech/widget/$id"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            views.setOnClickPendingIntent(R.id.widgetSpeechRoot,PendingIntent.getActivity(c,id,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            manager.updateAppWidget(id,views)
        }
    }
}

