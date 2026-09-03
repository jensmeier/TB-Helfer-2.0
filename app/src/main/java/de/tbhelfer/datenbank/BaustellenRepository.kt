package de.tbhelfer.datenbank

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

const val STANDALONE_STRECKENPUNKTE_BAUSTELLENNAME = "__tb_standalone_streckenpunkte__"

fun istStandaloneStreckenpunkteBaustelle(name: String?): Boolean =
    name == STANDALONE_STRECKENPUNKTE_BAUSTELLENNAME

class BaustellenRepository(private val dao: BaustelleDao) {

    fun alleBaustellen(): Flow<List<BaustelleEntity>> = dao.alleBaustellen().flowOn(Dispatchers.IO)

    fun baustelleMitId(id: Long): Flow<BaustelleEntity?> = dao.baustelleMitId(id).flowOn(Dispatchers.IO)

    suspend fun baustelleMitIdOnce(id: Long): BaustelleEntity? = withContext(Dispatchers.IO) {
        dao.baustelleMitIdOnce(id)
    }

    /**
     * "Alte" Hauptbaustelle (BaustelleEntity) holen oder erstellen.
     * Wird genutzt, damit Streckenpunkte/Kamera weiterhin stabil funktionieren,
     * obwohl die neue Baustellenlogik über einen Baum (Knoten) läuft.
     */
    suspend fun hauptbaustelleAltHolenOderErstellen(baustellenname: String): Long {
        return withContext(Dispatchers.IO) {
            val name = baustellenname.trim()
            val vorhanden = dao.findeNachExaktemNamen(name)
            if (vorhanden != null) return@withContext vorhanden.id

        // Wir speichern in der alten Tabelle nur den Namen. Details liegen im Knoten.
        val entity = BaustelleEntity(
            baustellenname = name,
            streckennummer = null,
            bahnhof = null,
            gleis = null,
            weiche = null
        )
            dao.einfuegen(entity)
        }
    }

    suspend fun neueBaustelle(
        name: String,
        streckennummer: String?,
        bahnhof: String?,
        gleis: String?,
        weiche: String?
    ): Long {
        val entity = BaustelleEntity(
            baustellenname = name.trim(),
            streckennummer = streckennummer?.trim().takeUnless { it.isNullOrBlank() },
            bahnhof = bahnhof?.trim().takeUnless { it.isNullOrBlank() },
            gleis = gleis?.trim().takeUnless { it.isNullOrBlank() },
            weiche = weiche?.trim().takeUnless { it.isNullOrBlank() }
        )
        return withContext(Dispatchers.IO) { dao.einfuegen(entity) }
    }



    suspend fun standaloneStreckenpunkteBaustelleHolenOderAktualisieren(
        streckennummer: String?,
        bahnhof: String?,
        gleis: String?,
        weiche: String?
    ): Long {
        return withContext(Dispatchers.IO) {
            val vorhanden = dao.findeNachExaktemNamen(STANDALONE_STRECKENPUNKTE_BAUSTELLENNAME)
            val entity = (vorhanden ?: BaustelleEntity(
                baustellenname = STANDALONE_STRECKENPUNKTE_BAUSTELLENNAME
            )).copy(
                baustellenname = STANDALONE_STRECKENPUNKTE_BAUSTELLENNAME,
                streckennummer = streckennummer?.trim().takeUnless { it.isNullOrBlank() },
                bahnhof = bahnhof?.trim().takeUnless { it.isNullOrBlank() },
                gleis = gleis?.trim().takeUnless { it.isNullOrBlank() },
                weiche = weiche?.trim().takeUnless { it.isNullOrBlank() }
            )
            if (vorhanden == null) {
                dao.einfuegen(entity)
            } else {
                dao.aktualisieren(entity)
                vorhanden.id
            }
        }
    }

    suspend fun hauptbaustelleNameAktualisieren(id: Long, neuerName: String) {
        withContext(Dispatchers.IO) {
            val alt = dao.baustelleMitIdOnce(id) ?: return@withContext
            dao.aktualisieren(alt.copy(baustellenname = neuerName.trim()))
        }
    }


    suspend fun trassenKontextAktualisieren(
        id: Long,
        streckennummer: String?,
        bahnhof: String?,
        gleis: String?,
        weiche: String?
    ) {
        withContext(Dispatchers.IO) {
            val alt = dao.baustelleMitIdOnce(id) ?: return@withContext
            dao.aktualisieren(
                alt.copy(
                    streckennummer = streckennummer?.trim().takeUnless { it.isNullOrBlank() },
                    bahnhof = bahnhof?.trim().takeUnless { it.isNullOrBlank() },
                    gleis = gleis?.trim().takeUnless { it.isNullOrBlank() },
                    weiche = weiche?.trim().takeUnless { it.isNullOrBlank() }
                )
            )
        }
    }

    suspend fun loeschen(entity: BaustelleEntity): Int = withContext(Dispatchers.IO) { dao.loeschen(entity) }
}
