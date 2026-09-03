package de.tbhelfer.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import de.tbhelfer.R
import kotlin.math.max

/**
 * Patch45m: Einheitliche Safe-Area-Behandlung fuer Statusleiste, Kameraausschnitt
 * und Android-Navigation.
 *
 * Normale Fragment-Seiten erhalten automatisch sicheren Innenabstand.
 * Die Kamera bleibt randfuellend; nur Info-Text und Bedienfelder werden in den
 * sicheren Bereich geschoben.
 */
object SafeAreaInsets {

    private val selbstVerwalteteFragmente = setOf(
        // Nur Container, keine sichtbare Inhaltsseite: sonst wuerde der NavHost UND
        // das eigentliche Ziel-Fragment gepolstert und die Safe Area doppelt wirken.
        "androidx.navigation.fragment.NavHostFragment",
        "de.tbhelfer.ui.baustelle.BaustelleNeuFragment",
        "de.tbhelfer.ui.baustelle.BaustellenKnotenFragment",
        "de.tbhelfer.ui.bautagebuch.BautagebuchFragment",
        "de.tbhelfer.ui.importexport.ImportExportFragment",
        "de.tbhelfer.ui.suche.SucheFragment"
    )

    fun install(activity: AppCompatActivity) {
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    fragment: Fragment,
                    view: View,
                    savedInstanceState: Bundle?
                ) {
                    val name = fragment.javaClass.name
                    when {
                        name == "de.tbhelfer.ui.kamera.KameraFragment" -> applyCamera(view)
                        name in selbstVerwalteteFragmente -> Unit
                        else -> applyStandard(view)
                    }
                }
            },
            true
        )
    }

    private fun applyStandard(root: View) {
        val baseLeft = root.paddingLeft
        val baseTop = root.paddingTop
        val baseRight = root.paddingRight
        val baseBottom = root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )

            view.setPadding(
                baseLeft + safe.left,
                baseTop + safe.top,
                baseRight + safe.right,
                baseBottom + safe.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun applyCamera(root: View) {
        val status = root.findViewById<View>(R.id.textStatus)
        val buttonRow = root.findViewById<View>(R.id.buttonRow)
        val footer = root.findViewById<View>(R.id.footer)

        val statusBaseTop = (status?.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0
        val buttonsBaseBottom = (buttonRow?.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
        val footerBaseBottom = (footer?.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
        val extra = dp(root, 8)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )

            status?.let { setTopMargin(it, max(statusBaseTop, safe.top + extra)) }
            buttonRow?.let { setBottomMargin(it, max(buttonsBaseBottom, safe.bottom + extra)) }
            footer?.let { setBottomMargin(it, max(footerBaseBottom, safe.bottom)) }

            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun setTopMargin(view: View, margin: Int) {
        val lp = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (lp.topMargin == margin) return
        lp.topMargin = margin
        view.layoutParams = lp
    }

    private fun setBottomMargin(view: View, margin: Int) {
        val lp = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (lp.bottomMargin == margin) return
        lp.bottomMargin = margin
        view.layoutParams = lp
    }

    private fun dp(view: View, value: Int): Int =
        (value * view.resources.displayMetrics.density + 0.5f).toInt()
}
