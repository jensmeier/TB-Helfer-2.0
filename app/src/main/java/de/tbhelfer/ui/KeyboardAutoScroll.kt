package de.tbhelfer.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.max

/**
 * Zentrale Tastatur-/Scroll-Hilfe fuer Activity-Formulare und separate Dialogfenster.
 *
 * Patch45h:
 * - wartet nicht mehr nur auf WindowInsets, sondern beobachtet zusaetzlich das wirklich
 *   sichtbare Fensterrechteck. Das funktioniert auch auf Geraeten, auf denen die Tastatur
 *   das Fenster ueberlagert statt es sauber zu verkleinern.
 * - scrollt das aktive Eingabefeld anhand echter Bildschirmkoordinaten ueber die Tastatur.
 * - gibt ScrollViews bei offener Tastatur temporaer zusaetzlichen unteren Platz, damit auch
 *   die letzten Felder nach oben gescrollt werden koennen.
 * - kann explizit an Material-/AlertDialog-Fenster angehaengt werden.
 */
object KeyboardAutoScroll {

    private val activityControllers = WeakHashMap<Activity, Controller>()
    private val dialogControllers = WeakHashMap<Dialog, Controller>()

    @Synchronized
    fun install(activity: Activity) {
        if (activityControllers.containsKey(activity)) return
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val controller = Controller(activity.window, root)
        activityControllers[activity] = controller
        controller.install()
    }

    /** Muss nach dialog.show() aufgerufen werden, weil erst dann ein Window existiert. */
    @Synchronized
    fun install(dialog: Dialog) {
        if (dialogControllers.containsKey(dialog)) return
        val window = dialog.window ?: return
        val root = window.findViewById<ViewGroup>(android.R.id.content)
            ?: (window.decorView as? ViewGroup)
            ?: return
        val controller = Controller(window, root)
        dialogControllers[dialog] = controller
        controller.install()
    }

    private class Controller(
        private val window: Window,
        private val root: ViewGroup
    ) {
        private var keyboardVisible = false
        private var keyboardHeightPx = 0
        private var maxObservedWindowHeight = 0
        private var focusedInput: WeakReference<View>? = null
        private var savedScroll: SavedScroll? = null
        private val originalPadding = WeakHashMap<View, Padding>()
        private val visibleFrame = Rect()

        fun install() {
            // Fallback fuer aeltere Android-Versionen; die eigentliche Erkennung unten arbeitet
            // bewusst zusaetzlich ueber das sichtbare Fensterrechteck.
            @Suppress("DEPRECATION") // Legacy adjustResize remains required below edge-to-edge APIs.
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

            root.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
                if (newFocus is EditText && newFocus.isShown) {
                    focusedInput = WeakReference(newFocus)
                    if (!keyboardVisible && savedScroll == null) {
                        savedScroll = captureScroll(newFocus)
                    }
                    // Mehrfach nachsteuern: Fokus kommt oft vor der vollstaendig geoeffneten IME.
                    revealLater(newFocus, 80L)
                    revealLater(newFocus, 220L)
                    revealLater(newFocus, 420L)
                    revealLater(newFocus, 650L)
                }
            }

            ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
                val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
                val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                if (imeVisible && imeBottom > 0) {
                    updateKeyboardState(true, imeBottom)
                }
                focusedInput?.get()?.takeIf { it.isShown && it.hasFocus() }?.let { target ->
                    revealLater(target, 50L)
                    revealLater(target, 180L)
                }
                insets
            }
            ViewCompat.requestApplyInsets(root)

            // Entscheidend fuer Pixel/Poco und andere Geraete mit Overlay-IME:
            // das tatsaechlich sichtbare Fensterrechteck beobachten.
            root.viewTreeObserver.addOnGlobalLayoutListener {
                root.getWindowVisibleDisplayFrame(visibleFrame)
                maxObservedWindowHeight = max(
                    maxObservedWindowHeight,
                    max(root.rootView.height, max(root.height, visibleFrame.bottom))
                )
                val fullHeight = maxObservedWindowHeight.coerceAtLeast(root.rootView.height)
                val obscured = (fullHeight - visibleFrame.bottom).coerceAtLeast(0)
                val threshold = max(dp(120), (fullHeight * 0.15f).toInt())
                val visible = obscured > threshold
                updateKeyboardState(visible, if (visible) obscured else 0)

                if (visible) {
                    focusedInput?.get()?.takeIf { it.isShown && it.hasFocus() }?.let { target ->
                        reveal(target)
                    }
                }
            }
        }

        private fun updateKeyboardState(visible: Boolean, heightPx: Int) {
            val wasVisible = keyboardVisible
            keyboardVisible = visible
            keyboardHeightPx = if (visible) heightPx.coerceAtLeast(0) else 0

            if (visible) {
                focusedInput?.get()?.takeIf { it.isShown && it.hasFocus() }?.let { target ->
                    findScrollParent(target)?.let { applyKeyboardPadding(it) }
                }
            } else if (wasVisible) {
                restorePadding()
                restoreSavedScroll()
            }
        }

        private fun revealLater(target: View, delayMs: Long) {
            target.postDelayed({ reveal(target) }, delayMs)
        }

        private fun reveal(target: View) {
            if (!target.isShown || !target.hasFocus()) return

            val margin = dp(24)
            val scroll = findScrollParent(target)
            if (keyboardVisible && scroll != null) applyKeyboardPadding(scroll)

            val targetRect = Rect()
            if (!target.getGlobalVisibleRect(targetRect)) return
            root.getWindowVisibleDisplayFrame(visibleFrame)

            val visibleTop = visibleFrame.top + margin
            val imeTop = if (keyboardVisible && keyboardHeightPx > 0 && maxObservedWindowHeight > 0) {
                (maxObservedWindowHeight - keyboardHeightPx).coerceAtLeast(visibleFrame.top)
            } else visibleFrame.bottom
            val visibleBottom = minOf(visibleFrame.bottom, imeTop) - margin
            val delta = when {
                targetRect.bottom > visibleBottom -> targetRect.bottom - visibleBottom
                targetRect.top < visibleTop -> targetRect.top - visibleTop
                else -> 0
            }

            if (delta != 0 && scroll != null) {
                when (scroll) {
                    is ScrollView -> scroll.smoothScrollBy(0, delta)
                    is NestedScrollView -> scroll.smoothScrollBy(0, delta)
                }
                // Nach dem Scrollen einmal kontrollieren, weil SmoothScroll asynchron laeuft.
                target.postDelayed({ revealOnceWithoutRepeat(target) }, 140L)
            } else if (delta != 0) {
                val localRect = Rect(0, -margin, target.width, target.height + margin)
                target.requestRectangleOnScreen(localRect, true)
            }
        }

        private fun revealOnceWithoutRepeat(target: View) {
            if (!target.isShown || !target.hasFocus()) return
            val scroll = findScrollParent(target) ?: return
            val margin = dp(24)
            val targetRect = Rect()
            if (!target.getGlobalVisibleRect(targetRect)) return
            root.getWindowVisibleDisplayFrame(visibleFrame)
            val visibleTop = visibleFrame.top + margin
            val imeTop = if (keyboardVisible && keyboardHeightPx > 0 && maxObservedWindowHeight > 0) {
                (maxObservedWindowHeight - keyboardHeightPx).coerceAtLeast(visibleFrame.top)
            } else visibleFrame.bottom
            val visibleBottom = minOf(visibleFrame.bottom, imeTop) - margin
            val delta = when {
                targetRect.bottom > visibleBottom -> targetRect.bottom - visibleBottom
                targetRect.top < visibleTop -> targetRect.top - visibleTop
                else -> 0
            }
            when {
                delta == 0 -> Unit
                scroll is ScrollView -> scroll.scrollBy(0, delta)
                scroll is NestedScrollView -> scroll.scrollBy(0, delta)
            }
        }

        private fun applyKeyboardPadding(scroll: View) {
            if (keyboardHeightPx <= 0) return
            val original = originalPadding.getOrPut(scroll) {
                Padding(scroll.paddingLeft, scroll.paddingTop, scroll.paddingRight, scroll.paddingBottom)
            }
            val extra = keyboardHeightPx + dp(32)
            val desiredBottom = original.bottom + extra
            if (scroll.paddingBottom != desiredBottom) {
                scroll.setPadding(original.left, original.top, original.right, desiredBottom)
                if (scroll is ViewGroup) scroll.clipToPadding = false
            }
        }

        private fun restorePadding() {
            originalPadding.entries.toList().forEach { (view, p) ->
                view.setPadding(p.left, p.top, p.right, p.bottom)
            }
            originalPadding.clear()
        }

        private fun captureScroll(target: View): SavedScroll? {
            return when (val scroll = findScrollParent(target)) {
                is ScrollView -> SavedScroll.Scroll(WeakReference(scroll), scroll.scrollY)
                is NestedScrollView -> SavedScroll.Nested(WeakReference(scroll), scroll.scrollY)
                else -> null
            }
        }

        private fun restoreSavedScroll() {
            val saved = savedScroll ?: return
            savedScroll = null
            root.postDelayed({
                when (saved) {
                    is SavedScroll.Scroll -> saved.view.get()?.smoothScrollTo(0, saved.scrollY)
                    is SavedScroll.Nested -> saved.view.get()?.smoothScrollTo(0, saved.scrollY)
                }
            }, 120L)
        }

        private fun findScrollParent(target: View): View? {
            var parent = target.parent
            while (parent is View) {
                if (parent is ScrollView || parent is NestedScrollView) return parent
                parent = parent.parent
            }
            return null
        }

        private fun dp(value: Int): Int =
            (value * root.resources.displayMetrics.density).toInt().coerceAtLeast(value)
    }

    private data class Padding(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private sealed class SavedScroll {
        data class Scroll(val view: WeakReference<ScrollView>, val scrollY: Int) : SavedScroll()
        data class Nested(val view: WeakReference<NestedScrollView>, val scrollY: Int) : SavedScroll()
    }
}
