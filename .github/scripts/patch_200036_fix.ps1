$ErrorActionPreference = 'Stop'

$repoRoot = (Get-Location).Path
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$storagePath = Join-Path $repoRoot 'app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchSpeicher.kt'
$mainPatch = Join-Path $repoRoot '.github/scripts/patch_200036.ps1'

if (-not (Test-Path $storagePath)) { throw "Fehlt: $storagePath" }
if (-not (Test-Path $mainPatch)) { throw "Fehlt: $mainPatch" }

$storage = [System.IO.File]::ReadAllText($storagePath).Replace("`r`n", "`n")

$expected = @'
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

if ($storage.Contains($expected)) {
    Write-Host 'BautagebuchSpeicher PDF-Finder: bereits korrekt vorbereitet'
} else {
    # Repariert sowohl den originalen 200035-Stand als auch den teilweise geschriebenen
    # Stand des ersten 200036-Fixversuchs. Wir ersetzen nur den Funktionsanfang bis val base.
    $pattern = '(?ms)(    private fun neuestePdfFuerVerlauf\(\n.*?    \): File\? \{\n).*?(        val base = context\.getExternalFilesDir\(Environment\.DIRECTORY_PICTURES\) \?: return null\n)'
    $m = [regex]::Match($storage, $pattern)
    if (-not $m.Success) {
        throw 'BautagebuchSpeicher PDF-Finder: Funktion neuestePdfFuerVerlauf konnte nicht eindeutig repariert werden'
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

    # Hier-Strings besitzen absichtlich keinen verlässlichen Abschluss-Zeilenumbruch.
    # Deshalb den Umbruch zwischen Insert und val base explizit setzen.
    $replacement = $m.Groups[1].Value + $insert.TrimEnd("`r", "`n") + "`n" + $m.Groups[2].Value
    $storage = $storage.Substring(0, $m.Index) + $replacement + $storage.Substring($m.Index + $m.Length)
    [System.IO.File]::WriteAllText($storagePath, $storage, $utf8NoBom)

    $storage = [System.IO.File]::ReadAllText($storagePath).Replace("`r`n", "`n")
    if (-not $storage.Contains($expected)) {
        throw 'BautagebuchSpeicher PDF-Finder: Reparatur konnte nicht verifiziert werden'
    }
    Write-Host 'BautagebuchSpeicher PDF-Finder: repariert und verifiziert'
}

Write-Host 'Starte 200036-Hauptpatch ...'
& $mainPatch
