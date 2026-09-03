package de.tbhelfer.einstellungen

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AktuelleBaustelleSpeicher(private val context: Context) {

    private val keyAktuelleBaustelleId = longPreferencesKey("aktuelle_baustelle_id")
    private val keyAktuellerKnotenId = longPreferencesKey("aktueller_knoten_id")
    private val keyAktuelleHauptId = longPreferencesKey("aktuelle_hauptbaustelle_knoten_id")

    val aktuelleBaustelleId: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[keyAktuelleBaustelleId]
    }

    /** Aktuell ausgewählter Knoten (Unterbaustelle/Unterunter...). */
    val aktuellerKnotenId: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[keyAktuellerKnotenId]
    }

    /** Wurzel-Knoten der aktuellen Hauptbaustelle (für Navigation). */
    val aktuelleHauptbaustelleKnotenId: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[keyAktuelleHauptId]
    }

    suspend fun setzen(id: Long) {
        context.dataStore.edit { prefs ->
            prefs[keyAktuelleBaustelleId] = id
        }
        de.tbhelfer.widget.TBHelferWidgetUpdater.updateAll(context)
    }

    suspend fun setzenKnoten(aktuellerKnotenId: Long, hauptKnotenId: Long, altBaustelleId: Long) {
        context.dataStore.edit { prefs ->
            prefs[keyAktuelleBaustelleId] = altBaustelleId
            prefs[keyAktuellerKnotenId] = aktuellerKnotenId
            prefs[keyAktuelleHauptId] = hauptKnotenId
        }
        de.tbhelfer.widget.TBHelferWidgetUpdater.updateAll(context)
    }

    /** Setzt nur die Hauptbaustelle als „aktuell“ (Unterbaustelle wird geleert). */
    suspend fun setzenNurHaupt(hauptKnotenId: Long, altBaustelleId: Long) {
        context.dataStore.edit { prefs ->
            prefs[keyAktuelleBaustelleId] = altBaustelleId
            prefs[keyAktuelleHauptId] = hauptKnotenId
            prefs.remove(keyAktuellerKnotenId)
        }
        de.tbhelfer.widget.TBHelferWidgetUpdater.updateAll(context)
    }



    suspend fun setzenStandalone(altBaustelleId: Long) {
        context.dataStore.edit { prefs ->
            prefs[keyAktuelleBaustelleId] = altBaustelleId
            prefs.remove(keyAktuellerKnotenId)
            prefs.remove(keyAktuelleHauptId)
        }
        de.tbhelfer.widget.TBHelferWidgetUpdater.updateAll(context)
    }


    suspend fun setzenOhneKnoten(baustelleId: Long) {
        context.dataStore.edit { prefs ->
            prefs[keyAktuelleBaustelleId] = baustelleId
            prefs.remove(keyAktuellerKnotenId)
            prefs.remove(keyAktuelleHauptId)
        }
        de.tbhelfer.widget.TBHelferWidgetUpdater.updateAll(context)
    }

    suspend fun loeschen() {
        context.dataStore.edit { prefs ->
            prefs.remove(keyAktuelleBaustelleId)
            prefs.remove(keyAktuellerKnotenId)
            prefs.remove(keyAktuelleHauptId)
        }
        de.tbhelfer.widget.TBHelferWidgetUpdater.updateAll(context)
    }
}
