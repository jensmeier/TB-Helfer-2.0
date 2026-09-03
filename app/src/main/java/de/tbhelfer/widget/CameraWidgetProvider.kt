package de.tbhelfer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import de.tbhelfer.R
import de.tbhelfer.ui.HauptActivity

class CameraWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateOne(context, appWidgetManager, it) }
    }

    companion object {
        fun updateOne(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_camera_1x1)
            val intent = Intent(context, HauptActivity::class.java).apply {
                putExtra(HauptActivity.EXTRA_NAV_DEST_ID, R.id.kameraFragment)
                putExtra(HauptActivity.EXTRA_WIDGET_LAUNCH, true)
                putExtra(HauptActivity.EXTRA_NAV_ARGS, Bundle().apply {
                    putBoolean("widgetCameraLaunch", true)
                })
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

            val flags = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                else -> PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pi = PendingIntent.getActivity(context, 1001, intent, flags)
            views.setOnClickPendingIntent(R.id.widgetCameraRoot, pi)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
