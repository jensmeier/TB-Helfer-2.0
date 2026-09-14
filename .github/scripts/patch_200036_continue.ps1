$ErrorActionPreference = 'Stop'

$repoRoot = (Get-Location).Path
$mainPatch = Join-Path $repoRoot '.github/scripts/patch_200036.ps1'
$storagePath = Join-Path $repoRoot 'app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchSpeicher.kt'

if (-not (Test-Path $mainPatch)) { throw "Fehlt: $mainPatch" }
if (-not (Test-Path $storagePath)) { throw "Fehlt: $storagePath" }

$storage = [System.IO.File]::ReadAllText($storagePath).Replace("`r`n", "`n")
if (-not $storage.Contains('Android 200036: Moderne Abschluss-PDFs')) {
    throw 'BautagebuchSpeicher PDF-Finder ist noch nicht vorbereitet; bitte nicht weiterpatchen'
}
Write-Host 'BautagebuchSpeicher PDF-Finder: bereits vorbereitet -> Schritt 1 wird übersprungen'

$source = [System.IO.File]::ReadAllText($mainPatch).Replace("`r`n", "`n")
$section1 = "# -----------------------------------------------------------------------------`n# 1) Abschluss-PDF im Verlauf/Zeitraum-/Gesamtexport zuverlässig finden."
$section2 = "# -----------------------------------------------------------------------------`n# 2) Zeitraum-/Baustellenexport: PDF-Fallback, stabile Multi-BTB-Dateinamen und"
$start = $source.IndexOf($section1, [System.StringComparison]::Ordinal)
$next = $source.IndexOf($section2, [System.StringComparison]::Ordinal)
if ($start -lt 0 -or $next -le $start) {
    throw '200036-Hauptpatch: Abschnittsgrenzen 1/2 nicht gefunden'
}

$replacement = @'
# -----------------------------------------------------------------------------
# 1) Abschluss-PDF-Finder wurde bereits durch den Reparatur-Patch vorbereitet.
# -----------------------------------------------------------------------------
$storagePath = Join-Path $repoRoot 'app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchSpeicher.kt'
$storage = Read-Normalized $storagePath
if (-not $storage.Contains('Android 200036: Moderne Abschluss-PDFs')) {
    throw 'BautagebuchSpeicher PDF-Finder: vorbereiteter Stand fehlt'
}
Write-Host 'BautagebuchSpeicher PDF-Finder: bereits vorbereitet'

'@

$continued = $source.Substring(0, $start) + $replacement + $source.Substring($next)
Write-Host 'Starte 200036 ab Schritt 2 ...'
$block = [scriptblock]::Create($continued)
& $block
