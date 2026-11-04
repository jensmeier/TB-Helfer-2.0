package de.tbhelfer.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import de.tbhelfer.R
import de.tbhelfer.ui.HauptActivity
import de.tbhelfer.ui.bautagebuch.BtbActiveContext
import de.tbhelfer.server.recovery.RecoveryWorkspace
import kotlinx.coroutines.*

/** Only permission/setup and explicit inbox clicks open a screen. Normal widget capture is a service. */
class SpeechWidgetActivity:AppCompatActivity() {
    companion object {
        const val EXTRA_LIST="speechReviewList"
        fun intent(c:Context,review:Boolean)=Intent(c,SpeechWidgetActivity::class.java)
            .putExtra("review",review).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    override fun attachBaseContext(newBase:Context){super.attachBaseContext(RecoveryWorkspace.wrap(newBase))}
    private lateinit var status:TextView
    private val permission=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)startCapture()
        else status.text="Mikrofon nicht freigegeben. Bitte Freigabe erteilen, um über das Widget aufzunehmen."
        SpeechWidgetProvider.refresh(this)
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(32,64,32,32) }
        status=TextView(this).apply{text="Spracheingabe";textSize=20f};box.addView(status)
        box.addView(Button(this).apply{text="Mikrofon freigeben";setOnClickListener{requestPermission()}})
        box.addView(Button(this).apply{text="BTB öffnen";setOnClickListener{openBtb(null)}})
        box.addView(Button(this).apply{text="Schließen";setOnClickListener{finish()}})
        setContentView(box)
        if(intent.getBooleanExtra("review",false)) lifecycleScope.launch {
            val t=withContext(Dispatchers.IO){BtbActiveContext.laden(this@SpeechWidgetActivity)}
            val expected=intent.getStringExtra("btb")
            if(t==null || (expected!=null && expected!=t.btbId))status.text="Der zugehörige BTB ist nicht mehr aktiv. Bitte seine Sprachentwürfe im BTB öffnen."
            else openBtb(t)
        } else if(savedInstanceState==null)requestPermission()
    }
    private fun requestPermission() {
        val list=mutableListOf(Manifest.permission.RECORD_AUDIO)
        if(Build.VERSION.SDK_INT>=33)list.add(Manifest.permission.POST_NOTIFICATIONS)
        permission.launch(list.toTypedArray())
    }
    private fun startCapture() {
        runCatching { ContextCompat.startForegroundService(this,Intent(this,SpeechWidgetService::class.java).setAction(SpeechWidgetService.TOGGLE)) }
            .onSuccess { finish() }.onFailure { status.text="Aufnahme konnte nicht gestartet werden. Bitte erneut versuchen." }
    }
    private fun openBtb(t:BtbActiveContext.Result?) {
        startActivity(Intent(this,HauptActivity::class.java).apply {
            putExtra(HauptActivity.EXTRA_NAV_DEST_ID,R.id.bautagebuchFragment)
            putExtra(HauptActivity.EXTRA_NAV_ARGS,Bundle().apply {
                t?.let { putString("btbId",it.btbId);putString("datumIso",it.entry.datumIso);putString("schichtTyp",it.entry.schicht.name);putBoolean(EXTRA_LIST,true) }
            });addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        });finish()
    }
}
