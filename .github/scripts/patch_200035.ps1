$ErrorActionPreference = 'Stop'

$repoRoot = (Get-Location).Path
$pythonPatch = Join-Path $repoRoot '.github/scripts/patch_200035.py'
$sourcePath = Join-Path $repoRoot 'app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchPdf.kt'
$gradlePath = Join-Path $repoRoot 'app/build.gradle.kts'

if (-not (Test-Path $pythonPatch)) { throw "Fehlt: $pythonPatch" }
if (-not (Test-Path $sourcePath)) { throw "Fehlt: $sourcePath" }
if (-not (Test-Path $gradlePath)) { throw "Fehlt: $gradlePath" }

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$patchText = [System.IO.File]::ReadAllText($pythonPatch).Replace("`r`n", "`n")
$source = [System.IO.File]::ReadAllText($sourcePath).Replace("`r`n", "`n")

if ($source.Contains('forceRed: Boolean = false') -and $source.Contains('renderRemovedPhotoBlock')) {
    Write-Host 'BautagebuchPdf.kt: 200035 bereits angewendet'
} else {
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

    function Replace-First([string]$text, [string]$old, [string]$new) {
        $idx = $text.IndexOf($old, [System.StringComparison]::Ordinal)
        if ($idx -lt 0) { throw 'Replace-First: Text nicht gefunden' }
        return $text.Substring(0, $idx) + $new + $text.Substring($idx + $old.Length)
    }

    # Liest die bereits versionierte Python-Patchbeschreibung nur als Text und führt die dort
    # definierten once()/exact()-Ersetzungen in derselben Reihenfolge mit PowerShell aus.
    # Dadurch braucht Windows selbst keine Python-Installation.
    $rx = [regex]::new("(?s)(once|exact)\(\s*'''(.*?)''',\s*'''(.*?)''',\s*(?:(\d+)\s*,\s*)?\"([^\"]+)\"\s*\)")
    $matches = $rx.Matches($patchText)
    if ($matches.Count -eq 0) { throw 'Keine Patch-Ersetzungen in patch_200035.py gefunden.' }

    foreach ($m in $matches) {
        $kind = $m.Groups[1].Value
        $old = $m.Groups[2].Value
        $new = $m.Groups[3].Value
        $expected = if ($kind -eq 'exact') { [int]$m.Groups[4].Value } else { 1 }
        $label = $m.Groups[5].Value
        $count = Count-Literal $source $old

        if ($count -ne $expected) {
            throw "$label`: erwartet $expected Treffer, gefunden $count"
        }

        if ($kind -eq 'once') {
            $source = Replace-First $source $old $new
        } else {
            $source = $source.Replace($old, $new)
        }
        Write-Host "OK: $label"
    }

    [System.IO.File]::WriteAllText($sourcePath, $source, $utf8NoBom)
    Write-Host 'BautagebuchPdf.kt: PATCH_200035_OK'
}

$gradle = [System.IO.File]::ReadAllText($gradlePath).Replace("`r`n", "`n")
if ($gradle.Contains('versionCode = 200035')) {
    Write-Host 'build.gradle.kts: 200035 bereits gesetzt'
} elseif ($gradle.Contains('versionCode = 200034')) {
    $gradle = $gradle.Replace('// Android 200034: zentraler Kamera-Guard, strikte btbId-Fotos, NightMode-Persistenz.', '// Android 200035: Fotozeilen im Original-Diff + rote entfernte BTB-Inhalte.')
    $gradle = $gradle.Replace('versionCode = 200034', 'versionCode = 200035')
    [System.IO.File]::WriteAllText($gradlePath, $gradle, $utf8NoBom)
    Write-Host 'build.gradle.kts: versionCode 200035 gesetzt'
} else {
    throw 'build.gradle.kts: weder 200034 noch 200035 gefunden.'
}

Write-Host 'PATCH_200035_FERTIG'
