$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Read-Lf([string]$path) {
    return [IO.File]::ReadAllText($path).Replace("`r`n", "`n")
}

function Write-Lf([string]$path, [string]$text) {
    [IO.File]::WriteAllText($path, $text.Replace("`r`n", "`n"), $utf8)
}

function Replace-Once([string]$text, [string]$old, [string]$new, [string]$label) {
    $old = $old.Replace("`r`n", "`n")
    $new = $new.Replace("`r`n", "`n")
    $first = $text.IndexOf($old, [StringComparison]::Ordinal)
    if ($first -lt 0) { throw "${label}: Quellblock nicht gefunden." }
    $second = $text.IndexOf($old, $first + $old.Length, [StringComparison]::Ordinal)
    if ($second -ge 0) { throw "${label}: Quellblock ist nicht eindeutig." }
    return $text.Substring(0, $first) + $new + $text.Substring($first + $old.Length)
}

$pdfPath = Join-Path $repo 'app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchPdf.kt'
$checkPath = Join-Path $repo 'server/checks/03_verify_schema10_source.py'
$statusPath = Join-Path $repo 'docs/TB-HELFER_AKTUELLER_STAND.md'

$pdf = Read-Lf $pdfPath

$old = @'
    suspend fun erstellenPdf(entry: BautagebuchEintrag, asDraft: Boolean = false): File {
        val datum = LocalDate.parse(entry.datumIso, isoFmt)
        val skey = schichtKey(entry)
        val safeBaustellenName = safeFolderName(entry.baustelleName)
        val pdfNameOriginal = "${safeBaustellenName}_${skey}.pdf"
        val pdfNameEntwurf = "${safeBaustellenName}_entwurf_${skey}.pdf"
'@
$new = @'
    suspend fun erstellenPdf(entry: BautagebuchEintrag, asDraft: Boolean = false): File {
        val datum = LocalDate.parse(entry.datumIso, isoFmt)
        val skey = schichtKey(entry)
        val safeBaustellenName = safeFolderName(entry.baustelleName)
        val dokumentIdentitaet = BtbDokumentIdentitaet.aufloesen(context, entry)
        val pdfNameKanonisch = dokumentIdentitaet.pdfDateiname()
        val pdfNameOriginalLegacy = "${safeBaustellenName}_${skey}.pdf"
        val pdfNameEntwurf = "${safeBaustellenName}_entwurf_${skey}.pdf"
'@
$pdf = Replace-Once $pdf $old $new 'PDF-Identitaet'

$old = @'
                ?.filter { it.name != pdfNameOriginal && it.name != pdfNameEntwurf && !excludeGeaendertRegex.matches(it.name) }
'@
$new = @'
                ?.filter {
                    it.name != pdfNameKanonisch &&
                        it.name != pdfNameOriginalLegacy &&
                        it.name != pdfNameEntwurf &&
                        !excludeGeaendertRegex.matches(it.name)
                }
'@
$pdf = Replace-Once $pdf $old $new 'PDF-Selbstausschluss'

$old = @'
        val datumAnzeige = dateRangeText(datum, entry.startUhrzeit, entry.endeUhrzeit)
        w.textWrapped(entry.baustelleName, pBaustelleHeader)
        w.textWrapped("Datum: $datumAnzeige", pT)
        w.textWrapped("Zeit: ${entry.startUhrzeit ?: "-"} – ${entry.endeUhrzeit ?: "-"}", pT)
'@
$new = @'
        val datumAnzeige = dateRangeText(datum, entry.startUhrzeit, entry.endeUhrzeit)
        val firmaAnzeige = dokumentIdentitaet.firmenname ?: "-"
        w.textWrapped("Firma: $firmaAnzeige", pT)
        w.textWrapped("Baustelle: ${entry.baustelleName}", pBaustelleHeader)
        w.textWrapped("Verantwortlicher TB: ${dokumentIdentitaet.verantwortlicherTb}", pT)
        w.textWrapped("Datum: $datumAnzeige", pT)
        w.textWrapped("Schicht: ${dokumentIdentitaet.schichtAnzeige}", pT)
        w.textWrapped("Zeit: ${entry.startUhrzeit ?: "-"} – ${entry.endeUhrzeit ?: "-"}", pT)
'@
$pdf = Replace-Once $pdf $old $new 'PDF-Hauptkopf'

$old = @'
        // --- Baseline + "geaendert"-Flag ---
        // Hinweis: Die Baseline (bautagebuch_original.json) wird NICHT automatisch beim PDF-Erstellen gesetzt,
        // sondern bewusst per "Tagesabschluss + PDF" im UI.
        val geaendert = baselineInfo.exists && baselineInfo.changedSinceBaseline

        // --- Ziel: App-Pictures/TB-Helfer/<Baustelle>/BTB/<datumIso>/ ---
        val outDir = BtbSpeicher.btbsFuerBaustelle(context, entry.baustelleId, entry.datumIso)
        val geaendertIndex = if (geaendert) nextGeaendertIndex(outDir, safeBaustellenName, skey) else null
        val file = if (asDraft) {
            File(outDir, safeBaustellenName + "_entwurf_" + skey + ".pdf")
        } else {
            File(outDir, pdfDateiname(entry.baustelleName, skey, geaendert, geaendertIndex))
        }
'@
$new = @'
        // Die Baseline bleibt für die roten Änderungsmarkierungen maßgeblich.
        // Der endgültige Dateipfad selbst ist dagegen stabil: derselbe fachliche Team-BTB
        // aktualisiert bei erneutem PDF-Erstellen dieselbe Datei statt geaendert2/geaendert3 anzulegen.

        // --- Ziel: App-Pictures/TB-Helfer/<Baustelle>/BTB/<datumIso>/ ---
        val outDir = BtbSpeicher.btbsFuerBaustelle(context, entry.baustelleId, entry.datumIso)
        val file = if (asDraft) {
            File(outDir, safeBaustellenName + "_entwurf_" + skey + ".pdf")
        } else {
            File(outDir, dokumentIdentitaet.pdfDateiname())
        }
'@
$pdf = Replace-Once $pdf $old $new 'PDF-Zielname'
Write-Lf $pdfPath $pdf

$check = Read-Lf $checkPath
$anchor = @'
    require_text(
        ANDROID / "ui" / "baustelle" / "BtbVerlaufFragment.kt",
'@
$addition = @'
    require_text(
        ANDROID / "ui" / "bautagebuch" / "BautagebuchPdf.kt",
        [
            "BtbDokumentIdentitaet.aufloesen(context, entry)",
            "dokumentIdentitaet.pdfDateiname()",
            "Firma: $firmaAnzeige",
            "Verantwortlicher TB: ${dokumentIdentitaet.verantwortlicherTb}",
            "Schicht: ${dokumentIdentitaet.schichtAnzeige}",
        ],
    )
'@
if ($check.IndexOf('BtbDokumentIdentitaet.aufloesen(context, entry)', [StringComparison]::Ordinal) -lt 0) {
    $check = Replace-Once $check $anchor ($addition + $anchor) 'Source-Contract'
}
Write-Lf $checkPath $check

$status = Read-Lf $statusPath
$old = @'
Noch offen in diesem BTB-Block:
- die **direkt lokal erzeugte/angezeigte PDF-Datei selbst** verwendet intern in `BautagebuchPdf.kt` noch die ältere lokale Dateinamenslogik; der merge-sichere ZIP-/Laptop-Export ist bereits team-/schichtbezogen.
- der sichtbare PDF-Hauptkopf in `BautagebuchPdf.kt` muss noch direkt um Firma/verantwortlichen TB/Schicht erweitert werden; DOCX-Archivkopf ist bereits normalisiert.
- historische bestehende PDFs werden nicht zwangsumbenannt.
'@
$new = @'
Direkter PDF-Abschluss ist im Feature-Branch jetzt ebenfalls vereinheitlicht:
- `BautagebuchPdf.kt` löst dieselbe `BtbDokumentIdentitaet` wie Verlauf/Export auf.
- abgeschlossene lokale PDFs verwenden den kanonischen Namen `BTB_<Datum>_<TAG|NACHT>_<VerantwortlicherTB>.pdf`; erneutes Erzeugen desselben fachlichen BTB aktualisiert denselben Zielpfad statt `geaendert2/geaendert3`-Dateien zu erzeugen.
- der sichtbare PDF-Hauptkopf enthält Firma, Baustelle, verantwortlichen TB, Datum, Schicht und Zeit; das zentrale lokale Firmenlogo bleibt nutzbar.
- Entwurf-PDFs bleiben lokale Arbeitsdateien mit der bisherigen Entwurf-Namenslogik.
- historische bestehende PDFs werden nicht zwangsumbenannt.
- der neue direkte PDF-Pfad ist noch lokal in Android Studio zu bauen und auf echtem Gerät zu prüfen.
'@
$status = Replace-Once $status $old $new 'Projektstatus'
Write-Lf $statusPath $status

$required = @(
    'BtbDokumentIdentitaet.aufloesen(context, entry)',
    'dokumentIdentitaet.pdfDateiname()',
    'Firma: $firmaAnzeige',
    'Verantwortlicher TB: ${dokumentIdentitaet.verantwortlicherTb}',
    'Schicht: ${dokumentIdentitaet.schichtAnzeige}'
)
$finalPdf = Read-Lf $pdfPath
foreach ($token in $required) {
    if ($finalPdf.IndexOf($token, [StringComparison]::Ordinal) -lt 0) { throw "Nachprüfung fehlgeschlagen: $token" }
}

# Dieser Helfer ist absichtlich einmalig. Nach erfolgreicher Anwendung soll er nicht
# als dauerhaftes Projektwerkzeug im späteren Commit verbleiben.
Remove-Item -LiteralPath $PSCommandPath -Force
Write-Host 'BTB_PDF_PATCH_OK'
