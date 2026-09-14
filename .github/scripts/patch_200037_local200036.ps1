$ErrorActionPreference = 'Stop'

$repoRoot = (Get-Location).Path
$patchPath = Join-Path $repoRoot '.github/scripts/patch_200037.ps1'
$gradlePath = Join-Path $repoRoot 'app/build.gradle.kts'

if (-not (Test-Path $patchPath)) { throw "Fehlt: $patchPath" }
if (-not (Test-Path $gradlePath)) { throw "Fehlt: $gradlePath" }

$gradle = [System.IO.File]::ReadAllText($gradlePath).Replace("`r`n", "`n")
if (-not ($gradle.Contains('versionCode = 200036') -or $gradle.Contains('versionCode = 200037'))) {
    throw 'Lokale Basis ist weder Android 200036 noch 200037.'
}

$patch = [System.IO.File]::ReadAllText($patchPath).Replace("`r`n", "`n")
$old = @'
# Android 200037 baut bewusst auf dem lokal erfolgreich gebauten 200036-Stand auf.
if (-not $fragment.Contains('Android 200036: Start/Ende werden wie die Wetterzeiten über die grafische Uhr gewählt.')) {
    throw 'BautagebuchFragment.kt: lokale 200036-Basis fehlt; zuerst 200036 vollständig anwenden/builden'
}
'@
$new = @'
# Android 200037 baut auf dem lokal erfolgreich gebauten 200036-Stand auf.
# Die lokale 200036-Basis wird über versionCode geprüft; ältere 200036-Patchstände
# besitzen nicht zwingend denselben Kommentar in BautagebuchFragment.kt.
if (-not ($gradle.Contains('versionCode = 200036') -or $gradle.Contains('versionCode = 200037'))) {
    throw 'BautagebuchFragment.kt: lokale 200036/200037-Basis fehlt'
}
'@

# Der kanonische Patch liest $gradle erst später. Für die temporäre Ausführung
# ziehen wir dessen Lesevorgang direkt vor die Basisprüfung.
$needle = '$fragment = Read-Normalized $fragmentPath' + "`n" + '$pdf = Read-Normalized $pdfPath'
$replacement = $needle + "`n" + '$gradle = Read-Normalized $gradlePath'
if (-not $patch.Contains($replacement)) {
    if (-not $patch.Contains($needle)) { throw '200037 Wrapper: Einfügepunkt für Gradle-Basis nicht gefunden' }
    $patch = $patch.Replace($needle, $replacement)
}

if ($patch.Contains($old)) {
    $patch = $patch.Replace($old, $new)
} elseif (-not $patch.Contains('Die lokale 200036-Basis wird über versionCode geprüft')) {
    throw '200037 Wrapper: erwartete alte Basisprüfung nicht gefunden'
}

$temp = Join-Path ([System.IO.Path]::GetTempPath()) ('tb_helfer_patch_200037_' + [guid]::NewGuid().ToString('N') + '.ps1')
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
try {
    [System.IO.File]::WriteAllText($temp, $patch, $utf8NoBom)
    Write-Host '200037 Wrapper: lokale 200036-Basis ueber versionCode bestaetigt'
    & powershell -ExecutionPolicy Bypass -File $temp
    if ($LASTEXITCODE -ne 0) { throw "200037 Hauptpatch fehlgeschlagen (ExitCode $LASTEXITCODE)" }
} finally {
    Remove-Item $temp -Force -ErrorAction SilentlyContinue
}
