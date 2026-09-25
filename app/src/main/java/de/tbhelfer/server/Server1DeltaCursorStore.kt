package de.tbhelfer.server

import android.content.Context

/** Persistenter Cursor fuer /api/v1/sync/changes/wait. */
object Server1DeltaCursorStore {
    private const val PREFS = "tbhelfer_server1_delta_v1"
    private const val KEY_CURSOR = "cursor"

    fun load(context: Context): Long = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getLong(KEY_CURSOR, 0L)
        .coerceAtLeast(0L)

    fun commit(context: Context, cursor: Long): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_CURSOR, cursor.coerceAtLeast(0L))
            .commit()
}
