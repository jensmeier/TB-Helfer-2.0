package de.tbhelfer.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Bündelt schnelle Autosave-Änderungen, damit das Widget nicht sekündlich neu aufgebaut wird. */
object TBHelferWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pendingJob: Job? = null

    @Synchronized
    fun updateAll(context: Context) {
        val app = context.applicationContext
        pendingJob?.cancel()
        pendingJob = scope.launch {
            delay(1_200L)
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, CurrentSiteWidgetProvider::class.java))
            if (ids.isNotEmpty()) CurrentSiteWidgetProvider.updateWidgets(app, manager, ids)
        }
    }
}
