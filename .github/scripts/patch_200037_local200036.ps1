$ErrorActionPreference = 'Stop'

$repoRoot = (Get-Location).Path
$patchPath = Join-Path $repoRoot '.github/scripts/patch_200037.ps1'
$gradlePath = Join-Path $repoRoot 'app/build.gradle.kts'

if (-not (Test-Path $patchPath)) { throw "Fehlt: $patchPath" }
if (-not (Test-Path $gradlePath)) { throw "Fehlt: $gradlePath" }

# Local 200036 is the verified base. Do not depend on comments in Kotlin files,
# because earlier PowerShell runs could change comment encoding without changing code.
$gradle = [System.IO.File]::ReadAllText($gradlePath).Replace("`r`n", "`n")
if (-not ($gradle.Contains('versionCode = 200036') -or $gradle.Contains('versionCode = 200037'))) {
    throw 'Local base is neither Android 200036 nor 200037.'
}

# Read the canonical patch as UTF-8 text. Windows PowerShell 5 can misread UTF-8
# scripts without BOM, so the temporary runnable copy is written WITH BOM below.
$patch = [System.IO.File]::ReadAllText($patchPath).Replace("`r`n", "`n")

# Remove only the obsolete comment-based 200036 guard from the runnable copy.
# Match uses ASCII-only anchors so this wrapper is safe under Windows PowerShell 5.
$pattern = '(?ms)^# Android 200037[^\n]*\nif \(-not \$fragment\.Contains\([^\n]*\)\) \{\n\s*throw [^\n]*\n\}\n'
$rx = [regex]::new($pattern)
$matches = $rx.Matches($patch)
if ($matches.Count -eq 1) {
    $patch = $rx.Replace(
        $patch,
        "# Android 200037: local 200036/200037 base already verified by wrapper.`n",
        1
    )
} elseif ($matches.Count -eq 0 -and $patch.Contains('local 200036/200037 base already verified by wrapper')) {
    Write-Host '200037 wrapper: basis guard already bypassed'
} else {
    throw "200037 wrapper: expected basis guard count 1, found $($matches.Count)"
}

$temp = Join-Path ([System.IO.Path]::GetTempPath()) ('tb_helfer_patch_200037_' + [guid]::NewGuid().ToString('N') + '.ps1')
$utf8WithBom = New-Object System.Text.UTF8Encoding($true)
try {
    [System.IO.File]::WriteAllText($temp, $patch, $utf8WithBom)
    Write-Host '200037 wrapper: local 200036/200037 base confirmed'
    & powershell -ExecutionPolicy Bypass -File $temp
    if ($LASTEXITCODE -ne 0) { throw "200037 main patch failed (ExitCode $LASTEXITCODE)" }
} finally {
    Remove-Item $temp -Force -ErrorAction SilentlyContinue
}
