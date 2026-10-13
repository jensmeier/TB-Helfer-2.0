$ErrorActionPreference = 'Stop'

$repoRoot = (Get-Location).Path
$basePatch = Join-Path $repoRoot '.github/scripts/patch_200038.ps1'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$pdfSelectPath = Join-Path $repoRoot 'app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPdfAuswahlFragment.kt'
$versioningPath = Join-Path $repoRoot 'app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbDokumentVersionierung.kt'

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

if (-not (Test-Path $basePatch)) { throw "Missing: $basePatch" }

# First apply the complete 200038 feature patch. It is idempotent and protects unrelated local files.
& powershell -ExecutionPolicy Bypass -File $basePatch
if ($LASTEXITCODE -ne 0) { throw "patch_200038.ps1 failed (ExitCode $LASTEXITCODE)" }

$pdfSelect = Read-Normalized $pdfSelectPath
$versioning = Read-Normalized $versioningPath

# BtbDokumentVersionierung is not part of the older local 200035-200037 patch set.
# Refuse to overwrite unexpected user changes unless this exact 200038-v2 marker is already present.
if (-not $versioning.Contains('Android 200038: external BTB content such as photo selection')) {
    & git diff --quiet -- 'app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbDokumentVersionierung.kt'
    $code = $LASTEXITCODE
    if ($code -eq 1) { throw 'BtbDokumentVersionierung.kt has unexpected local changes; aborting instead of overwriting them.' }
    if ($code -ne 0) { throw "git diff failed for BtbDokumentVersionierung.kt (ExitCode $code)" }
}

# Keep the current concrete BTB entry so external photo-selection changes can invalidate its closure.
$old = @'
    private var currentBlocks: List<Block> = emptyList()
    private var activeBtbIdArg: String? = null
'@
$new = @'
    private var currentBlocks: List<Block> = emptyList()
    private var currentEntry: BautagebuchEintrag? = null
    private var activeBtbIdArg: String? = null
'@
$pdfSelect = Replace-Once $pdfSelect $old $new 'Photo picker current entry state'

# Match only the stable assignment line. The following comment differs between the checked-in
# 200034 source and the locally patched 200037 source, so it must not be part of the literal match.
$old = '            maxFotos = entry?.maxFotosInPdf ?: 60'
$new = "            maxFotos = entry?.maxFotosInPdf ?: 60`n            currentEntry = entry"
$pdfSelect = Replace-Once $pdfSelect $old $new 'Photo picker current entry assignment'

# Any include/exclude change after the first successful closure makes the old closure document invalid.
$old = @'
                        updateCounts(tvCounts, b)
                        updateSelectedTotal()
                        scheduleAutosave()
'@
$new = @'
                        updateCounts(tvCounts, b)
                        updateSelectedTotal()
                        invalidateClosureForPhotoChange()
                        scheduleAutosave()
'@
$pdfSelect = Replace-Once $pdfSelect $old $new 'Photo picker checkbox invalidation'

$old = @'
    private fun removePhotoFromCurrentBtb(f: FotoEntity, displayNumber: Int) {
        selection.getOrPut(f.bildtyp.name) { LinkedHashSet() }.remove(f.id)
        render(currentBlocks)
        scheduleAutosave()
'@
$new = @'
    private fun invalidateClosureForPhotoChange() {
        if (!hasOriginalFotoBasis) return
        val entry = currentEntry ?: return
        val appContext = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            BtbDokumentVersionierung.invalidiereAbschlussWegenExternerAenderung(appContext, entry)
        }
    }

    private fun removePhotoFromCurrentBtb(f: FotoEntity, displayNumber: Int) {
        selection.getOrPut(f.bildtyp.name) { LinkedHashSet() }.remove(f.id)
        render(currentBlocks)
        invalidateClosureForPhotoChange()
        scheduleAutosave()
'@
$pdfSelect = Replace-Once $pdfSelect $old $new 'Photo picker external invalidation helper'

$old = @'
                if (result > 0) {
                    BtbPhotoTextOverrideStore.setOverrideForIds(
'@
$new = @'
                if (result > 0) {
                    currentEntry?.takeIf { hasOriginalFotoBasis }?.let { entry ->
                        BtbDokumentVersionierung.invalidiereAbschlussWegenExternerAenderung(appContext, entry)
                    }
                    BtbPhotoTextOverrideStore.setOverrideForIds(
'@
$pdfSelect = Replace-Once $pdfSelect $old $new 'Permanent photo delete invalidation'

# Add a dedicated public lifecycle operation for changes stored outside the BTB JSON (photo selection/assignment).
$old = @'
    fun spiegeleBaselineFuerRenderer(context: Context, entry: BautagebuchEintrag) {
'@
$new = @'
    /**
     * Android 200038: external BTB content such as photo selection lives outside the main BTB JSON.
     * Changing it after the first successful closure must invalidate the current closure document
     * even when the BTB JSON hash itself did not change. The immutable first original is untouched.
     */
    fun invalidiereAbschlussWegenExternerAenderung(context: Context, entry: BautagebuchEintrag) {
        val baseline = baselineFile(context, entry)?.takeIf { it.exists() } ?: return
        val current = currentFile(context, entry)?.takeIf { it.exists() } ?: return
        runCatching {
            loescheAktuellesAbschlussDokument(context, entry)

            val meta = readMeta(context, entry) ?: JSONObject().apply {
                put("btbId", entry.btbId)
            }
            meta.put("pdfGueltig", false)
            meta.put("abschlussPdfDateiname", JSONObject.NULL)
            writeMeta(context, entry, meta)

            val currentJson = JSONObject(current.readText(Charsets.UTF_8))
            currentJson.put("abschlussStatus", BtbAbschlussStatus.NACHBEARBEITUNG.name)
            currentJson.put("istFertig", false)
            currentJson.put("abschlussPdfDateiname", JSONObject.NULL)
            currentJson.put("dokumentVersion", 0)
            current.writeText(currentJson.toString(2), Charsets.UTF_8)
        }
    }

    fun spiegeleBaselineFuerRenderer(context: Context, entry: BautagebuchEintrag) {
'@
$versioning = Replace-Once $versioning $old $new 'BTB external-change invalidation API'

Write-Normalized $pdfSelectPath $pdfSelect
Write-Normalized $versioningPath $versioning

$verifyPdfSelect = Read-Normalized $pdfSelectPath
$verifyVersioning = Read-Normalized $versioningPath
if (-not $verifyPdfSelect.Contains('invalidateClosureForPhotoChange()')) { throw '200038-v2 verify: photo-selection invalidation call missing' }
if (-not $verifyPdfSelect.Contains('currentEntry: BautagebuchEintrag?')) { throw '200038-v2 verify: current entry state missing' }
if (-not $verifyVersioning.Contains('fun invalidiereAbschlussWegenExternerAenderung(')) { throw '200038-v2 verify: external invalidation API missing' }

Write-Host 'PATCH_200038_V2_FERTIG'
Write-Host 'Changed: 200038 base + BtbPdfAuswahlFragment.kt + BtbDokumentVersionierung.kt'
