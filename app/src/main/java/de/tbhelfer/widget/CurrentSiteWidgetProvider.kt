package de.tbhelfer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.Bildtyp
import de.tbhelfer.datenbank.FotoEntity
import de.tbhelfer.ui.HauptActivity
import de.tbhelfer.ui.abnahme.AbnahmeSpeicher
import de.tbhelfer.ui.baustelle.MangelFachlogik
import de.tbhelfer.ui.bautagebuch.BautagebuchEintrag
import de.tbhelfer.ui.bautagebuch.BautagebuchSpeicher
import de.tbhelfer.ui.bautagebuch.BtbPhotoSelectionStore
import de.tbhelfer.ui.bautagebuch.BtbPhotoStartOverrideStore
import de.tbhelfer.ui.bautagebuch.BtbPhotoTextOverrideStore
import de.tbhelfer.ui.bautagebuch.SchichtTyp
import de.tbhelfer.ui.bautagebuch.SectionBlock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Vergrößerbares Baustellen-Widget.
 * Das separate Kamera-Widget bleibt als eigener, direkter Kamera-Schnellstart erhalten.
 */
class CurrentSiteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(incoming: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val context = incoming.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { updateWidgetsNow(context.applicationContext, appWidgetManager, appWidgetIds) }
            finally { pending.finish() }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { updateWidgetsNow(context.applicationContext, appWidgetManager, intArrayOf(appWidgetId)) }
            finally { pending.finish() }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPreferences.delete(context, it) }
    }

    override fun onReceive(incoming: Context, intent: Intent) {
        val context = incoming.applicationContext
        if (intent.action == ACTION_REFRESH_POSITION) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    WidgetPositionStore.refreshManualFromSystem(context.applicationContext)
                    val manager = AppWidgetManager.getInstance(context.applicationContext)
                    val ids = manager.getAppWidgetIds(android.content.ComponentName(context.applicationContext, CurrentSiteWidgetProvider::class.java))
                    updateWidgetsNow(context.applicationContext, manager, ids)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_REFRESH_POSITION = "de.tbhelfer.widget.REFRESH_POSITION"
        private val deDate = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(android.content.ComponentName(context, CurrentSiteWidgetProvider::class.java))
            updateWidgets(context, manager, ids)
        }

        fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch {
                updateWidgetsNow(context.applicationContext, manager, ids)
            }
        }

        private suspend fun updateWidgetsNow(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val data = runCatching { loadData(context) }.getOrElse { WidgetData.empty() }
            ids.forEach { id ->
                val config = WidgetPreferences.load(context, id)
                val options = manager.getAppWidgetOptions(id)
                val views = buildViews(context, id, config, data, options)
                manager.updateAppWidget(id, views)
            }
        }

        private suspend fun loadData(context: Context): WidgetData {
            val altId = ServiceLocator.aktuelleBaustelleSpeicher.aktuelleBaustelleId.first()
            val nodeId = ServiceLocator.aktuelleBaustelleSpeicher.aktuellerKnotenId.first()
            val rootId = ServiceLocator.aktuelleBaustelleSpeicher.aktuelleHauptbaustelleKnotenId.first()
            if (altId == null) return WidgetData.empty()

            val entity = ServiceLocator.baustellenRepository.baustelleMitIdOnce(altId)
            val node = nodeId?.let { ServiceLocator.baustellenBaumRepository.knotenMitId(it) }
            val path = nodeId?.let {
                runCatching { ServiceLocator.baustellenBaumRepository.pfadVonRootBis(it) }.getOrDefault(emptyList())
            }.orEmpty()
            val mainName = entity?.baustellenname ?: node?.hauptbaustellenName ?: "Baustelle"

            fun nodeLabel(n: de.tbhelfer.datenbank.BaustellenKnotenEntity?): String? = n?.let {
                it.knotenName?.trim()?.takeIf { value -> value.isNotBlank() }
                    ?: it.gleisOderWeiche?.trim()?.takeIf { value -> value.isNotBlank() }
                    ?: it.streckenkennung?.trim()?.takeIf { value -> value.isNotBlank() }
                    ?: it.bahnhofName?.trim()?.takeIf { value -> value.isNotBlank() }
            }

            val under = nodeLabel(path.getOrNull(1))
                ?: node?.takeIf { it.parentId == rootId }?.let(::nodeLabel)
            val underUnder = path.drop(2).mapNotNull(::nodeLabel).joinToString(" › ").takeIf { it.isNotBlank() }
            val currentLabel = underUnder?.substringAfterLast(" › ") ?: under
            val displayName = if (currentLabel.isNullOrBlank()) mainName else "$mainName · $currentLabel"

            val today = LocalDate.now()
            val candidates = buildList {
                listOf(today.minusDays(1), today).forEach { date ->
                    listOf(SchichtTyp.TAG, SchichtTyp.NACHT).forEach { shift ->
                        BautagebuchSpeicher.laden(context, altId, mainName, date, shift)?.let(::add)
                    }
                }
            }
            val entry = candidates.maxByOrNull { it.geaendertAmEpochMs }
            val a = runCatching { AbnahmeSpeicher.zaehler(context, altId) }.getOrDefault(AbnahmeSpeicher.Zaehler())
            val m = runCatching { MangelFachlogik.zaehlerFuerBaustelle(context, altId, mainName) }
                .getOrDefault(MangelFachlogik.Zaehler())
            // Nur lesend: dieselben Foto-Daten und BTB-Auswahlen wie in der App verwenden.
            // BTB, PDF und Fotos selbst werden dabei nicht verändert.
            val photoEntries = if (entry != null) {
                try { loadPhotoEntries(context, altId, entry) } catch (_: Exception) { emptyList() }
            } else emptyList()

            return WidgetData(
                baustelleId = altId,
                hauptKnotenId = rootId,
                mainName = mainName,
                displayName = displayName,
                unterName = under,
                unterUnterName = underUnder,
                entry = entry,
                photoEntries = photoEntries,
                abnahme = a,
                mangel = m
            )
        }

        private fun buildViews(
            context: Context,
            appWidgetId: Int,
            config: WidgetPreferences.Config,
            data: WidgetData,
            options: Bundle
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_tbhelfer_unified)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)

            // Größenstufen für die üblichen Android-Raster:
            // unter 2x2: keine Aktionsleiste
            // ab 2x2: nur Kamera unten mittig
            // ab 3x2: beide Zusatzaktionen plus Kamera
            val atLeast2x2 = minWidth >= 100 && minHeight >= 100
            val atLeast3x2 = minWidth >= 205 && minHeight >= 100
            val compact = !atLeast2x2
            val expandedLabels = minWidth >= 300
            val showStatus = minHeight >= 180 || (minWidth >= 300 && minHeight >= 145)
            val maxBodyLines = when {
                compact -> 1
                minHeight < 135 -> 1
                minHeight < 180 -> config.letzteEintraege.coerceIn(1, 2)
                minHeight < 235 -> config.letzteEintraege.coerceIn(1, 4)
                minHeight < 300 -> config.letzteEintraege.coerceIn(1, 6)
                minHeight < 370 -> config.letzteEintraege.coerceIn(1, 8)
                else -> config.letzteEintraege.coerceIn(1, 10)
            }

            applyTheme(context, views, config.darstellung)
            resetVisibility(views)
            applyResponsiveTextSizes(views, minWidth, minHeight)

            val mainPending = pendingForMainClick(context, appWidgetId, config.hauptKlick, data)
            views.setOnClickPendingIntent(R.id.widgetUnifiedRoot, mainPending)

            views.setViewVisibility(R.id.widgetCameraOnly, View.GONE)
            views.setViewVisibility(R.id.widgetContent, View.VISIBLE)
            val detailedHeader = minWidth >= 205 && minHeight >= 260
            views.setViewVisibility(R.id.widgetHeaderTitle, if (compact) View.GONE else View.VISIBLE)
            val sitePath = widgetSitePath(data)
            val subtitle = data.entry?.let { e ->
                val schicht = if (e.schicht == SchichtTyp.TAG) "Tag" else "Nacht"
                val state = if (e.istFertig) "abgeschlossen" else "Entwurf"
                "${runCatching { LocalDate.parse(e.datumIso).format(deDate) }.getOrDefault(e.datumIso)} · $schicht · $state"
            } ?: "Kein aktueller BTB"

            // Die Baustellenhierarchie wird im Widget kompakt als Pfad dargestellt:
            // Pasing › Gl 28 › Kabelquerung. PDF und BTB bleiben unverändert.
            views.setTextViewText(R.id.widgetHeaderTitle, "Aktuelle Baustelle")
            views.setViewVisibility(R.id.widgetSiteName, View.VISIBLE)
            views.setTextViewText(R.id.widgetSiteName, sitePath)
            views.setViewVisibility(R.id.widgetSubSiteName, View.GONE)
            views.setViewVisibility(R.id.widgetSubSubSiteName, View.GONE)
            views.setViewVisibility(R.id.widgetLatestTitle, if (detailedHeader) View.VISIBLE else View.GONE)
            if (detailedHeader) {
                views.setTextViewTextSize(R.id.widgetHeaderTitle, TypedValue.COMPLEX_UNIT_SP, 16f)
                views.setTextViewTextSize(R.id.widgetSiteName, TypedValue.COMPLEX_UNIT_SP, 16f)
            }
            views.setTextViewText(R.id.widgetSubtitle, subtitle)
            views.setViewVisibility(R.id.widgetSubtitle, if (compact) View.GONE else View.VISIBLE)

            // Das Baustellen-Widget zeigt immer die letzten Einträge. Bei genügend Höhe
            // kommen automatisch die Mängel- und Abnahmestatuszeilen hinzu.
            renderLatest(views, config, data, maxBodyLines)
            if (showStatus && !compact) renderStatus(views, data, compact = false)

            // Fachzeilen können unabhängig vom Hauptklick direkt geöffnet werden.
            views.setOnClickPendingIntent(
                R.id.widgetStatusLine1,
                activityPending(context, appWidgetId * 20 + 11, R.id.maengellisteFragment, mangelArgs(data))
            )
            views.setOnClickPendingIntent(
                R.id.widgetStatusLine2,
                activityPending(context, appWidgetId * 20 + 12, R.id.abnahmelisteFragment, abnahmeListArgs(data))
            )

            val showCamera = config.kameraAnzeigen && atLeast2x2
            val configuredActions = if (config.buttonsAnzeigen && atLeast3x2) {
                listOf(config.button1, config.button2)
                    .distinct()
                    .take(2)
            } else {
                emptyList()
            }
            val showButtons = configuredActions.isNotEmpty()
            if (showButtons || showCamera) {
                views.setViewVisibility(R.id.widgetButtonRow, View.VISIBLE)
                val firstAction = configuredActions.getOrNull(0)
                val secondAction = configuredActions.getOrNull(1)
                views.setViewVisibility(R.id.widgetAction1, if (firstAction != null) View.VISIBLE else View.GONE)
                views.setViewVisibility(R.id.widgetAction2, if (secondAction != null) View.VISIBLE else View.GONE)
                views.setViewVisibility(R.id.widgetCameraAction, if (showCamera) View.VISIBLE else View.GONE)
                views.setViewVisibility(R.id.widgetRefreshPosition, View.GONE)
                firstAction?.let { action ->
                    views.setTextViewText(R.id.widgetAction1, buttonLabel(action, expandedLabels))
                    views.setOnClickPendingIntent(R.id.widgetAction1, pendingForAction(context, appWidgetId, action, data, 1))
                }
                secondAction?.let { action ->
                    views.setTextViewText(R.id.widgetAction2, buttonLabel(action, expandedLabels))
                    views.setOnClickPendingIntent(R.id.widgetAction2, pendingForAction(context, appWidgetId, action, data, 2))
                }
                if (showCamera) {
                    views.setOnClickPendingIntent(
                        R.id.widgetCameraAction,
                        pendingForAction(context, appWidgetId, WidgetPreferences.ButtonAktion.KAMERA, data, 3)
                    )
                }
                views.setOnClickPendingIntent(R.id.widgetRefreshPosition, refreshPending(context, appWidgetId))
            }
            return views
        }

        private fun widgetSitePath(data: WidgetData): String {
            val parts = buildList {
                add(data.mainName)
                data.unterName?.let { add(it) }
                data.unterUnterName
                    ?.split("›")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?.forEach { add(it) }
            }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .fold(mutableListOf<String>()) { acc, part ->
                    if (acc.lastOrNull()?.equals(part, ignoreCase = true) != true) acc += part
                    acc
                }
            return parts.joinToString(" › ").ifBlank { "Keine Baustelle gewählt" }
        }

        private fun renderLatest(
            views: RemoteViews,
            config: WidgetPreferences.Config,
            data: WidgetData,
            maxLines: Int
        ) {
            val lines = latestEntries(data.entry, data.photoEntries, config).takeLast(maxLines).asReversed()
            val shown = if (lines.isEmpty()) listOf("Noch keine Einträge") else lines
            setBodyLines(views, shown)
        }

        private fun renderStatus(views: RemoteViews, data: WidgetData, compact: Boolean) {
            views.setViewVisibility(R.id.widgetStatusArea, View.VISIBLE)
            views.setViewVisibility(R.id.widgetStatusLine1, View.VISIBLE)
            views.setTextViewText(
                R.id.widgetStatusLine1,
                "Mängel: ${data.mangel.offen} offen · ${data.mangel.inArbeit} Arbeit"
            )
            if (!compact) {
                views.setViewVisibility(R.id.widgetStatusLine2, View.VISIBLE)
                views.setTextViewText(
                    R.id.widgetStatusLine2,
                    "Abnahmen: ${data.abnahme.ja} ja · ${data.abnahme.nein} nein · ${data.abnahme.inPruefung} Prüfung"
                )
            }
        }

        private fun renderLocation(context: Context, views: RemoteViews, config: WidgetPreferences.Config, compact: Boolean) {
            val p = WidgetPositionStore.load(context, config.positionModus)
            views.setViewVisibility(R.id.widgetLocationLine1, View.VISIBLE)
            views.setViewVisibility(R.id.widgetLocationLine2, if (compact) View.GONE else View.VISIBLE)
            if (p == null) {
                views.setTextViewText(R.id.widgetLocationLine1, "Position noch nicht vorhanden")
                views.setTextViewText(R.id.widgetLocationLine2, "App-Kamera öffnen oder ↻ tippen")
                return
            }
            val line1 = p.fachzeile?.takeIf { it.isNotBlank() }
                ?: p.kontext?.takeIf { it.isNotBlank() }
                ?: String.format(Locale.GERMANY, "%.5f, %.5f", p.lat, p.lon)
            val acc = p.accuracyM?.let { " · ±${String.format(Locale.GERMANY, "%.0f", it)} m" }.orEmpty()
            views.setTextViewText(R.id.widgetLocationLine1, line1)
            views.setTextViewText(R.id.widgetLocationLine2, "Stand ${WidgetPositionStore.timeText(p.epochMs)}$acc")
        }

        private fun renderQuick(views: RemoteViews, compact: Boolean) {
            if (!compact) {
                views.setViewVisibility(R.id.widgetLine1, View.VISIBLE)
                views.setTextViewText(R.id.widgetLine1, "Schnellzugriff")
            }
        }

        private fun latestEntries(
            entry: BautagebuchEintrag?,
            photoEntries: List<EntryLine>,
            cfg: WidgetPreferences.Config
        ): List<String> {
            if (entry == null) return emptyList()
            val candidates = mutableListOf<EntryLine>()
            var order = 0

            fun addRaw(raw: String?, label: String) {
                raw.orEmpty().lines().forEach { original ->
                    val clean = de.tbhelfer.util.KmAnzeige.zeile(original)
                    if (clean.isBlank()) return@forEach
                    val parsed = parseTimeText(clean)
                    candidates += EntryLine(parsed.first, parsed.second, label, order++)
                }
            }

            fun addBlock(block: SectionBlock, label: String) {
                block.items.filter { it.text.isNotBlank() }.forEach { item ->
                    val km = de.tbhelfer.util.KmAnzeige.bereich(item.kmVon, item.kmBis, inKlammern = false)
                    val text = listOfNotNull(km, item.text.trim()).joinToString(" · ")
                    candidates += EntryLine(item.uhrzeit, text, label, order++)
                }
            }

            if (cfg.punkt4) addRaw(entry.notizen, "Arbeit")
            if (cfg.punkt5) addBlock(entry.abnahmen, "Abnahme")
            if (cfg.punkt6) addBlock(entry.einweisungUnterweisung, "Einweisung")
            if (cfg.punkt7) addBlock(entry.besonderheiten, "Besonderheit")
            if (cfg.punkt8) addBlock(entry.maengelSchaeden, "Mangel")

            photoEntries.filter { line ->
                when (line.label) {
                    "Arbeit" -> cfg.punkt4
                    "Abnahme" -> cfg.punkt5
                    "Einweisung" -> cfg.punkt6
                    "Besonderheit" -> cfg.punkt7
                    "Mangel" -> cfg.punkt8
                    else -> false
                }
            }.forEach { candidates += it.copy(order = order++) }

            val startMin = parseMinute(entry.startUhrzeit)
            return candidates.sortedWith(compareBy<EntryLine> {
                val m = it.sortMinute ?: parseMinute(it.time)
                if (m == null) 10_000 + it.order else if (startMin != null && m < startMin) m + 1440 else m
            }.thenBy { it.order }).map { e ->
                val time = e.time?.takeIf { it.isNotBlank() }?.plus("  ").orEmpty()
                "$time${e.text}"
            }
        }

        private suspend fun loadPhotoEntries(
            context: Context,
            baustelleId: Long,
            entry: BautagebuchEintrag
        ): List<EntryLine> {
            val datum = runCatching { LocalDate.parse(entry.datumIso) }.getOrNull() ?: return emptyList()
            val (from, to) = shiftWindowEpochMs(datum, entry.startUhrzeit, entry.endeUhrzeit)
            val fotos = ServiceLocator.fotoRepository.fotosFuerBaustelleOnce(baustelleId)
                .filter { it.erstelltAmEpochMs in from until to }
                .sortedBy { it.erstelltAmEpochMs }
            if (fotos.isEmpty()) return emptyList()

            val excluded = BtbPhotoSelectionStore.loadExcluded(
                context, baustelleId, entry.datumIso, entry.startUhrzeit, entry.endeUhrzeit, entry.schicht
            )
            val textOverrides = BtbPhotoTextOverrideStore.loadOverrides(
                context, baustelleId, entry.datumIso, entry.startUhrzeit, entry.endeUhrzeit, entry.schicht
            )
            val startOverrides = BtbPhotoStartOverrideStore.loadOverrides(
                context, baustelleId, entry.datumIso, entry.startUhrzeit, entry.endeUhrzeit, entry.schicht
            )

            val selected = fotos.filter { foto ->
                excluded[foto.bildtyp.name]?.contains(foto.id) != true
            }
            if (selected.isEmpty()) return emptyList()

            val anhangOrder = listOf(
                Bildtyp.ABNAHMEN,
                Bildtyp.EINWEISUNG_UNTERWEISUNG,
                Bildtyp.BESONDERHEITEN,
                Bildtyp.MAENGEL_SCHAEDEN,
                Bildtyp.FOTODOKUMENTATION
            )
            val byType = selected.groupBy { it.bildtyp }
            val ordered = anhangOrder
                .flatMap { typ -> byType[typ].orEmpty().sortedBy { it.erstelltAmEpochMs } }
                .take(entry.maxFotosInPdf.coerceAtLeast(1))
            val fotoNum = ordered.mapIndexed { index, foto -> foto.id to index + 1 }.toMap()

            val grouped = selected.groupBy { foto ->
                val text = textOverrides[foto.id]?.trim().orEmpty().ifBlank { foto.kurztext.trim() }
                Triple(foto.bildtyp, foto.firma.trim(), text)
            }

            var order = 100_000
            return grouped.entries.mapNotNull { (key, list) ->
                val (typ, firma, text) = key
                if (text.isBlank()) return@mapNotNull null
                val sorted = list.sortedBy { it.erstelltAmEpochMs }
                val range = effectivePhotoRange(sorted, startOverrides)
                val nums = sorted.mapNotNull { fotoNum[it.id] }
                val bild = bildLabel(nums)
                val core = if (range.from == range.to) "${range.from} $text" else "${range.from}–${range.to} $text"
                val withBild = if (bild.isBlank()) core else "$core  $bild"
                val textOut = if (firma.isBlank()) withBild else "$firma: $withBild"
                EntryLine(
                    time = null,
                    text = textOut,
                    label = labelForBildtyp(typ),
                    order = order++,
                    sortMinute = parseMinute(range.from)
                )
            }.sortedWith(compareBy<EntryLine> { parseMinute(it.time) ?: Int.MAX_VALUE }.thenBy { it.order })
        }

        private data class PhotoRange(val from: String, val to: String)

        private fun effectivePhotoRange(
            sorted: List<FotoEntity>,
            startOverrides: Map<Long, String>
        ): PhotoRange {
            val autoStart = sorted.first().erstelltAmEpochMs
            val autoEnd = sorted.last().erstelltAmEpochMs
            val ranges = sorted.mapNotNull { parsePhotoTimeOverride(startOverrides[it.id]) }
            val manualStart = ranges.mapNotNull { it.first?.let { min -> wallTimeEpochNear(autoStart, min) } }.minOrNull()
            val manualEnd = ranges.mapNotNull { it.second?.let { min -> wallTimeEpochNear(autoEnd, min) } }.maxOrNull()
            val start = manualStart?.let { minOf(it, autoStart) } ?: autoStart
            val end = manualEnd?.let { maxOf(it, autoEnd) } ?: autoEnd
            return PhotoRange(timeOf(start), timeOf(end))
        }

        private fun parsePhotoTimeOverride(raw: String?): Pair<Int?, Int?>? {
            val value = raw?.trim().orEmpty()
            if (value.isBlank()) return null
            fun minute(text: String): Int? = parseMinute(text)?.takeIf { it in 0 until 1440 }
            minute(value)?.let { return it to null }
            val m = Regex("^(\\d{1,2}:\\d{2})\\s*[-–]\\s*(\\d{1,2}:\\d{2})$").matchEntire(value)
                ?: return null
            return (minute(m.groupValues[1]) ?: return null) to (minute(m.groupValues[2]) ?: return null)
        }

        private fun wallTimeEpochNear(reference: Long, minuteOfDay: Int): Long {
            val zone = ZoneId.systemDefault()
            val date = Instant.ofEpochMilli(reference).atZone(zone).toLocalDate()
            return listOf(-1L, 0L, 1L).map { delta ->
                date.plusDays(delta).atStartOfDay(zone).plusMinutes(minuteOfDay.toLong()).toInstant().toEpochMilli()
            }.minBy { kotlin.math.abs(it - reference) }
        }

        private fun timeOf(epochMs: Long): String = Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(DateTimeFormatter.ofPattern("HH:mm"))

        private fun bildLabel(numbers: List<Int>): String {
            val nums = numbers.distinct().sorted()
            if (nums.isEmpty()) return ""
            val runs = mutableListOf<Pair<Int, Int>>()
            var start = nums.first()
            var end = start
            nums.drop(1).forEach { number ->
                if (number == end + 1) end = number else {
                    runs += start to end
                    start = number
                    end = number
                }
            }
            runs += start to end
            return "Bild " + runs.joinToString("+") { (a, b) -> if (a == b) "$a" else "$a–$b" }
        }

        private fun labelForBildtyp(typ: Bildtyp): String = when (typ) {
            Bildtyp.FOTODOKUMENTATION -> "Arbeit"
            Bildtyp.ABNAHMEN -> "Abnahme"
            Bildtyp.EINWEISUNG_UNTERWEISUNG -> "Einweisung"
            Bildtyp.BESONDERHEITEN -> "Besonderheit"
            Bildtyp.MAENGEL_SCHAEDEN -> "Mangel"
        }

        private fun shiftWindowEpochMs(datum: LocalDate, start: String?, end: String?): Pair<Long, Long> {
            val zone = ZoneId.systemDefault()
            val dayStart = datum.atStartOfDay(zone).toInstant().toEpochMilli()
            val nextDay = datum.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val s = parseMinute(start)
            val e = parseMinute(end)
            if (s == null || e == null) return dayStart to nextDay
            val from = dayStart + s * 60_000L
            val to = if (e >= s) dayStart + e * 60_000L else nextDay + e * 60_000L
            return from to to
        }

        private fun parseTimeText(line: String): Pair<String?, String> {
            val m = Regex("^(\\d{1,2}:\\d{2})\\s+(.+)$").find(line)
            return if (m != null) m.groupValues[1] to m.groupValues[2] else null to line
        }

        private fun parseMinute(raw: String?): Int? {
            val p = raw?.trim()?.split(":") ?: return null
            if (p.size != 2) return null
            val h = p[0].toIntOrNull() ?: return null
            val m = p[1].toIntOrNull() ?: return null
            return h * 60 + m
        }

        private fun setBodyLines(views: RemoteViews, lines: List<String>) {
            val ids = intArrayOf(
                R.id.widgetLine1, R.id.widgetLine2, R.id.widgetLine3, R.id.widgetLine4, R.id.widgetLine5,
                R.id.widgetLine6, R.id.widgetLine7, R.id.widgetLine8, R.id.widgetLine9, R.id.widgetLine10
            )
            ids.forEachIndexed { index, id ->
                val line = lines.getOrNull(index)
                views.setViewVisibility(id, if (line == null) View.GONE else View.VISIBLE)
                if (line != null) views.setTextViewText(id, line)
            }
        }

        private fun applyResponsiveTextSizes(views: RemoteViews, minWidth: Int, minHeight: Int) {
            val tier = when {
                minWidth >= 300 || minHeight >= 260 -> 3
                minWidth >= 205 && minHeight >= 100 -> 2
                minWidth >= 100 && minHeight >= 100 -> 1
                else -> 0
            }
            val header = floatArrayOf(9f, 10f, 12f, 14f)[tier]
            val site = floatArrayOf(12f, 14f, 17f, 20f)[tier]
            val subtitle = floatArrayOf(8f, 9f, 11f, 13f)[tier]
            val body = floatArrayOf(8f, 9f, 11f, 14f)[tier]
            val status = floatArrayOf(8f, 9f, 11f, 13f)[tier]
            val button = floatArrayOf(8f, 9f, 10f, 12f)[tier]

            views.setTextViewTextSize(R.id.widgetHeaderTitle, TypedValue.COMPLEX_UNIT_SP, header)
            views.setTextViewTextSize(R.id.widgetSiteName, TypedValue.COMPLEX_UNIT_SP, site)
            views.setTextViewTextSize(R.id.widgetSubSiteName, TypedValue.COMPLEX_UNIT_SP, subtitle)
            views.setTextViewTextSize(R.id.widgetSubSubSiteName, TypedValue.COMPLEX_UNIT_SP, subtitle)
            views.setTextViewTextSize(R.id.widgetLatestTitle, TypedValue.COMPLEX_UNIT_SP, header)
            views.setTextViewTextSize(R.id.widgetSubtitle, TypedValue.COMPLEX_UNIT_SP, subtitle)
            intArrayOf(
                R.id.widgetLine1, R.id.widgetLine2, R.id.widgetLine3, R.id.widgetLine4, R.id.widgetLine5,
                R.id.widgetLine6, R.id.widgetLine7, R.id.widgetLine8, R.id.widgetLine9, R.id.widgetLine10
            ).forEach { views.setTextViewTextSize(it, TypedValue.COMPLEX_UNIT_SP, body) }
            intArrayOf(R.id.widgetStatusLine1, R.id.widgetStatusLine2).forEach {
                views.setTextViewTextSize(it, TypedValue.COMPLEX_UNIT_SP, status)
            }
            intArrayOf(R.id.widgetAction1, R.id.widgetAction2).forEach {
                views.setTextViewTextSize(it, TypedValue.COMPLEX_UNIT_SP, button)
            }
        }

        private fun resetVisibility(views: RemoteViews) {
            listOf(
                R.id.widgetLine1, R.id.widgetLine2, R.id.widgetLine3, R.id.widgetLine4, R.id.widgetLine5,
                R.id.widgetLine6, R.id.widgetLine7, R.id.widgetLine8, R.id.widgetLine9, R.id.widgetLine10,
                R.id.widgetStatusArea, R.id.widgetStatusLine1, R.id.widgetStatusLine2,
                R.id.widgetSubSiteName, R.id.widgetSubSubSiteName, R.id.widgetLatestTitle,
                R.id.widgetLocationLine1, R.id.widgetLocationLine2,
                R.id.widgetButtonRow, R.id.widgetAction1, R.id.widgetAction2,
                R.id.widgetCameraAction, R.id.widgetRefreshPosition
            ).forEach { views.setViewVisibility(it, View.GONE) }
        }

        private fun applyTheme(context: Context, views: RemoteViews, mode: WidgetPreferences.Darstellung) {
            val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val dark = when (mode) {
                WidgetPreferences.Darstellung.DUNKEL -> true
                WidgetPreferences.Darstellung.HELL -> false
                WidgetPreferences.Darstellung.AUTOMATISCH -> night
                WidgetPreferences.Darstellung.TRANSPARENT -> true
            }
            val bg = when (mode) {
                WidgetPreferences.Darstellung.TRANSPARENT -> Color.argb(175, 20, 20, 20)
                else -> if (dark) Color.rgb(27, 27, 27) else Color.WHITE
            }
            val primary = if (dark) Color.WHITE else Color.BLACK
            val secondary = if (dark) Color.rgb(205, 205, 205) else Color.rgb(55, 55, 55)
            views.setInt(R.id.widgetUnifiedRoot, "setBackgroundColor", bg)
            listOf(
                R.id.widgetSiteName, R.id.widgetLine1, R.id.widgetLine2, R.id.widgetLine3,
                R.id.widgetLine4, R.id.widgetLine5, R.id.widgetLine6, R.id.widgetLine7,
                R.id.widgetLine8, R.id.widgetLine9, R.id.widgetLine10,
                R.id.widgetStatusLine1, R.id.widgetStatusLine2,
                R.id.widgetSubSiteName, R.id.widgetSubSubSiteName,
                R.id.widgetLocationLine1, R.id.widgetCameraOnlyText
            ).forEach { views.setTextColor(it, primary) }
            listOf(R.id.widgetHeaderTitle, R.id.widgetLatestTitle, R.id.widgetSubtitle, R.id.widgetLocationLine2)
                .forEach { views.setTextColor(it, secondary) }
        }

        private fun pendingForMainClick(
            context: Context,
            widgetId: Int,
            click: WidgetPreferences.HauptKlick,
            data: WidgetData
        ): PendingIntent {
            if (data.baustelleId == null && click !in setOf(
                    WidgetPreferences.HauptKlick.AKTUELLE_BAUSTELLE,
                    WidgetPreferences.HauptKlick.KAMERA,
                    WidgetPreferences.HauptKlick.STARTSEITE
                )
            ) {
                return activityPending(context, widgetId * 20 + 19, R.id.aktuelleBaustelleFragment)
            }
            return when (click) {
                WidgetPreferences.HauptKlick.AKTUELLER_BTB -> activityPending(context, widgetId * 20 + 1, R.id.bautagebuchFragment, btbArgs(data))
                WidgetPreferences.HauptKlick.AKTUELLE_BAUSTELLE -> activityPending(context, widgetId * 20 + 2, R.id.aktuelleBaustelleFragment)
                WidgetPreferences.HauptKlick.KAMERA -> activityPending(context, widgetId * 20 + 3, R.id.kameraFragment, cameraArgs())
                WidgetPreferences.HauptKlick.ABNAHMEN -> activityPending(context, widgetId * 20 + 4, R.id.abnahmelisteFragment, abnahmeListArgs(data))
                WidgetPreferences.HauptKlick.MAENGEL -> activityPending(context, widgetId * 20 + 5, R.id.maengellisteFragment, mangelArgs(data))
                WidgetPreferences.HauptKlick.STARTSEITE -> activityPending(context, widgetId * 20 + 6, R.id.startmenueFragment)
            }
        }

        private fun pendingForAction(
            context: Context,
            widgetId: Int,
            action: WidgetPreferences.ButtonAktion,
            data: WidgetData,
            slot: Int
        ): PendingIntent {
            val request = widgetId * 100 + 20 + slot
            if (data.baustelleId == null && action !in setOf(
                    WidgetPreferences.ButtonAktion.KAMERA,
                    WidgetPreferences.ButtonAktion.POSITION_AKTUALISIEREN
                )
            ) {
                return activityPending(context, request, R.id.aktuelleBaustelleFragment)
            }
            return when (action) {
                WidgetPreferences.ButtonAktion.PUNKT_4 -> activityPending(context, request, R.id.bautagebuchFragment, btbArgs(data, 4, true))
                WidgetPreferences.ButtonAktion.PUNKT_5 -> activityPending(context, request, R.id.bautagebuchFragment, btbArgs(data, 5, true))
                WidgetPreferences.ButtonAktion.PUNKT_6 -> activityPending(context, request, R.id.bautagebuchFragment, btbArgs(data, 6, true))
                WidgetPreferences.ButtonAktion.PUNKT_7 -> activityPending(context, request, R.id.bautagebuchFragment, btbArgs(data, 7, true))
                WidgetPreferences.ButtonAktion.PUNKT_8 -> activityPending(context, request, R.id.bautagebuchFragment, btbArgs(data, 8, true))
                WidgetPreferences.ButtonAktion.NEUE_ABNAHME -> activityPending(context, request, R.id.abnahmeEditorFragment, newAbnahmeArgs(data))
                WidgetPreferences.ButtonAktion.KAMERA -> activityPending(context, request, R.id.kameraFragment, cameraArgs())
                WidgetPreferences.ButtonAktion.AKTUELLER_BTB -> activityPending(context, request, R.id.bautagebuchFragment, btbArgs(data))
                WidgetPreferences.ButtonAktion.ABNAHMEN -> activityPending(context, request, R.id.abnahmelisteFragment, abnahmeListArgs(data))
                WidgetPreferences.ButtonAktion.MAENGEL -> activityPending(context, request, R.id.maengellisteFragment, mangelArgs(data))
                WidgetPreferences.ButtonAktion.POSITION_AKTUALISIEREN -> refreshPending(context, widgetId)
            }
        }

        private fun activityPending(
            context: Context,
            requestCode: Int,
            destination: Int,
            args: Bundle? = null
        ): PendingIntent {
            val intent = Intent(context, HauptActivity::class.java).apply {
                putExtra(HauptActivity.EXTRA_NAV_DEST_ID, destination)
                putExtra(HauptActivity.EXTRA_WIDGET_LAUNCH, true)
                if (args != null) putExtra(HauptActivity.EXTRA_NAV_ARGS, args)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            return PendingIntent.getActivity(context, requestCode, intent, pendingFlags())
        }

        private fun refreshPending(context: Context, widgetId: Int): PendingIntent {
            val intent = Intent(context, CurrentSiteWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_POSITION
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            return PendingIntent.getBroadcast(context, widgetId * 100 + 99, intent, pendingFlags())
        }

        private fun pendingFlags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT

        private fun cameraArgs(): Bundle = Bundle().apply {
            putBoolean("widgetCameraLaunch", true)
        }

        private fun btbArgs(data: WidgetData, section: Int? = null, openEditor: Boolean = false): Bundle = Bundle().apply {
            data.entry?.let {
                putString("datumIso", it.datumIso)
                putString("schichtTyp", it.schicht.name)
            }
            section?.let { putInt("widgetSection", it) }
            putBoolean("widgetOpenEditor", openEditor)
        }

        private fun newAbnahmeArgs(data: WidgetData): Bundle = Bundle().apply {
            putLong("baustelleId", data.baustelleId ?: -1L)
            putString("baustelleName", data.mainName)
            putLong("hauptKnotenId", data.hauptKnotenId ?: -1L)
            putString("abnahmeId", "")
        }

        private fun abnahmeListArgs(data: WidgetData): Bundle = Bundle().apply {
            putLong("baustelleId", data.baustelleId ?: -1L)
            putString("baustelleName", data.mainName)
            putLong("hauptKnotenId", data.hauptKnotenId ?: -1L)
            putString("filter", "ALLE")
        }

        private fun mangelArgs(data: WidgetData): Bundle = Bundle().apply {
            putLong("hauptKnotenId", data.hauptKnotenId ?: -1L)
            putLong("altBaustelleId", data.baustelleId ?: -1L)
            putString("baustelleName", data.mainName)
        }

        private fun buttonLabel(action: WidgetPreferences.ButtonAktion, expanded: Boolean): String {
            if (!expanded) return shortLabel(action)
            return when (action) {
                WidgetPreferences.ButtonAktion.PUNKT_4 -> "Punkt 4 – Eintrag"
                WidgetPreferences.ButtonAktion.PUNKT_5 -> "Punkt 5 – Eintrag"
                WidgetPreferences.ButtonAktion.PUNKT_6 -> "Punkt 6 – Eintrag"
                WidgetPreferences.ButtonAktion.PUNKT_7 -> "Punkt 7 – Eintrag"
                WidgetPreferences.ButtonAktion.PUNKT_8 -> "Punkt 8 – Eintrag"
                WidgetPreferences.ButtonAktion.NEUE_ABNAHME -> "Neue Abnahme"
                WidgetPreferences.ButtonAktion.KAMERA -> "Kamera"
                WidgetPreferences.ButtonAktion.AKTUELLER_BTB -> "BTB öffnen"
                WidgetPreferences.ButtonAktion.ABNAHMEN -> "Abnahmeliste"
                WidgetPreferences.ButtonAktion.MAENGEL -> "Mängelliste"
                WidgetPreferences.ButtonAktion.POSITION_AKTUALISIEREN -> "Aktualisieren"
            }
        }

        private fun shortLabel(action: WidgetPreferences.ButtonAktion): String = when (action) {
            WidgetPreferences.ButtonAktion.PUNKT_4 -> "+ Arbeit"
            WidgetPreferences.ButtonAktion.PUNKT_5 -> "+ Abn.-Text"
            WidgetPreferences.ButtonAktion.PUNKT_6 -> "+ Einweis."
            WidgetPreferences.ButtonAktion.PUNKT_7 -> "+ Besonderh."
            WidgetPreferences.ButtonAktion.PUNKT_8 -> "+ Mangel"
            WidgetPreferences.ButtonAktion.NEUE_ABNAHME -> "+ Abnahme"
            WidgetPreferences.ButtonAktion.KAMERA -> "Kamera"
            WidgetPreferences.ButtonAktion.AKTUELLER_BTB -> "BTB"
            WidgetPreferences.ButtonAktion.ABNAHMEN -> "Abnahmen"
            WidgetPreferences.ButtonAktion.MAENGEL -> "Mängel"
            WidgetPreferences.ButtonAktion.POSITION_AKTUALISIEREN -> "Aktual."
        }

        private data class EntryLine(
            val time: String?,
            val text: String,
            val label: String,
            val order: Int,
            val sortMinute: Int? = null
        )

        private data class WidgetData(
            val baustelleId: Long?,
            val hauptKnotenId: Long?,
            val mainName: String,
            val displayName: String,
            val unterName: String?,
            val unterUnterName: String?,
            val entry: BautagebuchEintrag?,
            val photoEntries: List<EntryLine>,
            val abnahme: AbnahmeSpeicher.Zaehler,
            val mangel: MangelFachlogik.Zaehler
        ) {
            companion object {
                fun empty() = WidgetData(
                    baustelleId = null,
                    hauptKnotenId = null,
                    mainName = "Keine Baustelle gewählt",
                    displayName = "Keine Baustelle gewählt",
                    unterName = null,
                    unterUnterName = null,
                    entry = null,
                    photoEntries = emptyList(),
                    abnahme = AbnahmeSpeicher.Zaehler(),
                    mangel = MangelFachlogik.Zaehler()
                )
            }
        }
    }
}

