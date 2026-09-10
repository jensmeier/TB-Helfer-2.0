$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Read-Lf([string]$path) {
    return [IO.File]::ReadAllText($path).Replace("`r`n", "`n")
}

function Write-Lf([string]$path, [string]$text) {
    [IO.File]::WriteAllText($path, $text.Replace("`r`n", "`n"), $utf8)
}

function Replace-OnceText([string]$text, [string]$old, [string]$new, [string]$label) {
    $old = $old.Replace("`r`n", "`n")
    $new = $new.Replace("`r`n", "`n")
    $first = $text.IndexOf($old, [StringComparison]::Ordinal)
    if ($first -lt 0) { throw "${label}: Quelltext nicht gefunden." }
    $second = $text.IndexOf($old, $first + $old.Length, [StringComparison]::Ordinal)
    if ($second -ge 0) { throw "${label}: Quelltext ist nicht eindeutig." }
    return $text.Substring(0, $first) + $new + $text.Substring($first + $old.Length)
}

$pdfPath = Join-Path $repo 'app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchPdf.kt'
$pdf = Read-Lf $pdfPath

# 1) Dieselbe fachliche Dokumentidentitaet wie Verlauf und ZIP-Export verwenden.
$old = '        val safeBaustellenName = safeFolderName(entry.baustelleName)'
$new = @'
        val safeBaustellenName = safeFolderName(entry.baustelleName)
        val dokumentIdentitaet = BtbDokumentIdentitaet.aufloesen(context, entry)
        val pdfNameKanonisch = dokumentIdentitaet.pdfDateiname()
'@.TrimEnd("`r", "`n")
$pdf = Replace-OnceText $pdf $old $new 'PDF-Identitaet'

$old = '        val pdfNameOriginal = "${safeBaustellenName}_${skey}.pdf"'
$new = '        val pdfNameOriginalLegacy = "${safeBaustellenName}_${skey}.pdf"'
$pdf = Replace-OnceText $pdf $old $new 'Legacy-PDF-Name'

# 2) Beim optionalen Einbetten vorhandener PDFs weder die kanonische noch die alte eigene Datei erneut aufnehmen.
$old = '                ?.filter { it.name != pdfNameOriginal && it.name != pdfNameEntwurf && !excludeGeaendertRegex.matches(it.name) }'
$new = @'
                ?.filter {
                    it.name != pdfNameKanonisch &&
                        it.name != pdfNameOriginalLegacy &&
                        it.name != pdfNameEntwurf &&
                        !excludeGeaendertRegex.matches(it.name)
                }
'@.TrimEnd("`r", "`n")
$pdf = Replace-OnceText $pdf $old $new 'PDF-Selbstausschluss'

# 3) Sichtbaren Hauptkopf erweitern. Einzelzeilen statt empfindlichem Mehrzeilenblock.
$old = '        val datumAnzeige = dateRangeText(datum, entry.startUhrzeit, entry.endeUhrzeit)'
$new = @'
        val datumAnzeige = dateRangeText(datum, entry.startUhrzeit, entry.endeUhrzeit)
        val firmaAnzeige = dokumentIdentitaet.firmenname ?: "-"
'@.TrimEnd("`r", "`n")
$pdf = Replace-OnceText $pdf $old $new 'PDF-Firma'

$old = '        w.textWrapped(entry.baustelleName, pBaustelleHeader)'
$new = '        w.textWrapped("Baustelle: ${entry.baustelleName}", pBaustelleHeader)'
$pdf = Replace-OnceText $pdf $old $new 'PDF-Baustelle'

$old = '        w.textWrapped("Datum: $datumAnzeige", pT)'
$new = @'
        w.textWrapped("Firma: $firmaAnzeige", pT)
        w.textWrapped("Verantwortlicher TB: ${dokumentIdentitaet.verantwortlicherTb}", pT)
        w.textWrapped("Datum: $datumAnzeige", pT)
        w.textWrapped("Schicht: ${dokumentIdentitaet.schichtAnzeige}", pT)
'@.TrimEnd("`r", "`n")
$pdf = Replace-OnceText $pdf $old $new 'PDF-Kopfdaten'

# 4) Endgueltige lokale PDF-Datei auf stabilen Team/Datum/Schicht-Namen umstellen.
$old = '            File(outDir, pdfDateiname(entry.baustelleName, skey, geaendert, geaendertIndex))'
$new = '            File(outDir, dokumentIdentitaet.pdfDateiname())'
$pdf = Replace-OnceText $pdf $old $new 'PDF-Zielname'

Write-Lf $pdfPath $pdf

# Nachpruefung: alle fachlich erforderlichen Tokens muessen jetzt im Quelltext stehen.
$finalPdf = Read-Lf $pdfPath
$required = @(
    'BtbDokumentIdentitaet.aufloesen(context, entry)',
    'dokumentIdentitaet.pdfDateiname()',
    'Firma: $firmaAnzeige',
    'Baustelle: ${entry.baustelleName}',
    'Verantwortlicher TB: ${dokumentIdentitaet.verantwortlicherTb}',
    'Schicht: ${dokumentIdentitaet.schichtAnzeige}'
)
foreach ($token in $required) {
    if ($finalPdf.IndexOf($token, [StringComparison]::Ordinal) -lt 0) {
        throw "Nachpruefung fehlgeschlagen: $token"
    }
}

# Einmaliger lokaler Helfer: nach erfolgreicher Anwendung entfernen.
Remove-Item -LiteralPath $PSCommandPath -Force
Write-Host 'BTB_PDF_PATCH_OK'
