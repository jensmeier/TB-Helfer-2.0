$ErrorActionPreference = 'Stop'

$repoRoot = (Get-Location).Path
$mainPatch = Join-Path $repoRoot '.github/scripts/patch_200036.ps1'
$storagePath = Join-Path $repoRoot 'app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchSpeicher.kt'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

if (-not (Test-Path $mainPatch)) { throw "Fehlt: $mainPatch" }
if (-not (Test-Path $storagePath)) { throw "Fehlt: $storagePath" }

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

$storage = Read-Normalized $storagePath
if (-not $storage.Contains('Android 200036: Moderne Abschluss-PDFs')) {
    throw 'BautagebuchSpeicher PDF-Finder ist noch nicht vorbereitet; bitte nicht weiterpatchen'
}
Write-Host 'BautagebuchSpeicher PDF-Finder: bereits vorbereitet'

# 200035 muss lokal bereits vorhanden sein. Wir wenden ihn hier absichtlich nicht erneut an.
$pdfPath = Join-Path $repoRoot 'app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchPdf.kt'
$pdfSource = Read-Normalized $pdfPath
if (-not ($pdfSource.Contains('forceRed: Boolean = false') -and $pdfSource.Contains('renderRemovedPhotoBlock'))) {
    throw 'BautagebuchPdf.kt: 200035-Basis fehlt; Abbruch ohne weitere Änderungen'
}
Write-Host 'BautagebuchPdf.kt: 200035-Basis erkannt'

# Den Hauptpatch nicht ueber Kommentar-Ueberschriften schneiden. Diese koennen durch Encoding/
# Zeilenenden variieren. Stattdessen an der eindeutigen ersten Codezeile von Schritt 2 ansetzen.
$source = Read-Normalized $mainPatch
$marker = '$exportPath = Join-Path $repoRoot ''app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbExportPaket.kt'''
$start = $source.IndexOf($marker, [System.StringComparison]::Ordinal)
if ($start -lt 0) {
    throw '200036-Hauptpatch: Start von Schritt 2 (BtbExportPaket) nicht gefunden'
}

$remainder = $source.Substring($start)
Write-Host 'Starte 200036 ab Schritt 2 ...'
$block = [scriptblock]::Create($remainder)
& $block

# Hotfix fuer den ersten 200036-Patch: Ein XML-Kommentar wurde innerhalb des offenen
# MaterialCardView-Starttags eingefuegt. XML erlaubt dort keine Kommentare.
$layoutPath = Join-Path $repoRoot 'app/src/main/res/layout/fragment_bautagebuch.xml'
$layout = Read-Normalized $layoutPath
$badCommentPattern = '(?m)^[ \t]*<!-- Android 200036: New-BTB-Karte folgt dem Theme statt festem hellem Hintergrund\. -->\n?'
$layoutFixed = [regex]::Replace($layout, $badCommentPattern, '')
if (-not $layoutFixed.Contains('app:cardBackgroundColor="?attr/colorSurface"')) {
    throw 'fragment_bautagebuch.xml: erwartete 200036-NightMode-Aenderung fehlt'
}
try {
    [void]([xml]$layoutFixed)
} catch {
    throw ('fragment_bautagebuch.xml bleibt ungueltig: ' + $_.Exception.Message)
}
if ($layoutFixed -ne $layout) {
    Write-Normalized $layoutPath $layoutFixed
    Write-Host 'fragment_bautagebuch.xml: XML-Kommentar aus Starttag entfernt'
} else {
    Write-Host 'fragment_bautagebuch.xml: XML bereits repariert'
}

Write-Host 'PATCH_200036_XML_FIX_FERTIG'
