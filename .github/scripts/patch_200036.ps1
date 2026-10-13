$ErrorActionPreference = 'Stop'

$repoRoot = (Get-Location).Path
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Read-Normalized([string]$path) {
    if (-not (Test-Path $path)) { throw "Fehlt: $path" }
    return [System.IO.File]::ReadAllText($path).Replace("`r`n", "`n")
}

function Write-Normalized([string]$path, [string]$text) {
    [System.IO.File]::WriteAllText($path, $text, $utf8NoBom)
}

function Replace-Once([string]$text, [string]$old, [string]$new, [string]$label) {
    if ($text.Contains($new)) {
        Write-Host "${label}: bereits angewendet"
        return $text
    }
    $idx = $text.IndexOf($old, [System.StringComparison]::Ordinal)
    if ($idx -lt 0) { throw "${label}: erwarteter Ausgangstext nicht gefunden" }
    $second = $text.IndexOf($old, $idx + $old.Length, [System.StringComparison]::Ordinal)
    if ($second -ge 0) { throw "${label}: Ausgangstext mehrfach gefunden" }
    return $text.Substring(0, $idx) + $new + $text.Substring($idx + $old.Length)
}

# 200036 baut auf der bereits vorbereiteten 200035-PDF-Original-Diff-Korrektur auf.
$pdfPath = Join-Path $repoRoot 'app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchPdf.kt'
$pdfSource = Read-Normalized $pdfPath
if (-not ($pdfSource.Contains('forceRed: Boolean = false') -and $pdfSource.Contains('renderRemovedPhotoBlock'))) {
    $patch200035 = Join-Path $repoRoot '.github/scripts/patch_200035.ps1'
    if (-not (Test-Path $patch200035)) { throw "200035 fehlt und Patch ist nicht vorhanden: $patch200035" }
    Write-Host '200035 ist noch nicht im Source-Stand -> wird zuerst angewendet'
    & $patch200035
    $pdfSource = Read-Normalized $pdfPath
    if (-not ($pdfSource.Contains('forceRed: Boolean = false') -and $pdfSource.Contains('renderRemovedPhotoBlock'))) {
        throw '200035 konnte nicht verifiziert werden'
    }
} else {
    Write-Host 'BautagebuchPdf.kt: 200035-Basis erkannt'
}

# -----------------------------------------------------------------------------
# 1) Abschluss-PDF im Verlauf/Zeitraum-/Gesamtexport zuverlässig finden.
#    Ursache: Dokumentdatei enthält nur den gekürzten btbToken; der alte Verlauf-Finder
#    suchte dagegen nach der kompletten UUID und lieferte deshalb bei modernen BTBs null.
# -----------------------------------------------------------------------------
$storagePath = Join-Path $repoRoot 'app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchSpeicher.kt'
$storage = Read-Normalized $storagePath
$old = @'
    ): File? {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
'@
$new = @'
    ): File? {
        // Android 200036: Moderne Abschluss-PDFs immer über denselben kanonischen Finder
        // auflösen. Dessen Token entspricht dem tatsächlich verwendeten Dateinamen.
        if (!entry.btbId.isNullOrBlank()) {
            BtbAbschlussPdfFinder.neueste(context, entry)
                ?.takeIf { it.exists() }
                ?.let { return it }
        }
        val base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
'@
$storage = Replace-Once $storage $old $new 'BautagebuchSpeicher PDF-Finder'
Write-Normalized $storagePath $storage

# -----------------------------------------------------------------------------
# 2) Zeitraum-/Baustellenexport: PDF-Fallback, stabile Multi-BTB-Dateinamen und
#    vollständiger DOCX-Kopf (Firma / verantwortlicher TB / Schicht / BTB-ID).
# -----------------------------------------------------------------------------
$exportPath = Join-Path $repoRoot 'app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbExportPaket.kt'
$export = Read-Normalized $exportPath

$old = @'
    private fun btbTextFromModell(model: BtbDruckModell): String {
        return buildString {
            appendLine("BAUTAGEBUCH")
            appendLine("Baustelle: ${model.baustelle}")
            appendLine("Datum: ${model.datumAnzeige}")
            appendLine("Zeit: ${model.zeitAnzeige}")
'@
$new = @'
    private fun btbTextFromModell(
        model: BtbDruckModell,
        identity: BtbDokumentIdentitaet.Identitaet
    ): String {
        return buildString {
            appendLine("BAUTAGEBUCH")
            appendLine("Baustelle: ${model.baustelle}")
            identity.firmenname?.takeIf { it.isNotBlank() }?.let { appendLine("Firma: $it") }
            appendLine("Verantwortlicher TB: ${identity.verantwortlicherTb}")
            appendLine("Schicht: ${identity.schichtAnzeige}")
            identity.btbId?.takeIf { it.isNotBlank() }?.let { appendLine("BTB-ID: $it") }
            appendLine("Datum: ${model.datumAnzeige}")
            appendLine("Zeit: ${model.zeitAnzeige}")
'@
$export = Replace-Once $export $old $new 'BtbExportPaket DOCX-Kopf'

$old = @'
            val btbTxtContent = btbTextFromModell(druckModell)
'@
$new = @'
            val dokumentIdentitaet = BtbDokumentIdentitaet.aufloesen(context, entry)
            val btbTxtContent = btbTextFromModell(druckModell, dokumentIdentitaet)
'@
$export = Replace-Once $export $old $new 'BtbExportPaket Einzel-DOCX Identitaet'

$old = @'
                v.pdfDatei?.takeIf { it.exists() }?.let { pdf ->
                    val name = if (v.schicht == SchichtTyp.NACHT) "BTB_${datum}_Nacht.pdf" else "BTB_${datum}_Tag.pdf"
                    addFile(zos, pdf, uniquePath("$root/BTB/$datum/$name", used))
                }
'@
$new = @'
                // Android 200036: Verlauf kann bei modernen BTBs aus Altcode eine null-pdfDatei
                // liefern. Deshalb zusätzlich den kanonischen Abschluss-PDF-Finder verwenden.
                val btbToken = BtbDokumentVersionierung.dokumentToken(v.entry)
                val pdfForExport = v.pdfDatei?.takeIf { it.exists() }
                    ?: BtbAbschlussPdfFinder.neueste(context, v.entry)?.takeIf { it.exists() }
                pdfForExport?.let { pdf ->
                    val name = "BTB_${datum}_${v.schicht.name}_${btbToken}.pdf"
                    addFile(zos, pdf, uniquePath("$root/BTB/$datum/$name", used))
                }
'@
$export = Replace-Once $export $old $new 'BtbExportPaket Zeitraum-PDF'

$old = @'
                addBinary(zos, uniquePath("$root/BTB/$datum/BTB_${datum}_${v.schicht.name}.docx", used), btbDocxBytes(btbTextFromModell(druckModell)))
'@
$new = @'
                val dokumentIdentitaet = BtbDokumentIdentitaet.aufloesen(context, v.entry)
                val docxName = "BTB_${datum}_${v.schicht.name}_${btbToken}.docx"
                addBinary(
                    zos,
                    uniquePath("$root/BTB/$datum/$docxName", used),
                    btbDocxBytes(btbTextFromModell(druckModell, dokumentIdentitaet))
                )
'@
$export = Replace-Once $export $old $new 'BtbExportPaket Zeitraum-DOCX'

# Moderne BTBs dürfen auch im exportierten DOCX/Bilderbezug nur exakt ihre eigene btbId sehen.
$old = @'
        val btbPlan = buildBtbFotoPlan(context, entry, fotos)
'@
$new = @'
        val btbFotos = BtbFotoBtbFilter.fotosFuerBtb(
            context = context,
            entry = entry,
            alleFotos = alleFotosDerBaustelle,
            legacyFromEpochMs = from,
            legacyToEpochMs = to
        ).sortedBy { it.erstelltAmEpochMs }
        val btbPlan = buildBtbFotoPlan(context, entry, btbFotos)
'@
$export = Replace-Once $export $old $new 'BtbExportPaket Einzel-BTB Foto-ID-Filter'

$old = @'
                val btbPlan = buildBtbFotoPlan(context, v.entry, alleFotosDerBaustelle.filter { f ->
                    val d = Instant.ofEpochMilli(f.erstelltAmEpochMs).atZone(ZoneId.systemDefault()).toLocalDate()
                    d == v.datum
                })
'@
$new = @'
                val (legacyFrom, legacyTo) = shiftWindowEpochMs(v.entry)
                val btbFotos = BtbFotoBtbFilter.fotosFuerBtb(
                    context = context,
                    entry = v.entry,
                    alleFotos = alleFotosDerBaustelle,
                    legacyFromEpochMs = legacyFrom,
                    legacyToEpochMs = legacyTo
                ).sortedBy { it.erstelltAmEpochMs }
                val btbPlan = buildBtbFotoPlan(context, v.entry, btbFotos)
'@
$export = Replace-Once $export $old $new 'BtbExportPaket Zeitraum-BTB Foto-ID-Filter'
Write-Normalized $exportPath $export

# -----------------------------------------------------------------------------
# 3) Einzel-BTB-ZIP: denselben vollständigen Dokumentkopf verwenden.
# -----------------------------------------------------------------------------
$singleZipPath = Join-Path $repoRoot 'app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbEinzelZip.kt'
$singleZip = Read-Normalized $singleZipPath
$old = @'
            add("BAUTAGEBUCH")
            add("Baustelle: ${entry.baustelleName}")
            add("Verantwortlicher TB: ${identity.verantwortlicherTb}")
            add("BTB-ID: ${entry.btbId}")
'@
$new = @'
            add("BAUTAGEBUCH")
            add("Baustelle: ${entry.baustelleName}")
            identity.firmenname?.takeIf { it.isNotBlank() }?.let { add("Firma: $it") }
            add("Verantwortlicher TB: ${identity.verantwortlicherTb}")
            add("Schicht: ${identity.schichtAnzeige}")
            add("BTB-ID: ${entry.btbId}")
'@
$singleZip = Replace-Once $singleZip $old $new 'BtbEinzelZip DOCX-Kopf'
Write-Normalized $singleZipPath $singleZip

# -----------------------------------------------------------------------------
# 4) Merge-sicherer Archivname: bei mehreren BTBs derselben Schicht am selben Tag
#    die stabile BTB-Kennung nicht mehr beim Normalisieren verlieren.
# -----------------------------------------------------------------------------
$mergePath = Join-Path $repoRoot 'app/src/main/java/de/tbhelfer/ui/bautagebuch/MergeSafeBaustellenExport.kt'
$merge = Read-Normalized $mergePath
$old = @'
        val withShift = Regex("(?i)^BTB_(\\d{4}-\\d{2}-\\d{2})_(TAG|NACHT|Tag|Nacht)(?:_[^.]*)?\\.(pdf|docx)$")
            .matchEntire(name)
        if (withShift != null) {
            val date = withShift.groupValues[1]
            val shift = withShift.groupValues[2].uppercase(Locale.ROOT)
            val newName = "BTB_${date}_${shift}_${responsibleToken}.${ext}"
            return path.substringBeforeLast('/') + "/" + newName
        }
'@
$new = @'
        val withShift = Regex("(?i)^BTB_(\\d{4}-\\d{2}-\\d{2})_(TAG|NACHT|Tag|Nacht)(?:_([^.]+))?\\.(pdf|docx)$")
            .matchEntire(name)
        if (withShift != null) {
            val date = withShift.groupValues[1]
            val shift = withShift.groupValues[2].uppercase(Locale.ROOT)
            // Android 200036: vorhandenen stabilen BTB-Suffix beibehalten. Sonst würden
            // mehrere BTBs derselben Schicht beim Gesamtexport auf denselben Namen fallen.
            val stableSuffix = withShift.groupValues.getOrNull(3)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let(::safeToken)
            val newName = listOfNotNull("BTB", date, shift, responsibleToken, stableSuffix)
                .joinToString("_") + ".${ext}"
            return path.substringBeforeLast('/') + "/" + newName
        }
'@
$merge = Replace-Once $merge $old $new 'MergeSafeBaustellenExport BTB-Dateiname'
Write-Normalized $mergePath $merge

# -----------------------------------------------------------------------------
# 5) Neuer BTB: echte Uhr/TimePicker statt Listen-Dropdown.
# -----------------------------------------------------------------------------
$fragmentPath = Join-Path $repoRoot 'app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchFragment.kt'
$fragment = Read-Normalized $fragmentPath
$old = @'
            acStart.setOnClickListener { showTimePopup(acStart, times) }
            acEnde.setOnClickListener { maybeAutoFillEndFromStart(); showTimePopup(acEnde, times) }
'@
$new = @'
            // Android 200036: Start/Ende werden wie die Wetterzeiten über die grafische Uhr gewählt.
            fun showClockPicker(field: MaterialAutoCompleteTextView, autoFillEndBeforeOpen: Boolean = false) {
                if (autoFillEndBeforeOpen) maybeAutoFillEndFromStart()
                val raw = field.text?.toString().orEmpty().trim()
                val initial = runCatching { LocalTime.parse(raw, timeFmt) }.getOrNull()
                    ?: runCatching { LocalTime.parse(suggestedNowQuarter(), timeFmt) }.getOrNull()
                    ?: LocalTime.now()
                TimePickerDialog(requireContext(), { _, h, m ->
                    val rounded = roundUpToQuarter(LocalTime.of(h, m))
                    field.setText(rounded.format(timeFmt), false)
                    if (field === acStart) maybeAutoFillEndFromStart()
                }, initial.hour, initial.minute, true).show()
            }
            acStart.setOnClickListener { showClockPicker(acStart) }
            acEnde.setOnClickListener { showClockPicker(acEnde, autoFillEndBeforeOpen = true) }
'@
$fragment = Replace-Once $fragment $old $new 'BautagebuchFragment TimePicker neuer BTB'

$old = @'
                        .setMessage("Tagesabschluss ist bereits aktiv. Abschluss-PDF jetzt erstellen?")
'@
$new = @'
                        .setMessage("Dieser BTB besitzt bereits ein unveränderliches Original. Der neue Abschluss ersetzt nur das aktuelle PDF; fachliche Abweichungen zum ersten Original bleiben rot. Abschluss-PDF jetzt erstellen?")
'@
$fragment = Replace-Once $fragment $old $new 'BautagebuchFragment Folgeabschluss-Hinweis'

$old = @'
                    .setMessage("PDF-Abschluss erstellen? Spätere Änderungen werden danach markiert und gezählt.")
'@
$new = @'
                    .setMessage("Erster Abschluss: Dieser Stand wird als unveränderliches Original festgeschrieben. Spätere fachliche Änderungen werden im Folge-PDF rot dargestellt. PDF-Abschluss jetzt erstellen?")
'@
$fragment = Replace-Once $fragment $old $new 'BautagebuchFragment Erstabschluss-Hinweis'
Write-Normalized $fragmentPath $fragment

# -----------------------------------------------------------------------------
# 6) Dialog/Neuer-BTB-Karte im Dunkelmodus lesbar und Uhrfelder ohne Dropdown-Pfeil.
# -----------------------------------------------------------------------------
$dialogPath = Join-Path $repoRoot 'app/src/main/res/layout/dialog_schicht.xml'
$dialog = Read-Normalized $dialogPath
$dialog = $dialog.Replace('app:endIconMode="dropdown_menu"', 'app:endIconMode="none"')
# Attribute gezielt an den beiden Uhrfeldern ergänzen, falls noch nicht vorhanden.
if (-not $dialog.Contains('android:hint="Start"')) {
    $old = @'
                android:id="@+id/acDialogStart"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:inputType="none"
'@
    $new = @'
                android:id="@+id/acDialogStart"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="Start"
                android:focusable="false"
                android:clickable="true"
                android:inputType="none"
'@
    $dialog = Replace-Once $dialog $old $new 'dialog_schicht Start-Uhrfeld'
}
if (-not $dialog.Contains('android:hint="Ende"')) {
    $old = @'
                android:id="@+id/acDialogEnde"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:inputType="none"
'@
    $new = @'
                android:id="@+id/acDialogEnde"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="Ende"
                android:focusable="false"
                android:clickable="true"
                android:inputType="none"
'@
    $dialog = Replace-Once $dialog $old $new 'dialog_schicht Ende-Uhrfeld'
}
Write-Normalized $dialogPath $dialog

$layoutPath = Join-Path $repoRoot 'app/src/main/res/layout/fragment_bautagebuch.xml'
$layout = Read-Normalized $layoutPath
if (-not $layout.Contains('<!-- Android 200036: New-BTB-Karte folgt dem Theme statt festem hellem Hintergrund. -->')) {
    $old = @'
            app:cardBackgroundColor="#F4F7F4"
            app:cardCornerRadius="14dp"
            app:cardElevation="0dp"
            app:strokeColor="#C8E6C9"
'@
    $new = @'
            <!-- Android 200036: New-BTB-Karte folgt dem Theme statt festem hellem Hintergrund. -->
            app:cardBackgroundColor="?attr/colorSurface"
            app:cardCornerRadius="14dp"
            app:cardElevation="0dp"
            app:strokeColor="?attr/colorPrimary"
'@
    $layout = Replace-Once $layout $old $new 'fragment_bautagebuch Nachtmodus-Karte'
}
$layout = $layout.Replace('android:textColor="#616161"', 'android:textColor="?attr/colorOnBackground"')
Write-Normalized $layoutPath $layout

# -----------------------------------------------------------------------------
# 7) Version hochsetzen. 200035 muss vorher angewendet sein.
# -----------------------------------------------------------------------------
$gradlePath = Join-Path $repoRoot 'app/build.gradle.kts'
$gradle = Read-Normalized $gradlePath
if ($gradle.Contains('versionCode = 200036')) {
    Write-Host 'build.gradle.kts: 200036 bereits gesetzt'
} else {
    if (-not $gradle.Contains('versionCode = 200035')) {
        throw 'build.gradle.kts: erwartete 200035-Basis fehlt'
    }
    $gradle = $gradle.Replace('versionCode = 200035', 'versionCode = 200036')
    $gradle = $gradle.Replace('// Android 200034: zentraler Kamera-Guard, strikte btbId-Fotos, NightMode-Persistenz.', '// Android 200036: Export-PDF/DOCX, Multi-BTB-Dateinamen, TimePicker und NightMode-Lesbarkeit.')
    Write-Host 'build.gradle.kts: versionCode 200036 gesetzt'
}
Write-Normalized $gradlePath $gradle

# Quellstand nach dem Patch noch einmal hart prüfen, damit ein Teilpatch nicht unbemerkt gebaut wird.
$verify = @{
    'BautagebuchSpeicher PDF-Finder' = (Read-Normalized $storagePath).Contains('Android 200036: Moderne Abschluss-PDFs')
    'BtbExportPaket PDF-Fallback' = (Read-Normalized $exportPath).Contains('val pdfForExport = v.pdfDatei?.takeIf { it.exists() }')
    'BtbExportPaket DOCX-Kopf' = (Read-Normalized $exportPath).Contains('appendLine("Verantwortlicher TB: ${identity.verantwortlicherTb}")')
    'BtbExportPaket btbId-Fotofilter' = (Read-Normalized $exportPath).Contains('BtbFotoBtbFilter.fotosFuerBtb(')
    'MergeSafe BTB-Suffix' = (Read-Normalized $mergePath).Contains('val stableSuffix = withShift.groupValues.getOrNull(3)')
    'Neuer-BTB TimePicker' = (Read-Normalized $fragmentPath).Contains('fun showClockPicker(field: MaterialAutoCompleteTextView')
    'NightMode Karte' = (Read-Normalized $layoutPath).Contains('Android 200036: New-BTB-Karte folgt dem Theme')
    'Version 200036' = (Read-Normalized $gradlePath).Contains('versionCode = 200036')
}
$failed = $verify.GetEnumerator() | Where-Object { -not $_.Value } | ForEach-Object { $_.Key }
if ($failed.Count -gt 0) { throw ('200036 Verifikation fehlgeschlagen: ' + ($failed -join ', ')) }

Write-Host 'PATCH_200036_FERTIG'
Write-Host 'Geändert: BautagebuchSpeicher.kt, BtbExportPaket.kt, BtbEinzelZip.kt, MergeSafeBaustellenExport.kt, BautagebuchFragment.kt, dialog_schicht.xml, fragment_bautagebuch.xml, build.gradle.kts'
