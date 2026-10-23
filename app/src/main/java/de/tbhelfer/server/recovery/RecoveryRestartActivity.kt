package de.tbhelfer.server.recovery

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import de.tbhelfer.ui.activation.AktivierungActivity

/** Separate process: none of the old lazy Room/DataStore instances can survive the switch. */
class RecoveryRestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pid = intent.getIntExtra("old_pid", -1)
        if (pid <= 0 || pid == Process.myPid()) { finish(); return }
        Process.killProcess(pid)
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, AktivierungActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }, 500)
    }
}
