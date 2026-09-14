$ErrorActionPreference = 'Stop'

$repoRoot = (Get-Location).Path
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$pdfSelectPath = Join-Path $repoRoot 'app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPdfAuswahlFragment.kt'
$quickPath = Join-Path $repoRoot 'app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbSchnellwechselInstaller.kt'
$activeContextPath = Join-Path $repoRoot 'app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbActiveContext.kt'
$rowXmlPath = Join-Path $repoRoot 'app/src/main/res/layout/item_btb_pdf_photo_row.xml'
$gradlePath = Join-Path $repoRoot 'app/build.gradle.kts'

function Read-Normalized([string]$path) {
    if (-not (Test-Path $path)) { throw "Missing: $path" }
    return [System.IO.File]::ReadAllText($path).Replace("`r`n", "`n")
}

function Write-Normalized([string]$path, [string]$text) {
    [System.IO.File]::WriteAllText($path, $text, $utf8NoBom)
}

function Count-Literal([string]$text, [string]$needle) {
    if ([string]::IsNullOrEmpty($needle)) { return 0 }
    $count = 0
    $pos = 0
    while ($true) {
        $idx = $text.IndexOf($needle, $pos, [System.StringComparison]::Ordinal)
        if ($idx -lt 0) { break }
        $count++
        $pos = $idx + $needle.Length
    }
    return $count
}

function Replace-Once([string]$text, [string]$old, [string]$new, [string]$label) {
    if ($text.Contains($new)) {
        Write-Host "${label}: already applied"
        return $text
    }
    $count = Count-Literal $text $old
    if ($count -ne 1) { throw "${label}: expected 1 match, found $count" }
    $idx = $text.IndexOf($old, [System.StringComparison]::Ordinal)
    return $text.Substring(0, $idx) + $new + $text.Substring($idx + $old.Length)
}

function Assert-CleanUnlessPatched([string]$relativePath, [string]$text, [string]$marker) {
    if ($text.Contains($marker)) { return }
    & git diff --quiet -- $relativePath
    $code = $LASTEXITCODE
    if ($code -eq 1) { throw "$relativePath has unexpected local changes; aborting instead of overwriting them." }
    if ($code -ne 0) { throw "git diff failed for $relativePath (ExitCode $code)" }
}

$pdfSelect = Read-Normalized $pdfSelectPath
$quick = Read-Normalized $quickPath
$activeContext = Read-Normalized $activeContextPath
$gradle = Read-Normalized $gradlePath

if (-not ($gradle.Contains('versionCode = 200037') -or $gradle.Contains('versionCode = 200038'))) {
    throw 'Android 200038 requires local versionCode 200037 or 200038.'
}

Assert-CleanUnlessPatched 'app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPdfAuswahlFragment.kt' $pdfSelect '// Android 200038: richer photo selection'
Assert-CleanUnlessPatched 'app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbSchnellwechselInstaller.kt' $quick '// Android 200038: inactive-after-completion'
Assert-CleanUnlessPatched 'app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbActiveContext.kt' $activeContext '// Android 200038: inactive marker wins'
$rowXml = Read-Normalized $rowXmlPath
Assert-CleanUnlessPatched 'app/src/main/res/layout/item_btb_pdf_photo_row.xml' $rowXml 'tvPhotoPrimary'

Write-Host '200038: local 200037/200038 base confirmed'

# ----- BtbPdfAuswahlFragment: richer metadata and safe delete/remove -----
$pdfSelect = Replace-Once $pdfSelect `
    'import com.google.android.material.snackbar.Snackbar' `
    "import com.google.android.material.snackbar.Snackbar`nimport com.google.android.material.dialog.MaterialAlertDialogBuilder" `
    'Photo picker dialog import'

$old = @'
    private var autosaveJob: Job? = null

    private var baustelleIdArg: Long = 0L
'@
$new = @'
    private var autosaveJob: Job? = null

    // Android 200038: richer photo selection plus protected original-photo handling.
    private var currentBlocks: List<Block> = emptyList()
    private var activeBtbIdArg: String? = null
    private var hasOriginalFotoBasis: Boolean = false
    private var originalFotoIds: Set<Long> = emptySet()
    private var textOverrides: Map<Long, String> = emptyMap()
    private var kmOverrides: Map<Long, BtbPhotoKmOverrideStore.KmOverride> = emptyMap()

    private var baustelleIdArg: Long = 0L
'@
$pdfSelect = Replace-Once $pdfSelect $old $new 'Photo picker state'

$old = @'
                    schichtTypArg
                ) ?: entry?.btbId
            }

            tvSub.text
'@
$new = @'
                    schichtTypArg
                ) ?: entry?.btbId
            }
            activeBtbIdArg = activeBtbId

            tvSub.text
'@
$pdfSelect = Replace-Once $pdfSelect $old $new 'Photo picker active btb id'

$old = @'
            // Selection = all - excluded (Default = all selected)
            selection.clear()
'@
$new = @'
            hasOriginalFotoBasis = withContext(Dispatchers.IO) {
                BtbOriginalFotoSpeicher.hatOriginal(appContext, activeBtbId)
            }
            originalFotoIds = withContext(Dispatchers.IO) {
                BtbOriginalFotoSpeicher.originalIds(appContext, activeBtbId)
            }
            textOverrides = withContext(Dispatchers.IO) {
                BtbPhotoTextOverrideStore.loadOverrides(
                    appContext, baustelleIdArg, datumIsoArg, shiftStartArg, shiftEndArg, schichtTypArg
                )
            }
            kmOverrides = withContext(Dispatchers.IO) {
                BtbPhotoKmOverrideStore.loadOverrides(
                    appContext, baustelleIdArg, datumIsoArg, shiftStartArg, shiftEndArg, schichtTypArg
                )
            }

            // Selection = all - excluded (Default = all selected)
            selection.clear()
'@
$pdfSelect = Replace-Once $pdfSelect $old $new 'Photo picker metadata load'

$pdfSelect = Replace-Once $pdfSelect `
    '            render(blocks)' `
    "            currentBlocks = blocks`n            render(currentBlocks)" `
    'Photo picker current blocks'

$old = @'
    private fun render(blocks: List<Block>) {
        llSections.removeAllViews()

        blocks.forEach { b ->
'@
$new = @'
    private fun render(blocks: List<Block>) {
        llSections.removeAllViews()
        val fotoNumberById = blocks
            .flatMap { it.fotos }
            .distinctBy { it.id }
            .sortedBy { it.erstelltAmEpochMs }
            .mapIndexed { index, foto -> foto.id to (index + 1) }
            .toMap()

        blocks.forEach { b ->
'@
$pdfSelect = Replace-Once $pdfSelect $old $new 'Photo picker numbering'

# Remove hard-coded black section header so dark mode inherits the theme color.
$pdfSelect = $pdfSelect.Replace("                setTextColor(resources.getColor(android.R.color.black, null))`n", '')

$old = @'
                b.fotos.forEach { f ->
                    val row = layoutInflater.inflate(R.layout.item_btb_pdf_photo_row, llFotos, false)
                    val img = row.findViewById<android.widget.ImageView>(R.id.imgThumb)
                    val tvTime = row.findViewById<TextView>(R.id.tvTime)
                    val cb = row.findViewById<MaterialCheckBox>(R.id.cb)

                    tvTime.text = timeOfFoto(f)
'@
$new = @'
                b.fotos.forEach { f ->
                    val displayNumber = fotoNumberById[f.id] ?: 0
                    val row = layoutInflater.inflate(R.layout.item_btb_pdf_photo_row, llFotos, false)
                    val img = row.findViewById<android.widget.ImageView>(R.id.imgThumb)
                    val tvPrimary = row.findViewById<TextView>(R.id.tvPhotoPrimary)
                    val tvMeta = row.findViewById<TextView>(R.id.tvPhotoMeta)
                    val tvDetail = row.findViewById<TextView>(R.id.tvPhotoDetail)
                    val cb = row.findViewById<MaterialCheckBox>(R.id.cb)
                    val btnDelete = row.findViewById<android.widget.ImageButton>(R.id.btnDelete)

                    tvPrimary.text = "Foto $displayNumber | ${photoDisplayText(f)}"
                    tvMeta.text = photoMetaText(f)
                    val detail = photoDetailText(f)
                    tvDetail.text = detail
                    tvDetail.visibility = if (detail.isBlank()) View.GONE else View.VISIBLE
'@
$pdfSelect = Replace-Once $pdfSelect $old $new 'Photo picker row metadata'

$old = @'
                    llFotos.addView(row)
'@
$new = @'
                    btnDelete.setOnClickListener {
                        confirmPhotoAction(f, displayNumber)
                    }

                    llFotos.addView(row)
'@
$pdfSelect = Replace-Once $pdfSelect $old $new 'Photo picker delete action'

$old = @'
    private fun updateCounts(tvCounts: TextView, b: Block) {
'@
$new = @'
    private fun photoDisplayText(f: FotoEntity): String {
        return textOverrides[f.id]?.trim()?.takeIf { it.isNotBlank() }
            ?: f.kurztext.trim().takeIf { it.isNotBlank() }
            ?: f.notiz?.trim()?.takeIf { it.isNotBlank() }
            ?: "ohne Text"
    }

    private fun photoKmText(f: FotoEntity): String {
        val override = kmOverrides[f.id]
        val von = override?.kmVon ?: f.km ?: return "KM -"
        val bis = override?.kmBis
        fun fmt(v: Double): String = String.format(java.util.Locale.GERMANY, "%.3f", v)
        return if (bis != null && kotlin.math.abs(bis - von) > 0.0000001) {
            "KM ${fmt(von)}-${fmt(bis)}"
        } else {
            "KM ${fmt(von)}"
        }
    }

    private fun photoMetaText(f: FotoEntity): String {
        val firma = f.firma.trim().takeIf { it.isNotBlank() } ?: "ohne Firma"
        return listOf(timeOfFoto(f), photoKmText(f), firma).joinToString(" | ")
    }

    private fun photoDetailText(f: FotoEntity): String {
        val out = mutableListOf<String>()
        f.objektKennung?.trim()?.takeIf { it.isNotBlank() }?.let(out::add)
        f.objektBereich?.trim()?.takeIf { it.isNotBlank() }?.let { out.add("Bereich $it") }
        val note = f.notiz?.trim().orEmpty()
        if (note.isNotBlank() && !note.equals(photoDisplayText(f), ignoreCase = true)) out.add(note)
        if (hasOriginalFotoBasis) {
            out.add(if (f.id in originalFotoIds) "Originalfoto" else "Nachtrag")
        }
        return out.joinToString(" | ")
    }

    private fun removePhotoFromCurrentBtb(f: FotoEntity, displayNumber: Int) {
        selection.getOrPut(f.bildtyp.name) { LinkedHashSet() }.remove(f.id)
        render(currentBlocks)
        scheduleAutosave()
        Snackbar.make(
            requireView(),
            "Foto $displayNumber aus BTB/PDF entfernt",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun confirmPhotoAction(f: FotoEntity, displayNumber: Int) {
        val isProtectedOriginal = hasOriginalFotoBasis && f.id in originalFotoIds
        val title = "Foto $displayNumber l\u00F6schen?"
        val base = "${photoDisplayText(f)}\n${photoMetaText(f)}"
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setNegativeButton("Abbrechen", null)

        if (isProtectedOriginal) {
            builder
                .setMessage(
                    base +
                        "\n\nDieses Foto geh\u00F6rt zum unver\u00E4nderlichen ersten Abschluss. " +
                        "Es wird deshalb nicht dauerhaft gel\u00F6scht. Du kannst es aus dem aktuellen " +
                        "BTB/PDF entfernen; beim n\u00E4chsten Abschluss bleibt das Original nachvollziehbar."
                )
                .setPositiveButton("Aus BTB entfernen") { _, _ ->
                    removePhotoFromCurrentBtb(f, displayNumber)
                }
        } else {
            builder
                .setMessage(
                    base +
                        "\n\nNur aus BTB/PDF entfernen: Foto bleibt in der Baustellen-Fotoliste. " +
                        "Foto l\u00F6schen: Foto-Datensatz wird aus der App entfernt."
                )
                .setNeutralButton("Nur aus BTB/PDF") { _, _ ->
                    removePhotoFromCurrentBtb(f, displayNumber)
                }
                .setPositiveButton("Foto l\u00F6schen") { _, _ ->
                    deletePhotoPermanently(f, displayNumber)
                }
        }
        builder.show()
    }

    private fun deletePhotoPermanently(f: FotoEntity, displayNumber: Int) {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                val result = ServiceLocator.fotoRepository.loeschen(f)
                if (result > 0) {
                    BtbPhotoTextOverrideStore.setOverrideForIds(
                        appContext, baustelleIdArg, datumIsoArg, shiftStartArg, shiftEndArg,
                        schichtTypArg, listOf(f.id), null
                    )
                    BtbPhotoStartOverrideStore.setOverrideForIds(
                        appContext, baustelleIdArg, datumIsoArg, shiftStartArg, shiftEndArg,
                        schichtTypArg, listOf(f.id), null
                    )
                    BtbPhotoKmOverrideStore.setOverrideForIds(
                        appContext, baustelleIdArg, datumIsoArg, shiftStartArg, shiftEndArg,
                        schichtTypArg, listOf(f.id), null
                    )
                }
                result
            }

            if (!isAdded || view == null) return@launch
            if (deleted <= 0) {
                Snackbar.make(requireView(), "Foto konnte nicht gel\u00F6scht werden", Snackbar.LENGTH_LONG).show()
                return@launch
            }

            selection[f.bildtyp.name]?.remove(f.id)
            allIdsByTypeInShift[f.bildtyp.name] = allIdsByTypeInShift[f.bildtyp.name].orEmpty()
                .filterNot { it == f.id }
            textOverrides = textOverrides - f.id
            kmOverrides = kmOverrides - f.id
            currentBlocks = currentBlocks.mapNotNull { block ->
                val remaining = block.fotos.filterNot { it.id == f.id }
                if (remaining.isEmpty() && filterBildtyp.isNullOrBlank()) null else block.copy(fotos = remaining)
            }
            persistNow()
            render(currentBlocks)
            Snackbar.make(requireView(), "Foto $displayNumber gel\u00F6scht", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun updateCounts(tvCounts: TextView, b: Block) {
'@
$pdfSelect = Replace-Once $pdfSelect $old $new 'Photo picker helpers'

# ----- Quick-open: inactive-after-completion is not an active BTB -----
$old = @'
                val activeId = BautagebuchSpeicher.aktiveBtbId(appContext, altBaustelleId, baustelleName)
                val items = BautagebuchSpeicher.verlaufFuerBaustelle(appContext, altBaustelleId, baustelleName)
'@
$new = @'
                // Android 200038: inactive-after-completion beats any stale active pointer.
                val rawActiveId = BautagebuchSpeicher.aktiveBtbId(appContext, altBaustelleId, baustelleName)
                val inactiveId = BtbActivePointerControl.nachAbschlussInaktiveBtbId(
                    appContext, altBaustelleId, baustelleName
                )
                val activeId = rawActiveId?.takeUnless { it == inactiveId }
                val items = BautagebuchSpeicher.verlaufFuerBaustelle(appContext, altBaustelleId, baustelleName)
'@
$quick = Replace-Once $quick $old $new 'Quick-open effective active id'

# ----- Canonical active context: same inactive-marker rule everywhere -----
$old = @'
        val entry = BautagebuchSpeicher.ladenById(appContext, baustelleId, name, btbId) ?: return null
'@
$new = @'
        // Android 200038: inactive marker wins over a stale pointer after a completed BTB.
        val inactiveId = BtbActivePointerControl.nachAbschlussInaktiveBtbId(
            appContext, baustelleId, name
        )
        if (inactiveId == btbId) return null
        val entry = BautagebuchSpeicher.ladenById(appContext, baustelleId, name, btbId) ?: return null
'@
$activeContext = Replace-Once $activeContext $old $new 'Active context inactive marker'

# ----- Photo row layout -----
$rowXmlNew = @'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/rootRow"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:padding="8dp">

    <ImageView
        android:id="@+id/imgThumb"
        android:layout_width="96dp"
        android:layout_height="96dp"
        android:scaleType="centerCrop"
        android:contentDescription="Foto" />

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:layout_marginStart="12dp"
        android:orientation="vertical">

        <TextView
            android:id="@+id/tvPhotoPrimary"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:maxLines="2"
            android:ellipsize="end"
            android:text="Foto 6 | Test1.0"
            android:textColor="?attr/colorOnBackground"
            android:textStyle="bold"
            android:textSize="15sp" />

        <TextView
            android:id="@+id/tvPhotoMeta"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="3dp"
            android:maxLines="2"
            android:ellipsize="end"
            android:text="19:59 | KM 3,805 | bbg"
            android:textColor="?attr/colorOnBackground"
            android:alpha="0.82"
            android:textSize="13sp" />

        <TextView
            android:id="@+id/tvPhotoDetail"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="3dp"
            android:maxLines="2"
            android:ellipsize="end"
            android:text="Gleisquerung | Originalfoto"
            android:textColor="?attr/colorOnBackground"
            android:alpha="0.68"
            android:textSize="12sp" />
    </LinearLayout>

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:gravity="center">

        <com.google.android.material.checkbox.MaterialCheckBox
            android:id="@+id/cb"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:contentDescription="Im BTB/PDF verwenden" />

        <ImageButton
            android:id="@+id/btnDelete"
            android:layout_width="44dp"
            android:layout_height="44dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:src="@android:drawable/ic_menu_delete"
            android:contentDescription="Foto loeschen"
            app:tint="?attr/colorError" />
    </LinearLayout>

</LinearLayout>
'@
$rowXml = $rowXmlNew

# ----- Version -----
if ($gradle.Contains('versionCode = 200037')) {
    $gradle = $gradle.Replace('versionCode = 200037', 'versionCode = 200038')
} elseif (-not $gradle.Contains('versionCode = 200038')) {
    throw 'build.gradle.kts: expected local 200037/200038 versionCode missing'
}
$gradle = $gradle.Replace('// Android 200037: PM clock pickers + PDF grouping for same-name photos.', '// Android 200038: richer photo picker + safe photo removal + reliable old-BTB reopen.')

Write-Normalized $pdfSelectPath $pdfSelect
Write-Normalized $quickPath $quick
Write-Normalized $activeContextPath $activeContext
Write-Normalized $rowXmlPath $rowXml
Write-Normalized $gradlePath $gradle

$verifyPdfSelect = Read-Normalized $pdfSelectPath
$verifyQuick = Read-Normalized $quickPath
$verifyActive = Read-Normalized $activeContextPath
$verifyRow = Read-Normalized $rowXmlPath
$verifyGradle = Read-Normalized $gradlePath

if (-not $verifyPdfSelect.Contains('private fun confirmPhotoAction(')) { throw '200038 verify: photo action dialog missing' }
if (-not $verifyPdfSelect.Contains('Originalfoto')) { throw '200038 verify: original-photo protection missing' }
if (-not $verifyPdfSelect.Contains('tvPhotoPrimary')) { throw '200038 verify: rich photo metadata binding missing' }
if (-not $verifyQuick.Contains('val activeId = rawActiveId?.takeUnless { it == inactiveId }')) { throw '200038 verify: quick-open stale-pointer fix missing' }
if (-not $verifyActive.Contains('if (inactiveId == btbId) return null')) { throw '200038 verify: active-context stale-pointer fix missing' }
if (-not $verifyRow.Contains('@+id/btnDelete')) { throw '200038 verify: delete button missing' }
if (-not $verifyGradle.Contains('versionCode = 200038')) { throw '200038 verify: versionCode missing' }

Write-Host 'PATCH_200038_FERTIG'
Write-Host 'Changed: BtbPdfAuswahlFragment.kt, BtbSchnellwechselInstaller.kt, BtbActiveContext.kt, item_btb_pdf_photo_row.xml, build.gradle.kts'
