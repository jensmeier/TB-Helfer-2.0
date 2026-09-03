package de.tbhelfer.util

import android.view.View
import androidx.annotation.IdRes

/**
 * Like findViewById(), but fails fast with a clear message if the view is missing.
 *
 * We intentionally avoid 'inline reified' here to keep Kotlin's public API rules happy.
 */
@Suppress("UNCHECKED_CAST")
fun <T : View> View.requireViewByIdCompat(@IdRes id: Int): T {
    val found = this.findViewById<View>(id)
        ?: throw IllegalStateException(
            "Missing required view id='${idNameSafe(id)}' (0x${id.toString(16)}) in layout root=${this.javaClass.simpleName}"
        )
    return found as T
}

private fun View.idNameSafe(@IdRes id: Int): String =
    try {
        resources.getResourceEntryName(id)
    } catch (_: Exception) {
        id.toString()
    }
