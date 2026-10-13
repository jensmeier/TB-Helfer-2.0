$ErrorActionPreference = 'Stop'

$repoRoot = (Get-Location).Path
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$storagePath = Join-Path $repoRoot 'app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchSpeicher.kt'
$mainPatch = Join-Path $repoRoot '.github/scripts/patch_200036.ps1'

if (-not (Test-Path $storagePath)) { throw "Fehlt: $storagePath" }
if (-not (Test-Path $mainPatch)) { throw "Fehlt: $mainPatch" }

$storage = [System.IO.File]::ReadAllText($storagePath).Replace("`r`n", "`n")
$startMarker = '    private fun neuestePdfFuerVerlauf('
$endMarker = '    fun sichererName('
$start = $storage.IndexOf($startMarker, [System.StringComparison]::Ordinal)
if ($start -lt 0) { throw 'BautagebuchSpeicher PDF-Finder: Funktionsanfang nicht gefunden' }
$end = $storage.IndexOf($endMarker, $start, [System.StringComparison]::Ordinal)
if ($end -lt 0) { throw 'BautagebuchSpeicher PDF-Finder: Funktionsende nicht gefunden' }

$replacement = @'
    private fun neuestePdfFuerVerlauf(
        context: Context,
        baustelleId: Long?,
        baustelleName: String,
        entry: BautagebuchEintrag
    ): File? {
        // Android 200036: Moderne Abschluss-PDFs immer über denselben kanonischen Finder
        // auflösen. Dessen Token entspricht dem tatsächlich verwendeten Dateinamen.
        if (!entry.btbId.isNullOrBlank()) {
            BtbAbschlussPdfFinder.neueste(context, entry)
                ?.takeIf { it.exists() }
                ?.let { return it }
        }
        val base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
        val top = if ((baustelleId ?: 0L) > 0L) "baustelle_${baustelleId}" else safeFolderName(baustelleName)
        val date = runCatching { LocalDate.parse(entry.datumIso, isoFmt) }.getOrNull() ?: return null
        val dir = File(base, "TB-Helfer/$top/BTB/${date.format(isoFmt)}")
        if (!dir.exists()) return null
        val idToken = entry.btbId?.let(::safeBtbToken)
        val all = dir.listFiles()?.filter { it.isFile && it.extension.equals("pdf", true) && !it.name.contains("_entwurf_", true) }.orEmpty()
        if (!idToken.isNullOrBlank()) {
            all.filter { it.name.contains(idToken, true) }.maxByOrNull { it.lastModified() }?.let { return it }
        }
        val dateKey = date.format(DateTimeFormatter.ofPattern("yyyy_MM_dd"))
        return all.filter { it.name.contains(dateKey) }.maxByOrNull { it.lastModified() }
    }

'@

$storage = $storage.Substring(0, $start) + $replacement + $storage.Substring($end)
[System.IO.File]::WriteAllText($storagePath, $storage, $utf8NoBom)

$check = [System.IO.File]::ReadAllText($storagePath).Replace("`r`n", "`n")
$mustHave = @(
    'private fun neuestePdfFuerVerlauf(',
    'BtbAbschlussPdfFinder.neueste(context, entry)',
    'val base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null',
    'fun sichererName('
)
foreach ($needle in $mustHave) {
    if (-not $check.Contains($needle)) { throw "BautagebuchSpeicher PDF-Finder: Verifikation fehlgeschlagen: $needle" }
}
Write-Host 'BautagebuchSpeicher PDF-Finder: deterministisch repariert und verifiziert'

Write-Host 'Starte 200036-Hauptpatch ...'
& $mainPatch
