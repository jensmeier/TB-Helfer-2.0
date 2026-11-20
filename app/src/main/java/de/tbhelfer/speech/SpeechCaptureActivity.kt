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
import android.view.View
import android.graphics.Color
import android.content.res.ColorStateList
import de.tbhelfer.ServiceLocator
import de.tbhelfer.streckenobjekte.BaustellenObjektLogik
import kotlinx.coroutines.flow.first
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
class SpeechCaptureActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(RecoveryWorkspace.wrap(newBase)) }
    companion object {
        const val EXTRA_SETTINGS = "speechSettings"
        const val EXTRA_REVIEW = "speechReviewDraft"
        fun recordingIntent(context: Context, btbId: String? = null, siteId: Long? = null) =
            Intent(context, SpeechCaptureActivity::class.java).apply {
                putExtra("auto", true)
                btbId?.let { putExtra("btb", it) }
                siteId?.let { putExtra("site", it) }
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
    }
    private var settingsMode = false
    private lateinit var heading: TextView
    private lateinit var microphone: TextView
    private lateinit var stop: Button
    private lateinit var review: Button
    private var stopping = false
    private var reviewPending = false
    private var reviewOpened = false
    private var nodeNames = emptyList<Pair<Long, List<String>>>()
    private lateinit var status: TextView
    private lateinit var text: TextView
    private lateinit var start: Button
    private var session: SpeechSession? = null
    private var recordingReady=false
    private var target: BtbActiveContext.Result? = null
    private var owner: String? = null
    private var draftId = UUID.randomUUID().toString()
    private var busy = false
    private var preparing = false
    private var permissionPending = false
    private var autoRequested = false
    private var permissionStart: Boolean? = null
    private var test = false
    private var lastText = ""
    private var offline: Boolean? = null
    private var offlineFailed = false
    private var probe: SpeechRecognizer? = null
    private var waitingTest = false
    private var lastPersisted = false
    private var lastPersistedText = ""
    private var lastDraftWriteMs = 0L
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionPending=false
        if(granted) { permissionStart=waitingTest;window.decorView.post { startWhenVisible() } }
        else show("Mikrofon nicht freigegeben. Bestehende Entwürfe bleiben erhalten.")
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
        if(RecoveryWorkspace.blocked() || SecureTokenStore(this).load().isNullOrBlank()) {
            startActivity(Intent(this,HauptActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish(); return
        }
        settingsMode = intent.getBooleanExtra(EXTRA_SETTINGS, false)
        owner=SpeechDraftStore.owner(this)
        autoRequested=!settingsMode && savedInstanceState==null && intent.getBooleanExtra("auto",false)
        draftId=savedInstanceState?.getString("draft") ?: draftId
        lastText=if(savedInstanceState?.getString("owner")==owner) savedInstanceState?.getString("text").orEmpty() else ""
        reviewPending=savedInstanceState?.getBoolean("reviewPending") ?: false
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,24,24,32) }
        val scroll=ScrollView(this).apply { isFillViewport=true; addView(box) };setContentView(scroll)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val keyboard=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left,bars.top,bars.right,maxOf(bars.bottom,keyboard.bottom));insets
        }
        box.addView(TextView(this).apply { text=if(settingsMode) "Einstellungen · Spracheingabe" else "Spracheingabe";textSize=24f })
        heading=TextView(this).apply { text="BTB wird geladen …";textSize=18f;setPadding(0,16,0,16) };box.addView(heading)
        microphone=TextView(this).apply {
            text="🎙";textSize=64f;gravity=android.view.Gravity.CENTER
            contentDescription="Mikrofon · Aufnahme noch nicht gestartet"
        };box.addView(microphone)
        if(settingsMode) {
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
        }
        status=TextView(this).apply { text="Aufnahme wird vorbereitet …";textSize=18f;setPadding(0,20,0,16) };box.addView(status)
        text=TextView(this).apply { text=lastText;textSize=20f;setPadding(0,12,0,20);setTextIsSelectable(true) };box.addView(text)
        fun button(label:String,action:()->Unit)=Button(this).also {
            it.text=label;it.isAllCaps=false;it.minHeight=(56*resources.displayMetrics.density).toInt()
            it.setOnClickListener { action() };box.addView(it)
        }
        start=button(if(settingsMode) "Testaufnahme starten" else "Aufnahme starten") { begin(settingsMode) };start.isEnabled=false
        stop=button("Aufnahme beenden") { finishListening() }
        stop.backgroundTintList=ColorStateList.valueOf(Color.rgb(180,35,35));stop.setTextColor(Color.WHITE)
        stop.visibility=View.GONE
        review=button("Text und Zuordnung prüfen") { openReview() };review.visibility=View.GONE
        if(settingsMode) {
        button("Gemerkte Firmen-Schreibweisen löschen") {
            val own=SpeechDraftStore.owner(this@SpeechCaptureActivity) ?: return@button
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this@SpeechCaptureActivity)
                .setTitle("Gemerkte Schreibweisen löschen?")
                .setMessage("Nur die bestätigten Firmen-Schreibweisen deiner Anmeldung auf diesem Handy werden gelöscht. BTB-Einträge bleiben erhalten.")
                .setNegativeButton("Abbrechen",null).setPositiveButton("Löschen") { _,_ ->
                    runCatching { SpeechAliasStore.clearOwner(this@SpeechCaptureActivity,own) }
                        .onSuccess { show("Gemerkte Schreibweisen gelöscht.") }.onFailure { show("Schreibweisen konnten nicht gelöscht werden.") }
                }.show()
        }
        button("Gerät erneut prüfen") { checkDevice() }
        button("Deutsch-Unterstützung laden") {
            if(Build.VERSION.SDK_INT>=33 && localService() && !busy) {
                runCatching { (probe ?: SpeechRecognizer.createOnDeviceSpeechRecognizer(this).also { probe=it }).triggerModelDownload(request()) }
                    .onSuccess { show("Download angefragt. Nach Abschluss Gerät erneut prüfen; angefragt bedeutet noch nicht bereit.") }
                    .onFailure { show("Der Sprachdienst bietet diesen Download nicht an.") }
            } else show("Auf diesem Gerät nicht verfügbar oder Aufnahme läuft.")
        }
        } else {
            button("Einstellungen Spracheingabe") {
                stopCapture()
                startActivity(Intent(this,SpeechCaptureActivity::class.java).putExtra(EXTRA_SETTINGS,true))
            }
        }
        button("BTB öffnen") { openBtb(null) }
        lifecycleScope.launch {
            owner=SpeechDraftStore.owner(this@SpeechCaptureActivity)
            target=withContext(Dispatchers.IO) {
                val site=intent.getLongExtra("site",-1L)
                if(site>0L) BtbActiveContext.laden(this@SpeechCaptureActivity,site)
                else BtbActiveContext.laden(this@SpeechCaptureActivity)
            }
            val old=savedInstanceState?.getString("btb") ?: intent.getStringExtra("btb")
            if(old!=null && old!=target?.btbId) target=null
            val t=target
            if(t!=null) {
                val info=withContext(Dispatchers.IO) {
                    val root=ServiceLocator.baustellenBaumRepository.hauptNachAltBaustelleId(t.baustelleId)
                    val nodes=root?.let { ServiceLocator.baustellenBaumRepository.alleKnotenZuRootSofort(it.id) }.orEmpty()
                    val selected=ServiceLocator.aktuelleBaustelleSpeicher.aktuellerKnotenId.first()
                    nodeNames=nodes.filter { it.id!=root?.id }.map { node ->
                        node.id to listOfNotNull(BaustellenObjektLogik.unterbaustellenName(node),node.knotenName,node.gleisOderWeiche)
                    }
                    nodes.firstOrNull { it.id==selected && it.id!=root?.id }
                        ?.let { BaustellenObjektLogik.unterbaustellenName(it) }
                }
                heading.text="${t.baustelleName} · ${t.entry.datumIso}\n" +
                    (info?.let { "Aktuell ausgewählt: $it" } ?: "Unterbaustelle beim Prüfen zuordnen")
                val restored=SpeechDraftStore.list(this@SpeechCaptureActivity,t.btbId).firstOrNull { it.id==draftId }
                if(restored!=null) { lastPersisted=true;lastPersistedText=restored.text;lastText=restored.text;text.text=lastText }
            } else {
                heading.text="Kein passender aktiver BTB\nBitte zuerst einen BTB öffnen."
                autoRequested=false
            }
            updateControls()
            checkDevice()
            startWhenVisible()
        }
    }
    private fun updateControls() {
        if(!::start.isInitialized)return
        start.isEnabled=!busy && !preparing && (settingsMode || target!=null)
        start.visibility=if(busy)View.GONE else View.VISIBLE
        stop.visibility=if(busy)View.VISIBLE else View.GONE
        stop.isEnabled=!stopping
        review.visibility=if(!settingsMode && !busy && lastPersisted)View.VISIBLE else View.GONE
        microphone.setTextColor(if(recordingReady) Color.rgb(180,35,35) else Color.GRAY)
        microphone.contentDescription=if(recordingReady) "Mikrofon · Aufnahme läuft" else "Mikrofon · Aufnahme beendet"
    }
    private fun openBtb(draft: String?) {
        val t=target
        startActivity(Intent(this,HauptActivity::class.java).apply {
            putExtra(HauptActivity.EXTRA_NAV_DEST_ID,R.id.bautagebuchFragment)
            putExtra(HauptActivity.EXTRA_NAV_ARGS,Bundle().apply {
                t?.let { putString("btbId",it.btbId);putString("datumIso",it.entry.datumIso);putString("schichtTyp",it.entry.schicht.name);putLong("baustelleId",it.baustelleId) }
                draft?.let { putString(EXTRA_REVIEW,it) }
            })
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        });finish()
    }
    private fun openReview() {
        if(test || settingsMode || !lastPersisted || reviewOpened || target==null)return
        reviewPending=true
        if(!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))return
        if(owner!=SpeechDraftStore.owner(this) || RecoveryWorkspace.blocked()) {
            show("Anmeldung geändert. Der ursprüngliche Entwurf bleibt erhalten.");return
        }
        reviewOpened=true;reviewPending=false
        openBtb(draftId)
    }
    private fun finishListening() {
        if(!busy || stopping)return
        stopping=true;updateControls();session?.stop()
    }
    private fun show(s:String) { if(!isDestroyed) status.text=s }
    private fun checkDevice() {
        if(busy || preparing){show("Aufnahme zuerst beenden.");return}
        val device=localService();offline=if(device)null else false
        fun report(extra:String) { if(busy || isDestroyed || isFinishing || !settingsMode)return;show("${target?.baustelleName ?: "Kein aktiver BTB – nur Testaufnahme"}\n$extra\nRauschfilter vom Gerät angeboten: ${if(NoiseSuppressor.isAvailable()) "ja" else "nein"}. Verarbeitung im Sprachdienst: nicht separat steuerbar.\nMikrofon: ${if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) "freigegeben" else "noch nicht freigegeben"}") }
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
    private fun startWhenVisible() {
        if(!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))return
        if(reviewPending){openReview();return}
        val requested=permissionStart
        if(requested!=null){permissionStart=null;begin(requested)}
        else if(autoRequested && target!=null){autoRequested=false;begin(false)}
    }
    override fun onPostResume(){super.onPostResume();window.decorView.post { startWhenVisible() }}
    private fun begin(testOnly:Boolean) {
        if(!SpeechDraftPolicy.mayStart(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED),busy,preparing,permissionPending))return
        if(!testOnly && target==null){show("Bitte zuerst einen aktiven BTB öffnen.");return}
        if(!SpeechDraftStore.enabled(this)){show("Spracheingabe ist ausgeschaltet.");return}
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){waitingTest=testOnly;permissionPending=true;permission.launch(Manifest.permission.RECORD_AUDIO);return}
        preparing=true;start.isEnabled=false
        lifecycleScope.launch {
            try {
            if(!testOnly) {
                val active=withContext(Dispatchers.IO){BtbActiveContext.laden(this@SpeechCaptureActivity,target!!.baustelleId)}
                if(active==null || active.btbId!=target?.btbId || owner!=SpeechDraftStore.owner(this@SpeechCaptureActivity) || BtbServerStateStore.state(this@SpeechCaptureActivity,active.btbId)?.readOnly==true || RecoveryWorkspace.blocked()) {
                    show("Kein passender beschreibbarer aktiver BTB. Bitte BTB erneut öffnen.");return@launch
                }
            }
            if(!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))return@launch
            val useLocal=localService() && offline!=false && !offlineFailed
            val route=SpeechDraftPolicy.route(useLocal,SpeechDraftStore.online(this@SpeechCaptureActivity),connected())
            if(route==SpeechDraftPolicy.Route.BLOCKED){show("Deutsch offline nicht bereit. Für Online-Erkennung Internet und Onlinefreigabe erforderlich.");return@launch}
            if(!useLocal && !runCatching { SpeechRecognizer.isRecognitionAvailable(this@SpeechCaptureActivity) }.getOrDefault(false)){show("Kein Sprachdienst installiert.");return@launch}
            test=testOnly;lastText="";lastPersisted=false;lastPersistedText="";lastDraftWriteMs=0L;text.text="";draftId=UUID.randomUUID().toString();reviewOpened=false;reviewPending=false;stopping=false
            session=SpeechSession(this@SpeechCaptureActivity,useLocal,
                allowed={ !RecoveryWorkspace.blocked() && owner==SpeechDraftStore.owner(this@SpeechCaptureActivity) &&
                    SpeechDraftStore.enabled(this@SpeechCaptureActivity) && (useLocal || SpeechDraftStore.online(this@SpeechCaptureActivity)) },
                onState={message,ready -> recordingReady=ready;updateControls();show(if(test) "Test · $message" else message)},
                onText={value -> accept(Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,arrayListOf(value)) })},
                onFinished={value,error ->
                    accept(Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,arrayListOf(value)) }, forcePersist = true)
                    busy=false;stopping=false;recordingReady=false;updateControls()
                    if(error!=null) {
                        SpeechDiagnostics.error(this@SpeechCaptureActivity,error)
                        if(error==SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error==SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)offlineFailed=true
                        show(if(lastPersisted) "Aufnahme unterbrochen – Text gesichert. Bitte prüfen." else "Aufnahme unterbrochen – kein Text gesichert. Bitte erneut versuchen.")
                    } else if(!test && lastPersisted)openReview()
                    else show(if(test) "Test beendet – kein BTB-Eintrag." else "Keine Sprache erkannt – kein Entwurf angelegt.")
                })
            busy=true;updateControls();session!!.start()

            } finally { preparing=false;updateControls() }
        }
    }
    private fun accept(bundle:Bundle?, forcePersist: Boolean = false) {
        val value=bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.take(12000) ?: return
        if(value.isBlank())return
        if(value != lastText) { lastText=value; text.text=value }
        if(test)return
        if(value == lastPersistedText && lastPersisted) return
        // Partial callbacks may arrive several times per second. Limit writes
        // to disk while always persisting the final full recognition result.
        val now = android.os.SystemClock.elapsedRealtime()
        if(!forcePersist && lastPersisted && now-lastDraftWriteMs < 350L) return
        val t=target ?: return
        val own=owner ?: return
        val firms=t.entry.firmen.filter{!it.isDeleted}.map{it.firmaId to it.name}
        val section=SpeechMatching.section(value)
        val firm=SpeechMatching.firm(value,firms,SpeechAliasStore.list(this,t.baustelleId))?.id
        val saved=runCatching { SpeechDraftStore.put(this,SpeechDraft(draftId,own,t.baustelleId,t.baustelleName,t.btbId,value,section,firm,nodeId=SpeechDraftPolicy.suggestNode(value,nodeNames)));true }
            .getOrDefault(false)
        if(saved) { lastPersisted=true;lastPersistedText=value;lastDraftWriteMs=now }
        else show("Text konnte nicht als Entwurf gesichert werden. Bitte Text prüfen und Aufnahme wiederholen.")
    }
    private fun stopCapture() { session?.cancel();session=null;busy=false;recordingReady=false;stopping=false;updateControls() }
    override fun onStop(){autoRequested=false;stopCapture();super.onStop()}
    override fun onDestroy(){session?.cancel();probe?.destroy();super.onDestroy()}
    override fun onSaveInstanceState(outState:Bundle){
        super.onSaveInstanceState(outState)
        outState.putString("draft",draftId);outState.putString("text",lastText)
        outState.putString("btb",target?.btbId);outState.putString("owner",owner)
        outState.putBoolean("reviewPending",reviewPending)
    }
}

