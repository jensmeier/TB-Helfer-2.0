package de.tbhelfer.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import de.tbhelfer.R
import de.tbhelfer.server.ActivationApi
import de.tbhelfer.server.SecureTokenStore
import de.tbhelfer.server.UserDisplayCache
import de.tbhelfer.ui.activation.AktivierungActivity
import de.tbhelfer.ui.style.UiStyleApplier
import de.tbhelfer.widget.CameraWidgetProvider
import de.tbhelfer.widget.CurrentSiteWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HauptActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_NAV_DEST_ID = "de.tbhelfer.EXTRA_NAV_DEST_ID"
        const val EXTRA_NAV_ARGS = "de.tbhelfer.EXTRA_NAV_ARGS"
        const val EXTRA_WIDGET_LAUNCH = "de.tbhelfer.EXTRA_WIDGET_LAUNCH"
        private const val STATE_WIDGET_ROOT_DESTINATION = "widget_root_destination"
    }

    private var widgetRootDestination: Int? = null
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tokenStore = SecureTokenStore(this)
        val deviceToken = tokenStore.load()
        if (deviceToken.isNullOrBlank()) {
            openActivation()
            return
        }

        SafeAreaInsets.install(this)
        setContentView(R.layout.activity_haupt)
        UiStyleApplier.install(this)
        KeyboardAutoScroll.install(this)

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHost.navController
        de.tbhelfer.ui.bautagebuch.BtbSchnellwechselInstaller.install(this)
        de.tbhelfer.ui.bautagebuch.BtbModernLifecycleInstaller.install(this)
        de.tbhelfer.ui.bautagebuch.BtbPatch200031Installer.install(this)
        de.tbhelfer.ui.bautagebuch.BtbPatch200032Installer.install(this)
        widgetRootDestination = savedInstanceState?.getInt(STATE_WIDGET_ROOT_DESTINATION, -1)?.takeIf { it != -1 }
        installWidgetBackHandling()

        if (!handleWidgetConfigurationIntent(intent)) {
            handleNavigationIntent(intent)
        }

        validateDeviceSession(tokenStore, deviceToken)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        widgetRootDestination?.let { outState.putInt(STATE_WIDGET_ROOT_DESTINATION, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!handleWidgetConfigurationIntent(intent)) {
            handleNavigationIntent(intent)
        }
    }

    private fun validateDeviceSession(tokenStore: SecureTokenStore, token: String) {
        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) {
                ActivationApi().checkSessionInfo(token)
            }
            if (session.state == ActivationApi.SessionState.VALID) {
                UserDisplayCache.rememberName(applicationContext, session.userName)
            }
            if (session.state == ActivationApi.SessionState.REVOKED && !isFinishing) {
                tokenStore.clear()
                Toast.makeText(
                    this@HauptActivity,
                    R.string.activation_session_revoked,
                    Toast.LENGTH_LONG
                ).show()
                openActivation()
            }
        }
    }

    private fun openActivation() {
        startActivity(Intent(this, AktivierungActivity::class.java))
        finish()
    }

    private fun handleWidgetConfigurationIntent(intent: Intent?): Boolean {
        val appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return false

        setResult(
            Activity.RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )

        val manager = AppWidgetManager.getInstance(this)
        val provider = manager.getAppWidgetInfo(appWidgetId)?.provider?.className

        return when (provider) {
            CameraWidgetProvider::class.java.name -> {
                val others = manager.getAppWidgetIds(ComponentName(this, CameraWidgetProvider::class.java))
                    .filter { it != appWidgetId }
                if (others.isNotEmpty()) {
                    Toast.makeText(this, "Es kann nur ein TB-Helfer-Kamera-Widget angelegt werden.", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    CameraWidgetProvider.updateOne(this, manager, appWidgetId)
                    setResult(
                        Activity.RESULT_OK,
                        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    )
                    finish()
                }
                true
            }

            CurrentSiteWidgetProvider::class.java.name -> {
                val others = manager.getAppWidgetIds(ComponentName(this, CurrentSiteWidgetProvider::class.java))
                    .filter { it != appWidgetId }
                if (others.isNotEmpty()) {
                    Toast.makeText(this, "Es kann nur ein TB-Helfer-Baustellen-Widget angelegt werden.", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                    navHost?.navController?.navigate(
                        R.id.widgetEinstellungenFragment,
                        bundleOf("appWidgetId" to appWidgetId, "launcherMode" to true)
                    )
                }
                true
            }

            else -> {
                Toast.makeText(this, "Widget konnte nicht eingerichtet werden.", Toast.LENGTH_LONG).show()
                finish()
                true
            }
        }
    }

    private fun handleNavigationIntent(intent: Intent?) {
        val navIntent = intent ?: return
        val destId = navIntent.getIntExtra(EXTRA_NAV_DEST_ID, -1)
        if (destId == -1) return

        val navArgs = if (android.os.Build.VERSION.SDK_INT >= 33) {
            navIntent.getBundleExtra(EXTRA_NAV_ARGS)
        } else {
            @Suppress("DEPRECATION")
            navIntent.getBundleExtra(EXTRA_NAV_ARGS)
        }
        val fromWidget = navIntent.getBooleanExtra(EXTRA_WIDGET_LAUNCH, false)

        if (fromWidget) {
            widgetRootDestination = destId
            if (navController.currentDestination?.id != destId || navArgs != null) {
                navController.navigate(destId, navArgs)
            }
        } else {
            widgetRootDestination = null
            if (navController.currentDestination?.id != destId || navArgs != null) {
                navController.navigate(destId, navArgs)
            }
        }
        navIntent.removeExtra(EXTRA_NAV_DEST_ID)
        navIntent.removeExtra(EXTRA_NAV_ARGS)
        navIntent.removeExtra(EXTRA_WIDGET_LAUNCH)
    }

    private fun installWidgetBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (shouldCloseWidgetTask()) {
                    widgetRootDestination = null
                    finish()
                    return
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })
    }

    private fun shouldCloseWidgetTask(): Boolean {
        val root = widgetRootDestination ?: return false
        return navController.currentDestination?.id == root
    }

    override fun onSupportNavigateUp(): Boolean {
        if (shouldCloseWidgetTask()) {
            widgetRootDestination = null
            finish()
            return true
        }
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
