package de.tbhelfer.einstellungen

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/** Gemeinsamer DataStore für TB-Helfer Einstellungen. */
val Context.dataStore by preferencesDataStore(name = "tb_helfer_einstellungen")
