from pathlib import Path


def replace_exact(path: str, old: str, new: str, expected: int = 1) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != expected:
        raise SystemExit(
            f"{path}: erwartet {expected} Treffer, gefunden {count}: {old[:90]!r}"
        )
    p.write_text(text.replace(old, new, expected), encoding="utf-8")


repo = "app/src/main/java/de/tbhelfer/datenbank/TrassenRepository.kt"
replace_exact(
    repo,
    """                if (grenze1 == null || grenze2 == null) {
                    // Patch45ZW: Solange der betriebliche Abschnitt nicht mit zwei Grenzen
                    // eingegrenzt ist, gilt er vorlaeufig auf der gesamten fest gewaehlten Roh-TRA.
                    true
                } else {""",
    """                if (grenze1 == null || grenze2 == null) {
                    // Offene Entwuerfe bleiben gespeichert, duerfen aber nicht die gesamte Roh-TRA
                    // als fachlich gueltigen, manuell kilometrierten Abschnitt ueberlagern.
                    false
                } else {""",
)
replace_exact(
    repo,
    "status = TrassenKmStatusLogik.STATUS_UNPLAUSIBEL,",
    "status = TrassenKmStatusLogik.STATUS_FEHLT,",
    expected=2,
)

fragment = "app/src/main/java/de/tbhelfer/ui/importexport/ImportExportFragment.kt"
replace_exact(
    fragment,
    """    private fun abschnittKmStatus(abschnitt: TrassenManuellerAbschnittEntity): String = when {
        abschnitt.kmAnkerStation != null && abschnitt.kmAnkerMeter != null && abschnitt.kmStationVorzeichen != null -> TrassenKmStatusLogik.STATUS_MANUELL
        abschnitt.kmAnkerStation != null && abschnitt.kmAnkerMeter != null -> TrassenKmStatusLogik.STATUS_FEHLT
        else -> TrassenKmStatusLogik.STATUS_FEHLT
    }""",
    """    private fun abschnittKmStatus(abschnitt: TrassenManuellerAbschnittEntity): String =
        TrassenKmStatusLogik.bewerteManuellenAbschnitt(
            kmAnkerStation = abschnitt.kmAnkerStation,
            kmAnkerMeter = abschnitt.kmAnkerMeter,
            kmStationVorzeichen = abschnitt.kmStationVorzeichen,
            stationStart = abschnitt.stationStart,
            stationEnde = abschnitt.stationEnde
        ).status""",
)
replace_exact(
    fragment,
    'kmAnkerStation != null && kmAnkerMeter != null && kmStationVorzeichen != null -> "ORANGE · manuell kilometriert"',
    'kmAnkerStation != null && kmAnkerMeter != null && kmStationVorzeichen != null && stationStart != null && stationEnde != null -> "ORANGE · manuell kilometriert"',
)
replace_exact(
    fragment,
    'kmAnkerStation != null && kmAnkerMeter != null -> "ROT · Richtung offen"',
    'kmAnkerStation != null && kmAnkerMeter != null -> "ROT · manueller Abschnitt noch unvollstaendig"',
)
replace_exact(
    fragment,
    'aktuell.kmAnkerStation != null && aktuell.kmAnkerMeter != null && aktuell.kmStationVorzeichen != null -> "ORANGE · manuell kilometriert"',
    'aktuell.kmAnkerStation != null && aktuell.kmAnkerMeter != null && aktuell.kmStationVorzeichen != null && aktuell.stationStart != null && aktuell.stationEnde != null -> "ORANGE · manuell kilometriert"',
)
replace_exact(fragment, '"GRAU · unplausibel"', '"ROT · unplausibel"')
replace_exact(
    fragment,
    'TrassenKmStatusLogik.STATUS_MANUELL -> "ORANGE · manuell mit 2 Punkten kalibriert"',
    'TrassenKmStatusLogik.STATUS_MANUELL -> "ORANGE · manuell kilometriert"',
)
replace_exact(
    fragment,
    'TrassenKmStatusLogik.STATUS_FEHLT -> "ROT · keine KM-Kalibrierung"',
    'TrassenKmStatusLogik.STATUS_FEHLT -> "ROT · keine belastbare KM"',
)
replace_exact(
    fragment,
    'TrassenKmStatusLogik.STATUS_UNPLAUSIBEL -> "GRAU · KM unplausibel"',
    'TrassenKmStatusLogik.STATUS_UNPLAUSIBEL -> "ROT · KM unplausibel"',
)
replace_exact(
    fragment,
    'sb.appendLine("KM-Status: Grün $kmAutomatischAchsen · Orange $kmManuellAchsen · Rot $kmFehltAchsen · Grau $kmUnplausibelAchsen")',
    'sb.appendLine("KM-Status: Grün $kmAutomatischAchsen · Orange $kmManuellAchsen · Rot ${kmFehltAchsen + kmUnplausibelAchsen}")',
)
replace_exact(
    fragment,
    'sb.appendLine("• KM-Achsen: Grün $kmAutomatischAchsen · Orange $kmManuellAchsen · Rot $kmFehltAchsen · Grau $kmUnplausibelAchsen")',
    'sb.appendLine("• KM-Achsen: Grün $kmAutomatischAchsen · Orange $kmManuellAchsen · Rot ${kmFehltAchsen + kmUnplausibelAchsen}")',
)

grafik = "app/src/main/java/de/tbhelfer/ui/importexport/TrassenGrafikView.kt"
replace_exact(
    grafik,
    "* - Achsfarbe zeigt den KM-Status: GRUEN automatisch, ORANGE manuell, ROT ohne KM, GRAU unplausibel.",
    "* - Achsfarbe zeigt den KM-Status: GRUEN automatisch, ORANGE manuell, ROT ohne belastbare KM.",
)
replace_exact(
    grafik,
    '"Rot fehlt · Grau unplausibel",',
    '"Rot = keine belastbare KM · Grau = ohne Vermessungsgeometrie",',
)
replace_exact(
    grafik,
    "TrassenKmStatusLogik.STATUS_UNPLAUSIBEL -> if (dark) Color.rgb(170, 170, 170) else Color.rgb(105, 105, 105)",
    "TrassenKmStatusLogik.STATUS_UNPLAUSIBEL -> if (dark) Color.rgb(255, 82, 82) else Color.rgb(205, 35, 45)",
)
replace_exact(
    grafik,
    "else -> if (dark) Color.rgb(180, 180, 180) else Color.rgb(130, 130, 130)",
    "else -> if (dark) Color.rgb(255, 82, 82) else Color.rgb(205, 35, 45)",
)

print("Kilometrierung-v1 Integrationspatch erfolgreich angewendet.")
