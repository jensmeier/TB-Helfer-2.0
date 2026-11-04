package de.tbhelfer.speech

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.ui.bautagebuch.BtbActiveContext
import de.tbhelfer.server.recovery.RecoveryWorkspace
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class SpeechWidgetProvider : AppWidgetProvider() {
    data class Capture(val busy:Boolean=false,val listening:Boolean=false,val btb:String?=null,val site:Long?=null)
    override fun onReceive(context:Context,intent:Intent) {
        super.onReceive(context,intent)
        if(intent.action==Intent.ACTION_MY_PACKAGE_REPLACED)refresh(context)
    }
    override fun onUpdate(c:Context,manager:AppWidgetManager,ids:IntArray){install(c);refresh(c)}
    companion object {
        val capture=MutableStateFlow(Capture())
        private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        private var installed=false
        private var pending:Job?=null
        fun install(c:Context) {
            if(installed)return
            installed=true
            val app=c.applicationContext
            scope.launch {
                combine(SpeechDraftStore.changes,ServiceLocator.aktuelleBaustelleSpeicher.aktuelleBaustelleId,capture) { _,_,_->Unit }
                    .collect { refresh(app) }
            }
        }
        fun refresh(c:Context) {
            val app=c.applicationContext
            scope.launch {
                pending?.cancel()
                pending=scope.launch {
                    delay(80)
                    val manager=AppWidgetManager.getInstance(app)
                    val ids=manager.getAppWidgetIds(ComponentName(app,SpeechWidgetProvider::class.java))
                    if(ids.isEmpty())return@launch
                    val running=capture.value
                    val owner=SpeechDraftStore.owner(app)
                    val target=if(owner==null || RecoveryWorkspace.blocked())null else withContext(Dispatchers.IO) {
                        runCatching { BtbActiveContext.laden(app) }.getOrNull()
                    }
                    if(owner!=SpeechDraftStore.owner(app))return@launch
                    val btb=if(running.busy)running.btb else target?.btbId
                    val site=if(running.busy)running.site else target?.baustelleId
                    val count=btb?.let { SpeechDraftStore.list(app,it).size } ?: 0
                    ids.forEach { id ->
                        val v=RemoteViews(app.packageName,R.layout.widget_speech)
                        v.setInt(R.id.widgetSpeechRoot,"setBackgroundResource",if(running.listening)R.drawable.bg_speech_recording else R.drawable.bg_speech_widget)
                        v.setTextViewText(R.id.widgetSpeechStatus,if(running.listening)"Aufnahme" else if(running.busy)"Bitte warten" else "Sprechen")
                        v.setContentDescription(R.id.widgetSpeechRoot,if(running.busy)"Sprachaufnahme beenden" else "Sprachaufnahme starten")
                        val intent=Intent(app,SpeechWidgetService::class.java).setAction(SpeechWidgetService.TOGGLE)
                        val permission=ContextCompat.checkSelfPermission(app,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
                        val pi=if(!permission)PendingIntent.getActivity(app,id,SpeechWidgetActivity.intent(app,false),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                            else if(Build.VERSION.SDK_INT>=26)PendingIntent.getForegroundService(app,id,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                            else PendingIntent.getService(app,id,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        v.setOnClickPendingIntent(R.id.widgetSpeechRoot,pi)
                        v.setViewVisibility(R.id.widgetSpeechCount,if(count>0 && !running.busy)View.VISIBLE else View.GONE)
                        v.setTextViewText(R.id.widgetSpeechCount,count.toString())
                        v.setContentDescription(R.id.widgetSpeechCount,"$count Sprachentwürfe prüfen")
                        v.setOnClickPendingIntent(R.id.widgetSpeechCount,PendingIntent.getActivity(app,10000+id,
                            SpeechWidgetActivity.intent(app,true).putExtra("btb",btb).putExtra("site",site ?: -1L),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                        manager.updateAppWidget(id,v)
                    }
                }
            }
        }
    }
}
