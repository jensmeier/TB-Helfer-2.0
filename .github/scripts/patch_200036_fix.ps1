$ErrorActionPreference = 'Stop'

$repoRoot = (Get-Location).Path
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$storagePath = Join-Path $repoRoot 'app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchSpeicher.kt'
$mainPatch = Join-Path $repoRoot '.github/scripts/patch_200036.ps1'

if (-not (Test-Path $storagePath)) { throw "Fehlt: $storagePath" }
if (-not (Test-Path $mainPatch)) { throw "Fehlt: $mainPatch" }

$storage = [System.IO.File]::ReadAllText($storagePath).Replace("`r`n", "`n")
$marker = @'
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

if ($storage.Contains($marker)) {
    Write-Host 'BautagebuchSpeicher PDF-Finder: bereits vorbereitet'
} else {
    $pattern = '(?ms)(    private fun neuestePdfFuerVerlauf\(.*?\n    \): File\? \{\n)([ \t]*val base = context\.getExternalFilesDir\(Environment\.DIRECTORY_PICTURES\) \?: return null\n)'
    $m = [regex]::Match($storage, $pattern)
    if (-not $m.Success) {
        throw 'BautagebuchSpeicher PDF-Finder: Funktion neuestePdfFuerVerlauf konnte nicht eindeutig vorbereitet werden'
    }
    $insert = @'
        // Android 200036: Moderne Abschluss-PDFs immer über denselben kanonischen Finder
        // auflösen. Dessen Token entspricht dem tatsächlich verwendeten Dateinamen.
        if (!entry.btbId.isNullOrBlank()) {
            BtbAbschlussPdfFinder.neueste(context, entry)
                ?.takeIf { it.exists() }
                ?.let { return it }
        }
'@
    $replacement = $m.Groups[1].Value + $insert + $m.Groups[2].Value
    $storage = $storage.Substring(0, $m.Index) + $replacement + $storage.Substring($m.Index + $m.Length)
    [System.IO.File]::WriteAllText($storagePath, $storage, $utf8NoBom)
    Write-Host 'BautagebuchSpeicher PDF-Finder: vorbereitet'
}

Write-Host 'Starte korrigierten 200036-Hauptpatch ...'
& $mainPatch
