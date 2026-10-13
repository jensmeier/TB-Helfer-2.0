package de.tbhelfer.ui.bautagebuch

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.WeakHashMap

/**
 * Android 200034:
 * - historische Foto-Lesewege sehen bei modernen BTBs nur die exakt aktive btbId,
 * - allgemeine Baustellenfotos ohne btbId koennen dadurch nicht mehr in einen BTB hineinrutschen,
 * - bewusst aus dem Verlauf geoeffnete BTBs loesen den 200033-Inaktivmarker sofort,
 * - Startmenue kann den BTB-Bildschirm mit einer einmaligen Aktion NEW/OPEN oeffnen.
 */
object BtbPatch200034Installer {

    const val ARG_AUTO_ACTION = "btbAutoAction"
    const val AUTO_NEW = "NEW"
    const val AUTO_OPEN = "OPEN"

    private val boundViews = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val owners = WeakHashMap<View, String>()

    fun install(activity: FragmentActivity) {
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    f: Fragment,
                    v: View,
                    savedInstanceState: Bundle?
                ) {
                    if (f is BautagebuchFragment) bind(f, v)
                }

                override fun onFragmentViewDestroyed(fm: FragmentManager, f: Fragment) {
                    if (f is BautagebuchFragment) {
                        f.view?.let { view -> owners.remove(view)?.let(BtbFotoQueryScope::loeschen) }
                    }
                }
            },
            true
        )
        activity.supportFragmentManager.fragments.forEach(::bindRecursively)
    }

    private fun bindRecursively(fragment: Fragment) {
        if (fragment is BautagebuchFragment) fragment.view?.let { bind(fragment, it) }
        runCatching { fragment.childFragmentManager.fragments }.getOrDefault(emptyList()).forEach(::bindRecursively)
    }

    private fun bind(fragment: BautagebuchFragment, root: View) {
        if (!boundViews.add(root)) return
        val owner = "btb-view-${System.identityHashCode(root)}"
        owners[root] = owner

        // Beim bewussten Oeffnen aus Verlauf/Schnellwechsel kennen wir btbId und Baustelle bereits
        // als Argumente. So steht der strikte Fotofilter schon vor dem ersten manuellen Editor-Tap.
        val explicitBtbId = fragment.arguments?.getString("btbId")?.trim().orEmpty()
        val explicitSiteId = fragment.arguments?.getLong("baustelleId", -1L) ?: -1L
        if (explicitBtbId.isNotBlank() && !explicitBtbId.startsWith("legacy:") && explicitSiteId > 0L) {
            BtbFotoQueryScope.setzen(owner, explicitSiteId, explicitBtbId)
        }

        startCanonicalScopeWatcher(fragment, root, owner)
        runAutoActionOnce(fragment, root)
    }

    private fun startCanonicalScopeWatcher(fragment: BautagebuchFragment, root: View, owner: String) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            fragment.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    while (isActive) {
                        val active = withContext(Dispatchers.IO) {
                            BtbActiveContext.laden(fragment.requireContext().applicationContext)
                        }
                        if (active == null) {
                            BtbFotoQueryScope.loeschen(owner)
                        } else {
                            BtbFotoQueryScope.setzen(owner, active.baustelleId, active.btbId)

                            // Bewusste Reaktivierung: nicht auf den 200033-Watcher warten. Damit kann
                            // Abschluss/Vorschau sofort wieder denselben aktiven Pointer verwenden.
                            val explicit = fragment.arguments?.getString("btbId")?.trim().orEmpty()
                            if (explicit.isNotBlank() && explicit == active.btbId) {
                                withContext(Dispatchers.IO) {
                                    BtbActivePointerControl.inaktivMarkerLoeschen(
                                        fragment.requireContext().applicationContext,
                                        active.baustelleId,
                                        active.baustelleName
                                    )
                                }
                            }
                        }
                        delay(300L)
                    }
                } finally {
                    BtbFotoQueryScope.loeschen(owner)
                }
            }
        }
    }

    private fun runAutoActionOnce(fragment: BautagebuchFragment, root: View) {
        val action = fragment.arguments?.getString(ARG_AUTO_ACTION)?.trim().orEmpty()
        if (action.isBlank()) return
        fragment.arguments?.remove(ARG_AUTO_ACTION)

        root.postDelayed({
            if (!fragment.isAdded || fragment.view == null) return@postDelayed
            when (action) {
                AUTO_NEW -> root.findViewById<View>(R.id.btnQuickNewBtb)?.performClick()
                AUTO_OPEN -> root.findViewById<View>(R.id.cardCurrentReport)?.performClick()
            }
        }, 220L)
    }
}
