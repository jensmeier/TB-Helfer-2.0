package de.tbhelfer.ui.kamera

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import de.tbhelfer.einstellungen.dataStore
import kotlinx.coroutines.flow.first

object FotoOptionalFelderSpeicher {

    data class Labels(
        val info1: String = "Info1",
        val info2: String = "Info2",
        val info3: String = "Info3"
    ) {
        fun asList(): List<String> = listOf(info1, info2, info3)
    }

    suspend fun laden(context: Context, baustelleId: Long): Labels {
        val prefs = context.dataStore.data.first()
        return Labels(
            info1 = prefs[key(baustelleId, 1)]?.takeIf { it.isNotBlank() } ?: "Info1",
            info2 = prefs[key(baustelleId, 2)]?.takeIf { it.isNotBlank() } ?: "Info2",
            info3 = prefs[key(baustelleId, 3)]?.takeIf { it.isNotBlank() } ?: "Info3"
        )
    }

    fun key(baustelleId: Long, index: Int) = stringPreferencesKey("foto_optional_label_${baustelleId}_$index")
}
