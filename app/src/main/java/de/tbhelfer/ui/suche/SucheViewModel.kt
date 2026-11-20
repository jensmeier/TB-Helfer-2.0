package de.tbhelfer.ui.suche

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import de.tbhelfer.einstellungen.dataStore
import de.tbhelfer.datenbank.BaustelleEntity
import de.tbhelfer.datenbank.BaustellenRepository
import de.tbhelfer.datenbank.FotoEntity
import de.tbhelfer.datenbank.FotoRepository
import de.tbhelfer.datenbank.StreckenpunktEntity
import de.tbhelfer.datenbank.StreckenpunkteRepository
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

class SucheViewModel(
    private val appContext: Context,
    private val baustellenRepository: BaustellenRepository,
    private val fotoRepository: FotoRepository,
    private val streckenpunkteRepository: StreckenpunkteRepository
) : ViewModel() {

    private val filterFlow = MutableStateFlow(SucheFilter())
    // A StateFlow does not emit when assigned an equal value. Explicit refresh
    // must have its own changing signal so new BTB PDFs are visible immediately.
    private val btbRefresh = MutableStateFlow(0L)

    private val KEY_CART_FOTO_IDS = stringSetPreferencesKey("cart_foto_ids")
    private val KEY_CART_BTB_PATHS = stringSetPreferencesKey("cart_btb_paths")


    /** Aktueller Filterzustand (für UI). */
    val filter: StateFlow<SucheFilter> = filterFlow.asStateFlow()

    fun currentFilter(): SucheFilter = filterFlow.value

    fun setFilter(update: (SucheFilter) -> SucheFilter) {
        filterFlow.value = update(filterFlow.value)
    }

    

    // --- Sammelkorb (Auswahl über Tabs hinweg) ---
    private val selectedFotoIdsFlow = MutableStateFlow<Set<Long>>(emptySet())
    val selectedFotoIds: StateFlow<Set<Long>> = selectedFotoIdsFlow.asStateFlow()

    private val selectedBtbPathsFlow = MutableStateFlow<Set<String>>(emptySet())
    val selectedBtbPaths: StateFlow<Set<String>> = selectedBtbPathsFlow.asStateFlow()


    init {
        // Korb aus DataStore laden
        viewModelScope.launch {
            appContext.dataStore.data.collect { prefs ->
                val fotoIds = prefs[KEY_CART_FOTO_IDS].orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
                val btb = prefs[KEY_CART_BTB_PATHS].orEmpty()
                selectedFotoIdsFlow.value = fotoIds
                selectedBtbPathsFlow.value = btb
            }
        }

        // Änderungen persistieren
        viewModelScope.launch {
            combine(selectedFotoIdsFlow, selectedBtbPathsFlow) { fotos, btbs -> fotos to btbs }
                .distinctUntilChanged()
                .collect { (fotos, btbs) ->
                    appContext.dataStore.edit { prefs ->
                        prefs[KEY_CART_FOTO_IDS] = fotos.map { it.toString() }.toSet()
                        prefs[KEY_CART_BTB_PATHS] = btbs
                    }
                }
        }
    }

    val korbCounts: StateFlow<Pair<Int, Int>> =
        combine(selectedFotoIds, selectedBtbPaths) { fotos, btbs -> fotos.size to btbs.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0 to 0)

    fun setSelectedFotos(ids: Set<Long>) {
        selectedFotoIdsFlow.value = ids
    }

    fun setSelectedBtbs(paths: Set<String>) {
        selectedBtbPathsFlow.value = paths
    }

    fun addFotosToKorb(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        selectedFotoIdsFlow.value = selectedFotoIdsFlow.value + ids
    }

    fun addBtbsToKorb(paths: Collection<String>) {
        if (paths.isEmpty()) return
        selectedBtbPathsFlow.value = selectedBtbPathsFlow.value + paths
    }

    fun clearKorb() {
        selectedFotoIdsFlow.value = emptySet()
        selectedBtbPathsFlow.value = emptySet()
    }

    fun clearFotoSelection() {
        selectedFotoIdsFlow.value = emptySet()
    }

    fun clearBtbSelection() {
        selectedBtbPathsFlow.value = emptySet()
    }

val streckenpunkte: StateFlow<List<StreckenpunktEntity>> =
        streckenpunkteRepository.allePunkte()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val baustellen: StateFlow<List<BaustelleEntity>> =
        baustellenRepository.alleBaustellen()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val fotosGefiltert: StateFlow<List<FotoEntity>> =
        combine(fotoRepository.alleFotos(), filterFlow, baustellen, streckenpunkte) { fotos, filter, baustellen, punkte ->
            filterFotos(fotos, filter, baustellen, punkte)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val btbsGefiltert: StateFlow<List<BtbDatei>> =
        combine(filterFlow, baustellen, btbRefresh) { filter, baustellen, _ ->
            filterBtbs(filter, baustellen)
        }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun filterFotos(
        fotos: List<FotoEntity>,
        filter: SucheFilter,
        baustellen: List<BaustelleEntity>,
        punkte: List<StreckenpunktEntity>
    ): List<FotoEntity> {

        val streckenQuery = filter.streckenNrQuery?.trim().orEmpty().lowercase()
        val textQuery = filter.volltext?.trim().orEmpty().lowercase()
        val erlaubteBildtypen = filter.bildtypen

        val kmVon = filter.kmVon
        val kmBis = filter.kmBis

        val streckenBaustellenIds: Set<Long>? =
            if (streckenQuery.isBlank()) null else {
                baustellen
                    .filter { it.streckennummer?.lowercase()?.contains(streckenQuery) == true }
                    .map { it.id }
                    .toSet()
            }

        // Streckenpunkte-Filter (a+b)
        val spQuery = filter.streckenpunktText?.trim().orEmpty().lowercase()
        val spActive = filter.nurBeiStreckenpunkten || spQuery.isNotBlank()
        val kmTol = 0.02 // 0.02 km = 20 m (kann später in Einstellungen)

        val relevantePunkte: List<StreckenpunktEntity> =
            if (!spActive) emptyList() else {
                punkte.asSequence()
                    .filter { p -> filter.baustelleId == null || p.baustelleId == filter.baustelleId }
                    .filter { p -> streckenBaustellenIds == null || p.baustelleId in streckenBaustellenIds }
                    .filter { p ->
                        if (spQuery.isBlank()) true else {
                            listOfNotNull(p.bezeichnung, p.objektKennung, p.notiz)
                                .any { it.lowercase().contains(spQuery) }
                        }
                    }
                    .toList()
            }

        fun matchStreckenpunkt(f: FotoEntity): Boolean {
            if (!spActive) return true
            if (relevantePunkte.isEmpty()) return false
            return relevantePunkte.any { p ->
                p.baustelleId == f.baustelleId &&
                        p.kmSuffix == f.kmSuffix &&
                        f.km?.let { km -> abs(p.km - km) <= kmTol } ?: false
            }
        }

        val liste = fotos.asSequence()
            .filter { f -> filter.baustelleId == null || f.baustelleId == filter.baustelleId }
            .filter { f -> streckenBaustellenIds == null || f.baustelleId in streckenBaustellenIds }
            .filter { f ->
                val von = filter.datumVonEpochMs
                val bis = filter.datumBisEpochMs
                (von == null || f.erstelltAmEpochMs >= von) && (bis == null || f.erstelltAmEpochMs <= bis)
            }
            .filter { f -> kmVon == null || (f.km != null && f.km >= kmVon) }
            .filter { f -> kmBis == null || (f.km != null && f.km <= kmBis) }
            .filter { f -> erlaubteBildtypen.isEmpty() || f.bildtyp in erlaubteBildtypen }
            .filter { f ->
                if (textQuery.isBlank()) true else {
                    listOfNotNull(
                        f.kurztext,
                        f.notiz,
                        f.firma,
                        f.objektKennung,
                        f.indikator?.name
                    ).any { it.lowercase().contains(textQuery) }
                }
            }
            .filter { f -> matchStreckenpunkt(f) }
            .toList()

        return if (filter.sortDesc) {
            liste.sortedByDescending { it.erstelltAmEpochMs }
        } else {
            liste.sortedBy { it.erstelltAmEpochMs }
        }
    }

    private fun filterBtbs(filter: SucheFilter, baustellen: List<BaustelleEntity>): List<BtbDatei> {
        val all = BtbSpeicher.alleBtbs(appContext)

        // Zuordnung: Ordnername = Baustellenname (sichererOrdnerName passiert beim Speichern),
        // deshalb matchen wir "contains" möglichst tolerant.
        val byFolderName = baustellen.associateBy { it.baustellenname }

        val streckenQuery = filter.streckenNrQuery?.trim().orEmpty().lowercase()
        val textQuery = filter.volltext?.trim().orEmpty().lowercase()

        fun fileDate(f: File): LocalDate = BtbSpeicher.dateFromLastModified(f)

        val vonDate = filter.datumVonEpochMs?.let {
            java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
        }
        val bisDate = filter.datumBisEpochMs?.let {
            java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
        }

        val liste = all.mapNotNull { file ->
            // Ordnername extrahieren: .../TB-Helfer/<Baustelle>/BTB/<pdf>
            val pathParts = file.absolutePath.split(File.separatorChar)
            val idx = pathParts.indexOfLast { it == "TB-Helfer" }
            val baustelleOrdner = if (idx >= 0 && idx + 1 < pathParts.size) pathParts[idx + 1] else ""
            val baustelleEntity = byFolderName.entries.firstOrNull { (name, _) ->
                name.isNotBlank() && baustelleOrdner.isNotBlank() && (
                    name.equals(baustelleOrdner, ignoreCase = true) ||
                    baustelleOrdner.contains(name, ignoreCase = true) ||
                    name.contains(baustelleOrdner, ignoreCase = true)
                )
            }?.value

            val bId = baustelleEntity?.id
            if (filter.baustelleId != null && bId != filter.baustelleId) return@mapNotNull null

            val strecke = baustelleEntity?.streckennummer
            if (streckenQuery.isNotBlank() && (strecke?.lowercase()?.contains(streckenQuery) != true)) return@mapNotNull null

            val d = fileDate(file)
            if (vonDate != null && d.isBefore(vonDate)) return@mapNotNull null
            if (bisDate != null && d.isAfter(bisDate)) return@mapNotNull null

            if (textQuery.isNotBlank()) {
                val hay = listOfNotNull(
                    file.name,
                    baustelleEntity?.baustellenname,
                    baustelleOrdner,
                    strecke
                ).joinToString(" ").lowercase()
                if (!hay.contains(textQuery)) return@mapNotNull null
            }

            BtbDatei(
                file = file,
                baustellenName = baustelleEntity?.baustellenname ?: baustelleOrdner,
                streckennummer = baustelleEntity?.streckennummer,
                datum = d
            )
        }

        return if (filter.sortDesc) {
            liste.sortedByDescending { it.file.lastModified() }
        } else {
            liste.sortedBy { it.file.lastModified() }
        }
    }

    fun refreshBtbs() {
        btbRefresh.value += 1L
    }

    class Fabrik(
        private val context: Context,
        private val baustellenRepository: BaustellenRepository,
        private val fotoRepository: FotoRepository,
        private val streckenpunkteRepository: StreckenpunkteRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SucheViewModel(context.applicationContext, baustellenRepository, fotoRepository, streckenpunkteRepository) as T
        }
    }
}
