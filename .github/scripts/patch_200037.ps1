$ErrorActionPreference = 'Stop'

$repoRoot = (Get-Location).Path
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$fragmentPath = Join-Path $repoRoot 'app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchFragment.kt'
$gradlePath = Join-Path $repoRoot 'app/build.gradle.kts'

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

$fragment = Read-Normalized $fragmentPath

# Android 200037 baut bewusst auf dem lokal erfolgreich gebauten 200036-Stand auf.
if (-not $fragment.Contains('Android 200036: Start/Ende werden wie die Wetterzeiten über die grafische Uhr gewählt.')) {
    throw 'BautagebuchFragment.kt: lokale 200036-Basis fehlt; zuerst 200036 vollständig anwenden/builden'
}

# Gemeinsamer Uhr-Dialog für Personal- und Maschinenzeiten.
$old = @'
        private fun suggestedNowQuarter(): String {
            return roundUpToQuarter(java.time.LocalTime.now()).format(timeFmt)
        }

        private fun showTimePopup(ac: MaterialAutoCompleteTextView, items: List<String>) {
'@
$new = @'
        private fun suggestedNowQuarter(): String {
            return roundUpToQuarter(java.time.LocalTime.now()).format(timeFmt)
        }

        /**
         * Android 200037: Personal- und Maschinenzeiten nutzen dieselbe grafische 24h-Uhr
         * wie Datum/Zeit. Die Auswahl wird weiterhin auf das 15-Minuten-Raster gerundet.
         */
        private fun showPmClockPicker(
            field: com.google.android.material.textfield.TextInputEditText,
            suggestedWhenBlank: String,
            onChosen: (() -> Unit)? = null
        ) {
            val raw = field.text?.toString().orEmpty().trim()
            val initial = runCatching { LocalTime.parse(raw, timeFmt) }.getOrNull()
                ?: runCatching { LocalTime.parse(suggestedWhenBlank, timeFmt) }.getOrNull()
                ?: LocalTime.now()
            TimePickerDialog(requireContext(), { _, hour, minute ->
                val rounded = roundUpToQuarter(LocalTime.of(hour, minute))
                field.setText(rounded.format(timeFmt))
                onChosen?.invoke()
            }, initial.hour, initial.minute, true).show()
        }

        private fun showTimePopup(ac: MaterialAutoCompleteTextView, items: List<String>) {
'@
$fragment = Replace-Once $fragment $old $new 'BautagebuchFragment PM-Uhr Helper'

# Neuer-BTB-Dialog: Personal/Geräte-Maschinen.
$old = @'
                etVon.setOnClickListener {
                    showTimePopup(
                        anchor = etVon,
                        setValue = { etVon.setText(it) },
                        currentValue = etVon.text?.toString().orEmpty(),
                        items = times,
                        suggestedWhenBlank = suggestedVon
                    )
                }
                etBis.setOnClickListener {
                    val suggestedBis = plusHours(etVon.text?.toString().orEmpty(), 10L) ?: suggestedVon
                    showTimePopup(
                        anchor = etBis,
                        setValue = { etBis.setText(it) },
                        currentValue = etBis.text?.toString().orEmpty(),
                        items = times,
                        suggestedWhenBlank = suggestedBis
                    )
                }
'@
$new = @'
                etVon.setOnClickListener {
                    showPmClockPicker(etVon, suggestedVon) {
                        autoFillBisIfBlank(etVon, etBis, 10L)
                    }
                }
                etBis.setOnClickListener {
                    autoFillBisIfBlank(etVon, etBis, 10L)
                    val suggestedBis = plusHours(etVon.text?.toString().orEmpty(), 10L) ?: suggestedVon
                    showPmClockPicker(etBis, suggestedBis)
                }
'@
$fragment = Replace-Once $fragment $old $new 'Neuer-BTB Personal/Maschinen Uhr'

# Laufender BTB: aufgeklappte Personal-/Maschinen-Sektionen.
$old = @'
            etVon.setOnClickListener {
                showTimePopup(
                    anchor = etVon,
                    setValue = { etVon.setText(it) },
                    currentValue = etVon.text?.toString().orEmpty(),
                    items = times,
                    suggestedWhenBlank = suggestedVon
                )
            }

            etBis.setOnClickListener {
                autoFillBisIfBlank(etVon, etBis, 10L)
                val suggestedBis = plusHours(etVon.text?.toString().orEmpty(), 10L) ?: suggestedVon
                showTimePopup(
                    anchor = etBis,
                    setValue = { etBis.setText(it) },
                    currentValue = etBis.text?.toString().orEmpty(),
                    items = times,
                    suggestedWhenBlank = suggestedBis
                )
            }
'@
$new = @'
            etVon.setOnClickListener {
                showPmClockPicker(etVon, suggestedVon) {
                    autoFillBisIfBlank(etVon, etBis, 10L)
                }
            }

            etBis.setOnClickListener {
                autoFillBisIfBlank(etVon, etBis, 10L)
                val suggestedBis = plusHours(etVon.text?.toString().orEmpty(), 10L) ?: suggestedVon
                showPmClockPicker(etBis, suggestedBis)
            }
'@
$fragment = Replace-Once $fragment $old $new 'BTB Personal/Maschinen Uhr'

Write-Normalized $fragmentPath $fragment

$gradle = Read-Normalized $gradlePath
if ($gradle.Contains('versionCode = 200037')) {
    Write-Host 'build.gradle.kts: 200037 bereits gesetzt'
} elseif ($gradle.Contains('versionCode = 200036')) {
    $gradle = $gradle.Replace('versionCode = 200036', 'versionCode = 200037')
    $gradle = $gradle.Replace('// Android 200036: Export-PDF/DOCX, Multi-BTB-Dateinamen, TimePicker und NightMode-Lesbarkeit.', '// Android 200037: 200036-Basis + grafische Uhr für Personal und Geräte/Maschinen.')
    Write-Normalized $gradlePath $gradle
    Write-Host 'build.gradle.kts: versionCode 200037 gesetzt'
} else {
    throw 'build.gradle.kts: erwartete lokale 200036-Basis fehlt'
}

$verifyFragment = Read-Normalized $fragmentPath
$verifyGradle = Read-Normalized $gradlePath
if (-not $verifyFragment.Contains('private fun showPmClockPicker(')) { throw '200037 Verifikation: PM-Uhr-Helper fehlt' }
if (($verifyFragment.Split('showPmClockPicker(etVon').Count - 1) -lt 2) { throw '200037 Verifikation: Von-Uhr nicht in beiden PM-Editoren gefunden' }
if (($verifyFragment.Split('showPmClockPicker(etBis').Count - 1) -lt 2) { throw '200037 Verifikation: Bis-Uhr nicht in beiden PM-Editoren gefunden' }
if (-not $verifyGradle.Contains('versionCode = 200037')) { throw '200037 Verifikation: versionCode fehlt' }

Write-Host 'PATCH_200037_FERTIG'
Write-Host 'Geändert: BautagebuchFragment.kt, build.gradle.kts'
