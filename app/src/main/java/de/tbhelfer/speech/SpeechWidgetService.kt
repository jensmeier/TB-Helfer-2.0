package de.tbhelfer.speech

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.streckenobjekte.BaustellenObjektLogik
import de.tbhelfer.server.BtbServerStateStore
import de.tbhelfer.server.recovery.RecoveryWorkspace
import de.tbhelfer.ui.bautagebuch.BtbActiveContext
import kotlinx.coroutines.*
import java.util.UUID

class SpeechWidgetService:Service() {
    companion object { const val TOGGLE="de.tbhelfer.speech.TOGGLE";const val STOP="de.tbhelfer.speech.STOP";private const val CHANNEL="speech_capture";private const val ID=260089 }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var session:SpeechSession?=null
    private var target:BtbActiveContext.Result?=null
    private var owner:String?=null
    private var draftId=UUID.randomUUID().toString()
    private var busy=false
    private var ending=false
    private var persisted=false
    private var lastText=""
    private var nodes=emptyList<Pair<Long,List<String>>>()
    private var lastMessage=""
    private var signaled=false
    override fun attachBaseContext(newBase:Context){super.attachBaseContext(RecoveryWorkspace.wrap(newBase))}
    override fun onBind(intent:Intent?)=null
    override fun onCreate(){
        super.onCreate()
        if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL,"Sprachaufnahme",NotificationManager.IMPORTANCE_LOW))
    }
    private fun notification(message:String,ongoing:Boolean):Notification {
        val builder=NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_speech_widget)
            .setContentTitle("TB-Helfer · Spracheingabe").setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setOngoing(ongoing).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(PendingIntent.getActivity(this,ID,SpeechWidgetActivity.intent(this,true),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        if(ongoing)builder.addAction(0,"Beenden",PendingIntent.getService(this,ID,
            Intent(this,SpeechWidgetService::class.java).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        else builder.setAutoCancel(true)
        return builder.build()
    }
    private fun postNotification(message:String,ongoing:Boolean) {
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        getSystemService(NotificationManager::class.java).notify(ID,notification(message,ongoing))
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(busy) { if(!ending) { ending=true;session?.stop() ?: finish("Aufnahme vor dem Start beendet",null) };return START_NOT_STICKY }
        if(intent?.action!=TOGGLE){stopSelf();return START_NOT_STICKY}
        busy=true
        try {
            if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)throw SecurityException("microphone permission")
            val n=notification("Aufnahme wird vorbereitet …",true)
            if(Build.VERSION.SDK_INT>=30)startForeground(ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(ID,n)
        } catch(_:Exception) {
            SpeechDiagnostics.error(this,1001)
            Toast.makeText(this,"Mikrofonstart nicht erlaubt. Bitte Spracheingabe in der App prüfen.",Toast.LENGTH_LONG).show()
            stopSelf();return START_NOT_STICKY
        }
        SpeechWidgetProvider.capture.value=SpeechWidgetProvider.Capture(busy=true)
        scope.launch {
            try {
                owner=SpeechDraftStore.owner(this@SpeechWidgetService)
                if(owner==null || RecoveryWorkspace.blocked() || !SpeechDraftStore.enabled(this@SpeechWidgetService)) {
                    finish("Spracheingabe nicht bereit. Anmeldung und Einstellungen prüfen.",1001);return@launch
                }
                target=withContext(Dispatchers.IO){BtbActiveContext.laden(this@SpeechWidgetService)}
                if(ending)return@launch
                val t=target
                if(t==null || BtbServerStateStore.state(this@SpeechWidgetService,t.btbId)?.readOnly==true) {
                    finish("Kein beschreibbarer aktiver BTB. Bitte zuerst einen BTB öffnen.",null);return@launch
                }
                nodes=withContext(Dispatchers.IO) {
                    val root=ServiceLocator.baustellenBaumRepository.hauptNachAltBaustelleId(t.baustelleId)
                    root?.let { ServiceLocator.baustellenBaumRepository.alleKnotenZuRootSofort(it.id) }
                        .orEmpty().filter { it.id!=root?.id }.map { it.id to listOfNotNull(BaustellenObjektLogik.unterbaustellenName(it),it.knotenName,it.gleisOderWeiche) }
                }
                if(ending)return@launch
                val local=runCatching { Build.VERSION.SDK_INT>=31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this@SpeechWidgetService) }.getOrDefault(false)
                val cm=getSystemService(ConnectivityManager::class.java)
                val online=cm.activeNetwork?.let { cm.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) }==true
                if(SpeechDraftPolicy.route(local,SpeechDraftStore.online(this@SpeechWidgetService),online)==SpeechDraftPolicy.Route.BLOCKED) {
                    finish("Offline-Sprachdienst fehlt. Online-Erkennung benötigt deine Freigabe und Internet.",null);return@launch
                }
                SpeechWidgetProvider.capture.value=SpeechWidgetProvider.Capture(true,false,t.btbId,t.baustelleId)
                session=SpeechSession(this@SpeechWidgetService,local,
                    allowed={ owner==SpeechDraftStore.owner(this@SpeechWidgetService) && !RecoveryWorkspace.blocked() &&
                        SpeechDraftStore.enabled(this@SpeechWidgetService) && (local || SpeechDraftStore.online(this@SpeechWidgetService)) &&
                        ContextCompat.checkSelfPermission(this@SpeechWidgetService,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED &&
                        BtbServerStateStore.state(this@SpeechWidgetService,t.btbId)?.readOnly!=true },
                    onState={message,listening ->
                        SpeechWidgetProvider.capture.value=SpeechWidgetProvider.Capture(true,listening,t.btbId,t.baustelleId)
                        if(listening && !signaled){signaled=true;vibrate(false)}
                        if(message!=lastMessage){lastMessage=message;postNotification("${t.baustelleName}: $message",true)}
                    },onText={save(it)},onFinished={text,error ->
                        save(text)
                        val message=when {
                            error!=null -> if(persisted)"Aufnahme unterbrochen – Text als Entwurf gesichert." else "Aufnahme unterbrochen – kein Text gesichert."
                            persisted -> "Sprachentwurf gesichert. Im BTB später prüfen."
                            else -> "Keine Sprache erkannt – kein Entwurf angelegt."
                        }
                        finish(message,error)
                    })
                session!!.start()
            } catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){finish("Aufnahme konnte nicht vorbereitet werden. Gesicherte Entwürfe bleiben erhalten.",1002)}
        }
        return START_NOT_STICKY
    }
    private fun save(value:String) {
        if(value.isBlank() || (value==lastText && persisted))return
        val t=target ?: return
        val own=owner ?: return
        val firms=t.entry.firmen.filter { !it.isDeleted }.map { it.firmaId to it.name }
        val firm=SpeechMatching.firm(value,firms,SpeechAliasStore.list(this,t.baustelleId))?.id
        persisted=runCatching {
            SpeechDraftStore.put(this,SpeechDraft(draftId,own,t.baustelleId,t.baustelleName,t.btbId,value,
                SpeechMatching.section(value),firm,nodeId=SpeechDraftPolicy.suggestNode(value,nodes)))
            lastText=value;true
        }.getOrDefault(false)
        if(!persisted){SpeechDiagnostics.error(this,1004);session?.cancel()}
    }
    private fun vibrate(end:Boolean) {
        runCatching {
            val v=getSystemService(Vibrator::class.java) ?: return
            val pattern=if(end)longArrayOf(0,60,80,60) else longArrayOf(0,70)
            if(Build.VERSION.SDK_INT>=26)v.vibrate(VibrationEffect.createWaveform(pattern,-1)) else @Suppress("DEPRECATION") v.vibrate(pattern,-1)
        }
    }
    private fun finish(message:String,error:Int?) {
        ending=true
        if(error!=null)SpeechDiagnostics.error(this,error)
        SpeechWidgetProvider.capture.value=SpeechWidgetProvider.Capture()
        if(signaled)vibrate(true)
        if(Build.VERSION.SDK_INT>=24)stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        runCatching { postNotification(message,false) }
        Toast.makeText(this,message,Toast.LENGTH_LONG).show()
        stopSelf()
    }
    override fun onDestroy(){
        // No sticky restart: process/task termination must never restart a microphone silently.
        val wasEnding=ending;ending=true
        if(!wasEnding)SpeechDiagnostics.error(this,1003)
        session?.cancel();session=null;scope.cancel()
        SpeechWidgetProvider.capture.value=SpeechWidgetProvider.Capture()
        super.onDestroy()
    }
}
