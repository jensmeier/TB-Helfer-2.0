$ErrorActionPreference = 'Stop'

$repoRoot = (Get-Location).Path
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$fragmentPath = Join-Path $repoRoot 'app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchFragment.kt'
$pdfPath = Join-Path $repoRoot 'app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchPdf.kt'
$gradlePath = Join-Path $repoRoot 'app/build.gradle.kts'

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

function Replace-Exact([string]$text, [string]$old, [string]$new, [int]$expected, [string]$label) {
    $newCount = Count-Literal $text $new
    if ($newCount -eq $expected) {
        Write-Host "${label}: already applied"
        return $text
    }
    $oldCount = Count-Literal $text $old
    if ($oldCount -ne $expected) { throw "${label}: expected $expected matches, found $oldCount" }
    return $text.Replace($old, $new)
}

$fragment = Read-Normalized $fragmentPath
$pdf = Read-Normalized $pdfPath
$gradle = Read-Normalized $gradlePath

if (-not ($gradle.Contains('versionCode = 200036') -or $gradle.Contains('versionCode = 200037'))) {
    throw 'Android 200037 requires local versionCode 200036 or 200037.'
}
if (-not $pdf.Contains('forceRed: Boolean = false') -or -not $pdf.Contains('val isNew: Boolean')) {
    throw 'BautagebuchPdf.kt is missing the local 200035/200036 photo-diff base.'
}

Write-Host '200037 v2: local 200036/200037 base confirmed'

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

        // Android 200037: Personal and machine times use the same 24h clock picker.
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
$fragment = Replace-Once $fragment $old $new 'PM clock helper'

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
$fragment = Replace-Once $fragment $old $new 'New-BTB PM clock'

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
$fragment = Replace-Once $fragment $old $new 'Running-BTB PM clock'

$old = @'
        val grouped = rows.groupBy { Pair(Triple(it.firma, it.ortId, it.text), it.isNew) }
grouped.entries.forEach { (k, list) ->
    val (baseKey, isNewGroup) = k
    val (firma, ortId, txt) = baseKey
'@
$new = @'
        // Same photo text in the same company/location context stays one PDF row.
        val grouped = rows.groupBy { Triple(it.firma, it.ortId, it.text) }
grouped.entries.forEach { (k, list) ->
    val (firma, ortId, txt) = k
'@
$pdf = Replace-Exact $pdf $old $new 2 'PDF same-name photo grouping'

$old = @'
    val sorted = list.sortedBy { it.epochMs }
    if (sorted.isEmpty()) return@forEach
    val first = sorted.first()
'@
$new = @'
    val sorted = list.sortedBy { it.epochMs }
    if (sorted.isEmpty()) return@forEach
    val forceRedGroup = sorted.any { it.isNew }
    val first = sorted.first()
'@
$pdf = Replace-Exact $pdf $old $new 2 'PDF grouped photo red-state'

$pdf = Replace-Exact $pdf '            forceRed = isNewGroup' '            forceRed = forceRedGroup' 2 'PDF grouped forceRed'

if ($gradle.Contains('versionCode = 200036')) {
    $gradle = $gradle.Replace('versionCode = 200036', 'versionCode = 200037')
}
$gradle = $gradle.Replace('// Android 200035: Fotozeilen im Original-Diff + rote entfernte BTB-Inhalte.', '// Android 200037: PM clock pickers + PDF grouping for same-name photos.')
$gradle = $gradle.Replace('// Android 200036: Export-PDF/DOCX, Multi-BTB-Dateinamen, TimePicker und NightMode-Lesbarkeit.', '// Android 200037: PM clock pickers + PDF grouping for same-name photos.')

Write-Normalized $fragmentPath $fragment
Write-Normalized $pdfPath $pdf
Write-Normalized $gradlePath $gradle

$verifyFragment = Read-Normalized $fragmentPath
$verifyPdf = Read-Normalized $pdfPath
$verifyGradle = Read-Normalized $gradlePath

if (-not $verifyFragment.Contains('private fun showPmClockPicker(')) { throw '200037 verify: PM clock helper missing' }
if ((Count-Literal $verifyFragment 'showPmClockPicker(etVon') -lt 2) { throw '200037 verify: etVon clock missing in one PM editor' }
if ((Count-Literal $verifyFragment 'showPmClockPicker(etBis') -lt 2) { throw '200037 verify: etBis clock missing in one PM editor' }
if ((Count-Literal $verifyPdf 'val grouped = rows.groupBy { Triple(it.firma, it.ortId, it.text) }') -lt 2) { throw '200037 verify: PDF photo grouping missing' }
if ((Count-Literal $verifyPdf 'val forceRedGroup = sorted.any { it.isNew }') -lt 2) { throw '200037 verify: grouped photo red-state missing' }
if (-not $verifyGradle.Contains('versionCode = 200037')) { throw '200037 verify: versionCode missing' }

Write-Host 'PATCH_200037_V2_FERTIG'
Write-Host 'Changed: BautagebuchFragment.kt, BautagebuchPdf.kt, build.gradle.kts'
