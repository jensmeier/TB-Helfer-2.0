package de.tbhelfer.ui.style

import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import com.google.android.material.button.MaterialButton
import androidx.core.view.ViewCompat
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import de.tbhelfer.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * GLOBAL: wendet die Ansicht-Einstellungen (Buttonfarbe/Textfarbe/Schriftgröße)
 * auf ALLE Buttons in der ganzen App an – auch nach Navigation.
 */
object UiStyleApplier {

    private var job: Job? = null

    // Cache der letzten Werte, damit neue Screens sofort korrekt gestylt werden
    @Volatile private var lastButtonColor: Int? = null
    @Volatile private var lastTextColor: Int? = null
    @Volatile private var lastTextSizeSp: Int? = null

    fun install(activity: FragmentActivity) {

        // Bei neuen Fragment-Views direkt anwenden (mit gecachten Farben!)
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    f: Fragment,
                    v: View,
                    savedInstanceState: android.os.Bundle?
                ) {
                    applyToHierarchy(
                        root = v,
                        buttonColor = lastButtonColor,
                        textColor = lastTextColor,
                        textSizeSp = lastTextSizeSp
                    )
                }
            },
            true
        )

        // Einstellungen beobachten und auf den ganzen Screen anwenden
        job?.cancel()
        job = activity.lifecycleScope.launch {
            ServiceLocator.ansichtEinstellungenSpeicher.einstellungen.collectLatest { cfg ->
                lastButtonColor = cfg.menuButtonColor
                lastTextColor = cfg.menuTextColor
                lastTextSizeSp = cfg.menuTextSizeSp

                val root = activity.window?.decorView ?: return@collectLatest
                applyToHierarchy(
                    root = root,
                    buttonColor = cfg.menuButtonColor,
                    textColor = cfg.menuTextColor,
                    textSizeSp = cfg.menuTextSizeSp
                )
            }
        }
    }

    private fun applyToHierarchy(
        root: View,
        buttonColor: Int? = null,
        textColor: Int? = null,
        textSizeSp: Int? = null
    ) {
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                applyToHierarchy(root.getChildAt(i), buttonColor, textColor, textSizeSp)
            }
        }

        if (root is MaterialButton) {
            applyToButton(root, buttonColor, textColor, textSizeSp)
        }
    }

    private fun applyToButton(btn: MaterialButton, buttonColor: Int?, textColor: Int?, textSizeSp: Int?) {
        // Buttons sind immer TB-Blau + weiße Schrift (Menü-Farbe ist entfernt).
        val ctx = btn.context
        val blue = try { ctx.getColor(de.tbhelfer.R.color.tb_blue) } catch (_: Throwable) { null }
        val tint = buttonColor ?: blue
        if (tint != null) {
            ViewCompat.setBackgroundTintList(btn, ColorStateList.valueOf(tint))
        }
        btn.setTextColor(android.graphics.Color.WHITE)

        // AutoSize nur für echte Menü-Buttons (MaterialButton)
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            btn,
            10,
            20,
            1,
            TypedValue.COMPLEX_UNIT_SP,
        )
    }
}
