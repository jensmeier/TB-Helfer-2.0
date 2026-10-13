package de.tbhelfer.ui.baustelle

import android.content.Context
import de.tbhelfer.ServiceLocator
import de.tbhelfer.ui.bautagebuch.BautagebuchSpeicher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Einziger lokaler Komplett-Loeschpfad fuer eine Hauptbaustelle.
 * Wird sowohl durch den Benutzer als auch durch einen Server-Tombstone verwendet.
 */
object WorksiteLocalDeleteService {
    suspend fun delete(context: Context, localRootKnotenId: Long): Boolean {
        val root = ServiceLocator.baustellenKnotenDao.laden(localRootKnotenId) ?: return false
        val appContext = context.applicationContext
        val altId = root.altBaustelleId

        withContext(Dispatchers.IO) {
            ServiceLocator.fotoRepository.fotosFuerBaustelleOnce(altId).forEach { foto ->
                runCatching { File(foto.dateipfad).delete() }
            }
            BautagebuchSpeicher.loescheAlleBtbsFuerBaustelle(
                appContext,
                altId,
                root.hauptbaustellenName
            )
            ServiceLocator.baustellenRepository.baustelleMitIdOnce(altId)?.let {
                ServiceLocator.baustellenRepository.loeschen(it)
            }
            ServiceLocator.baustellenBaumRepository.loescheTeilbaum(root.id)
            ServiceLocator.worksiteBindingRepository.unbindLocal(root.id)
        }

        if (ServiceLocator.aktuelleBaustelleSpeicher.aktuelleHauptbaustelleKnotenId.first() == root.id) {
            ServiceLocator.aktuelleBaustelleSpeicher.loeschen()
        }
        return true
    }
}
