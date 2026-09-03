package de.tbhelfer.util

import android.content.Context
import java.util.Locale

object BahnhofSucheUtil {
    private const val PREFS_NAME = "tbhelfer_bahnhof_suche"
    private const val KEY_LETZTE_EINGABE = "letzte_bahnhof_eingabe"
    private val NON_ALNUM_REGEX = Regex("[^\\p{L}\\p{Nd}]")

    fun normalisiereSuchtext(text: String): String {
        return text
            .trim()
            .lowercase(Locale.GERMANY)
            .replace(NON_ALNUM_REGEX, "")
    }

    fun enthaeltTreffer(query: String, code: String, name: String, display: String): Boolean {
        val q = normalisiereSuchtext(query)
        if (q.isBlank()) return true
        return normalisiereSuchtext(code).contains(q) ||
            normalisiereSuchtext(name).contains(q) ||
            normalisiereSuchtext(display).contains(q)
    }

    fun speichereLetzteEingabe(context: Context, value: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LETZTE_EINGABE, value.trim())
            .apply()
    }

    fun ladeLetzteEingabe(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LETZTE_EINGABE, "")
            .orEmpty()
    }
}
