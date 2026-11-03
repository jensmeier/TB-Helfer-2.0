package de.tbhelfer.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.NoiseSuppressor
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.speech.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import de.tbhelfer.R
import de.tbhelfer.server.SecureTokenStore
import de.tbhelfer.server.BtbServerStateStore
import de.tbhelfer.server.recovery.RecoveryWorkspace
import de.tbhelfer.ui.HauptActivity
import de.tbhelfer.ui.bautagebuch.BtbActiveContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** Foreground-only capture; recognition output is a draft, never a BTB mutation. */
class SpeechCaptureActivity : AppCompatActivity(), RecognitionListener {
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(RecoveryWorkspace.wrap(newBase)) }
    private lateinit var status: TextView
    private lateinit var text: TextView
    private lateinit var start: Button
    private var recognizer: SpeechRecognizer? = null
    private var target: BtbActiveContext.Result? = null
    private var owner: String? = null
    private var draftId = UUID.randomUUID().toString()
    private var busy = false
    private var test = false
    private var lastText = ""
    private var offline: Boolean? = null
    private var offlineFailed = false
    private var probe: SpeechRecognizer? = null
    private var waitingTest = false
    private var lastPersisted = false
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) begin(waitingTest) else show("Mikrofon nicht freigegeben. Bestehende Entwürfe bleiben erhalten.")
    }
    private fun connected(): Boolean {
        val cm=getSystemService(ConnectivityManager::class.java)
        val n=cm.activeNetwork ?: return false
        return cm.getNetworkCapabilities(n)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true
    }
    private fun localService() = runCatching { Build.VERSION.SDK_INT>=31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this) }.getOrDefault(false)
    private fun request() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE,"de-DE").putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(RecoveryWorkspace.blocked() || SecureTokenStore(this).load().isNullOrBlank()) { finish(); return }
        owner=SpeechDraftStore.owner(this)
        draftId=savedInstanceState?.getString("draft") ?: draftId
        lastText=if(savedInstanceState?.getString("owner")==owner) savedInstanceState?.getString("text").orEmpty() else ""
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,24,24,32) }
        val scroll=ScrollView(this).apply { isFillViewport=true; addView(box) };setContentView(scroll)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val keyboard=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left,bars.top,bars.right,maxOf(bars.bottom,keyboard.bottom));insets
        }
        box.addView(TextView(this).apply { text="Spracheingabe";textSize=24f })
        box.addView(Switch(this).apply { text="Spracheingabe aktivieren";isChecked=SpeechDraftStore.enabled(this@SpeechCaptureActivity)
            setOnCheckedChangeListener { _,v -> SpeechDraftStore.setting(this@SpeechCaptureActivity,"enabled",v);if(!v)stopCapture() } })
        box.addView(Switch(this).apply { text="Online-Erkennung erlauben";isChecked=SpeechDraftStore.online(this@SpeechCaptureActivity)
            setOnCheckedChangeListener { _,v ->
                if(v) com.google.android.material.dialog.MaterialAlertDialogBuilder(this@SpeechCaptureActivity)
                    .setTitle("Online-Erkennung erlauben?").setMessage("Der Android-Sprachdienst kann Audio an seinen Anbieter senden. Das ist nicht der TB-Helfer-Server.")
                    .setNegativeButton("Abbrechen") { _,_ -> isChecked=false }
                    .setPositiveButton("Erlauben") { _,_ -> SpeechDraftStore.setting(this@SpeechCaptureActivity,"online",true) }.show()
                else { SpeechDraftStore.setting(this@SpeechCaptureActivity,"online",false);stopCapture() }
            } })
        status=TextView(this).apply { text="Gerät wird geprüft …";setPadding(0,20,0,16) };box.addView(status)
        text=TextView(this).apply { text=lastText;textSize=18f;setPadding(0,12,0,20) };box.addView(text)
        fun button(label:String,action:()->Unit)=Button(this).also { it.text=label;it.setOnClickListener { action() };box.addView(it) }
        start=button("Aufnahme starten") { begin(false) };start.isEnabled=false
        button("Aufnahme beenden") { recognizer?.stopListening() }
        button("Gerät erneut prüfen") { checkDevice() }
        button("Testaufnahme – kein BTB-Eintrag") { begin(true) }
        button("Deutsch-Unterstützung laden") {
            if(Build.VERSION.SDK_INT>=33 && localService() && !busy) {
                runCatching { (probe ?: SpeechRecognizer.createOnDeviceSpeechRecognizer(this).also { probe=it }).triggerModelDownload(request()) }
                    .onSuccess { show("Download angefragt. Nach Abschluss Gerät erneut prüfen; angefragt bedeutet noch nicht bereit.") }
                    .onFailure { show("Der Sprachdienst bietet diesen Download nicht an.") }
            } else show("Auf diesem Gerät nicht verfügbar oder Aufnahme läuft.")
        }
        button("BTB öffnen / Entwürfe bearbeiten") {
            if(busy)stopCapture()
            val t=target
            startActivity(Intent(this,HauptActivity::class.java).apply {
                putExtra(HauptActivity.EXTRA_NAV_DEST_ID,R.id.bautagebuchFragment)
                putExtra(HauptActivity.EXTRA_NAV_ARGS,Bundle().apply {
                    t?.let { putString("btbId",it.btbId);putString("datumIso",it.entry.datumIso);putString("schichtTyp",it.entry.schicht.name) }
                }); addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            });finish()
        }
        lifecycleScope.launch {
            owner=SpeechDraftStore.owner(this@SpeechCaptureActivity)
            target=withContext(Dispatchers.IO) { BtbActiveContext.laden(this@SpeechCaptureActivity) }
            val old=savedInstanceState?.getString("btb") ?: intent.getStringExtra("btb")
            if(old!=null && old!=target?.btbId) {target=null;show("BTB gewechselt. Gesicherte Entwürfe bleiben beim ursprünglichen BTB.")}
            start.isEnabled=target!=null
            checkDevice()
            if(savedInstanceState==null && intent.getBooleanExtra("auto",false) && target!=null) begin(false)
        }
    }
    private fun show(s:String) { if(!isDestroyed) status.text=s }
    private fun checkDevice() {
        if(busy){show("Aufnahme zuerst beenden.");return}
        val device=localService();offline=if(device)null else false
        fun report(extra:String) { if(busy || isDestroyed || isFinishing)return;show("${target?.baustelleName ?: "Kein aktiver BTB – nur Testaufnahme"}\n$extra\nRauschfilter vom Gerät angeboten: ${if(NoiseSuppressor.isAvailable()) "ja" else "nein"}. Verarbeitung im Sprachdienst: nicht separat steuerbar.\nMikrofon: ${if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) "freigegeben" else "noch nicht freigegeben"}") }
        if(Build.VERSION.SDK_INT>=33 && device) {
            try {
            probe?.destroy();probe=SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            probe?.checkRecognitionSupport(request(),mainExecutor,object: RecognitionSupportCallback {
                override fun onSupportResult(s:RecognitionSupport) { offline=s.installedOnDeviceLanguages.any { it.startsWith("de",true) };report(if(offline==true) "Deutsch offline bereit" else "Deutsch offline nicht installiert; sonst nur freigegeben online") }
                override fun onError(error:Int) { offline=null;report("Offline-Sprachunterstützung ungeprüft; nur expliziter On-device-Versuch, kein stiller Onlinewechsel") }
            })
            } catch(_:Exception) { offline=null;report("Offlineprüfung nicht verfügbar. Berechtigung und Testaufnahme prüfen.") }
        } else report(if(device) "On-device-Dienst vorhanden; Deutsch noch durch Test prüfen" else "Kein On-device-Dienst: Internet und Onlinefreigabe erforderlich")
    }
    private fun begin(testOnly:Boolean) {
        if(busy)return
        if(!testOnly && target==null){show("Bitte zuerst einen aktiven BTB öffnen.");return}
        if(!SpeechDraftStore.enabled(this)){show("Spracheingabe ist ausgeschaltet.");return}
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){waitingTest=testOnly;permission.launch(Manifest.permission.RECORD_AUDIO);return}
        lifecycleScope.launch {
            if(!testOnly) {
                val active=withContext(Dispatchers.IO){BtbActiveContext.laden(this@SpeechCaptureActivity)}
                if(active==null || active.btbId!=target?.btbId || owner!=SpeechDraftStore.owner(this@SpeechCaptureActivity) || BtbServerStateStore.state(this@SpeechCaptureActivity,active.btbId)?.readOnly==true || RecoveryWorkspace.blocked()) {
                    show("Kein passender beschreibbarer aktiver BTB. Bitte BTB erneut öffnen.");return@launch
                }
            }
            val useLocal=localService() && offline!=false && !offlineFailed
            val route=SpeechDraftPolicy.route(useLocal,SpeechDraftStore.online(this@SpeechCaptureActivity),connected())
            if(route==SpeechDraftPolicy.Route.BLOCKED){show("Deutsch offline nicht bereit. Für Online-Erkennung Internet und Onlinefreigabe erforderlich.");return@launch}
            if(!useLocal && !runCatching { SpeechRecognizer.isRecognitionAvailable(this@SpeechCaptureActivity) }.getOrDefault(false)){show("Kein Sprachdienst installiert.");return@launch}
            test=testOnly;lastText="";lastPersisted=false;text.text="";draftId=UUID.randomUUID().toString()
            runCatching {
                recognizer?.destroy()
                recognizer=if(useLocal && Build.VERSION.SDK_INT>=31)SpeechRecognizer.createOnDeviceSpeechRecognizer(this@SpeechCaptureActivity) else SpeechRecognizer.createSpeechRecognizer(this@SpeechCaptureActivity)
                recognizer!!.setRecognitionListener(this@SpeechCaptureActivity)
                busy=true;start.isEnabled=false
                recognizer!!.startListening(request())
                show("${if(test) "Testaufnahme" else "Aufnahme"} · ${if(useLocal) "auf diesem Gerät" else "Sprachdienst online erlaubt"}\nBitte sprechen. Aufnahme endet bei Sprechpause oder Beenden.")
            }.onFailure { busy=false;start.isEnabled=target!=null;show("Sprachdienst konnte nicht gestartet werden. Bitte erneut prüfen.") }
        }
    }
    private fun accept(bundle:Bundle?) {
        val value=bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.take(12000) ?: return
        if(value.isBlank())return
        if(value==lastText && (test || lastPersisted))return
        lastText=value;text.text=value
        if(test)return
        val t=target ?: return
        val own=owner ?: return
        val (section,firm)=SpeechDraftPolicy.suggest(value,t.entry.firmen.filter{!it.isDeleted}.map{it.firmaId to it.name})
        lastPersisted=runCatching { SpeechDraftStore.put(this,SpeechDraft(draftId,own,t.baustelleId,t.baustelleName,t.btbId,value,section,firm));true }
            .getOrDefault(false)
        if(!lastPersisted)show("Text konnte nicht als Entwurf gesichert werden. Bitte Text prüfen und Aufnahme wiederholen.")
    }
    private fun stopCapture(){ if(busy){busy=false;recognizer?.cancel();start.isEnabled=target!=null;show("Aufnahme beendet. Bereits erkannter Text bleibt als Entwurf erhalten.") } }
    override fun onResults(results:Bundle?){if(!busy)return;accept(results);busy=false;start.isEnabled=target!=null;show(if(test)"Test beendet – kein BTB-Eintrag. Bitte Erkennung prüfen." else if(lastPersisted) "Als Sprachentwurf gesichert. Im BTB prüfen und ausdrücklich speichern." else "Kein Text gesichert. Bitte erneut aufnehmen oder manuell eingeben.")}
    override fun onPartialResults(partialResults:Bundle?){if(busy)accept(partialResults)}
    override fun onError(error:Int){if(!busy)return;busy=false;start.isEnabled=target!=null
        if(error==SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error==SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)offlineFailed=true
        show("Erkennung nicht abgeschlossen (Code $error). Bereits erkannter Text bleibt erhalten. Kein automatischer Onlinewechsel.") }
    override fun onReadyForSpeech(params:Bundle?){};override fun onBeginningOfSpeech(){};override fun onRmsChanged(rmsdB:Float){}
    override fun onBufferReceived(buffer:ByteArray?){};override fun onEndOfSpeech(){};override fun onEvent(eventType:Int,params:Bundle?){}
    override fun onStop(){stopCapture();super.onStop()}
    override fun onDestroy(){recognizer?.destroy();probe?.destroy();super.onDestroy()}
    override fun onSaveInstanceState(outState:Bundle){super.onSaveInstanceState(outState);outState.putString("draft",draftId);outState.putString("text",lastText);outState.putString("btb",target?.btbId);outState.putString("owner",owner)}
}
