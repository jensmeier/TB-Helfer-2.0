package de.tbhelfer.datenbank

import de.tbhelfer.util.KontextNormalisierung
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class StreckenpunkteRepository(private val dao: StreckenpunktDao, private val onChanged: () -> Unit = {}) {

    fun allePunkte(): Flow<List<StreckenpunktEntity>> = dao.allePunkte().flowOn(Dispatchers.IO)

    suspend fun hatDatenFuerStrecke(streckenkennung: String): Boolean =
        withContext(Dispatchers.IO) {
            val wanted = KontextNormalisierung.normalizeStrecke(streckenkennung)
            if (wanted.isBlank()) return@withContext false
            dao.ladeAlleOnce().any { KontextNormalisierung.sameStrecke(it.streckennummer, wanted) }
        }

    suspend fun hatDatenFuerBahnhof(bahnhof: String): Boolean =
        withContext(Dispatchers.IO) {
            val wanted = KontextNormalisierung.normalizeBahnhof(bahnhof)
            if (wanted.isBlank()) return@withContext false
            dao.ladeAlleOnce().any { KontextNormalisierung.sameBahnhof(it.bahnhof, wanted) }
        }

    suspend fun hatDatenFuerBahnhofGleis(bahnhof: String, gleis: String): Boolean =
        withContext(Dispatchers.IO) {
            val wantedBahnhof = KontextNormalisierung.normalizeBahnhof(bahnhof)
            val wantedGleis = KontextNormalisierung.normalizeGleis(gleis)
            if (wantedBahnhof.isBlank() || wantedGleis.isBlank()) return@withContext false
            dao.ladeAlleOnce().any {
                KontextNormalisierung.sameBahnhof(it.bahnhof, wantedBahnhof) &&
                    KontextNormalisierung.sameGleis(it.gleis, wantedGleis)
            }
        }

    suspend fun hatDatenFuerBahnhofWeiche(bahnhof: String, weiche: String): Boolean =
        withContext(Dispatchers.IO) {
            val wantedBahnhof = KontextNormalisierung.normalizeBahnhof(bahnhof)
            val wantedWeiche = KontextNormalisierung.normalizeWeiche(weiche)
            if (wantedBahnhof.isBlank() || wantedWeiche.isBlank()) return@withContext false
            dao.ladeAlleOnce().any {
                KontextNormalisierung.sameBahnhof(it.bahnhof, wantedBahnhof) &&
                    KontextNormalisierung.sameWeiche(it.weiche, wantedWeiche)
            }
        }

    fun punkteFuerBaustelle(baustelleId: Long): Flow<List<StreckenpunktEntity>> =
        dao.punkteFuerBaustelle(baustelleId).flowOn(Dispatchers.IO)

    suspend fun neuenPunkt(
        baustelleId: Long,
        km: Double,
        bezeichnung: String,
        notiz: String?,
        gpsLat: Double,
        gpsLon: Double,
        gpsAltM: Double? = null,
        kmSuffix: Int = 0,
        typ: StreckenpunktTyp = StreckenpunktTyp.EXAKT,
        typFreitext: String? = null,
        streckennummer: String? = null,
        bahnhof: String? = null,
        gleis: String? = null,
        weiche: String? = null,
        objektKennung: String? = null
    ): Long {
        val entity = StreckenpunktEntity(
            baustelleId = baustelleId,
            km = km,
            kmSuffix = kmSuffix,
            gpsLat = gpsLat,
            gpsLon = gpsLon,
            gpsAltM = gpsAltM,
            typ = typ,
            typFreitext = typFreitext?.trim().takeUnless { it.isNullOrBlank() },
            streckennummer = streckennummer?.trim().takeUnless { it.isNullOrBlank() },
            bahnhof = bahnhof?.trim().takeUnless { it.isNullOrBlank() },
            gleis = gleis?.trim().takeUnless { it.isNullOrBlank() },
            weiche = weiche?.trim().takeUnless { it.isNullOrBlank() },
            objektKennung = objektKennung?.trim().takeUnless { it.isNullOrBlank() },
            bezeichnung = bezeichnung.trim(),
            notiz = notiz?.trim().takeUnless { it.isNullOrBlank() }
        )
        return withContext(Dispatchers.IO) { dao.einfuegen(entity).also { onChanged() } }
    }

    suspend fun aktualisieren(entity: StreckenpunktEntity): Int =
        withContext(Dispatchers.IO) { dao.aktualisieren(entity).also { onChanged() } }

    suspend fun loeschen(entity: StreckenpunktEntity): Int = withContext(Dispatchers.IO) { dao.loeschen(entity).also { onChanged() } }
}
