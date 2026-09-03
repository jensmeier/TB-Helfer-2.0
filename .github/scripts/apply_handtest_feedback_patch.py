from pathlib import Path

path = Path("app/src/main/java/de/tbhelfer/ui/importexport/ImportExportFragment.kt")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: erwartet genau 1 Treffer, gefunden {count}")
    text = text.replace(old, new, 1)


replace_once(
    '            else -> "○ Bereich noch nicht begrenzt · gilt vorläufig auf der gesamten gewählten TRA"\n',
    '            else -> "○ Bereich noch nicht begrenzt · bleibt ROT, bis beide Grenzen gesetzt sind"\n',
    "offener Bereich Status",
)

replace_once(
    '        appendLine("KM-Richtung: ${when (entwurf.kmStationVorzeichen) { 1 -> "steigt mit TRA-Station"; -1 -> "fällt mit TRA-Station"; else -> "noch offen" }}")\n'
    '        append("Kontrollanker: ${kmPunkte.count { it.typ == TrassenAbschnittKmPunktEntity.TYP_KONTROLLANKER }} · KM-Sprünge: ${kmPunkte.count { it.typ == TrassenAbschnittKmPunktEntity.TYP_KM_SPRUNG }}")\n',
    '        appendLine("KM-Richtung: ${when (entwurf.kmStationVorzeichen) { 1 -> "steigt mit TRA-Station"; -1 -> "fällt mit TRA-Station"; else -> "noch offen" }}")\n'
    '        val kontrollanker = kmPunkte.count { it.typ == TrassenAbschnittKmPunktEntity.TYP_KONTROLLANKER }\n'
    '        val hauptanker = if (entwurf.kmAnkerStation != null && entwurf.kmAnkerMeter != null) 1 else 0\n'
    '        append("KM-Anker gesamt: ${hauptanker + kontrollanker} · Hauptanker: $hauptanker · Kontrollanker: $kontrollanker · KM-Sprünge: ${kmPunkte.count { it.typ == TrassenAbschnittKmPunktEntity.TYP_KM_SPRUNG }}")\n',
    "Ankerzahl Bearbeitungsmenue",
)

replace_once(
    '            hinweis = "Ohne zwei Grenzen gilt der Abschnitt vorläufig auf der gesamten gewählten TRA. Erst Grenze 1 + Grenze 2 schneiden den betrieblichen Bereich aus. Die technische TRA bleibt unverändert.",\n',
    '            hinweis = "Ohne zwei Grenzen bleibt der Entwurf offen und ROT. Er wird nicht als gültiger KM-Abschnitt auf die gesamte Roh-TRA gelegt. Erst Grenze 1 + Grenze 2 legen den gültigen Bereich fest.",\n',
    "Grenzen Hinweis",
)

replace_once(
    '                "KM-Kalibrierung und betriebliche Grenzen sind getrennt. Ohne zwei Grenzen gilt der erste Abschnitt zunächst auf der gesamten gewählten TRA."\n',
    '                "KM-Kalibrierung und betriebliche Grenzen sind getrennt. Ohne zwei Grenzen bleibt der Abschnitt offen und ROT; er wird nicht auf die gesamte Roh-TRA angewendet."\n',
    "Neuer Abschnitt Hinweis",
)

replace_once(
    '                appendLine("Kontrollanker: ${punkte.count { it.typ == TrassenAbschnittKmPunktEntity.TYP_KONTROLLANKER }}")\n'
    '                appendLine("KM-Sprünge: ${punkte.count { it.typ == TrassenAbschnittKmPunktEntity.TYP_KM_SPRUNG }}")\n',
    '                val kontrollanker = punkte.count { it.typ == TrassenAbschnittKmPunktEntity.TYP_KONTROLLANKER }\n'
    '                val hauptanker = if (aktuell.kmAnkerStation != null && aktuell.kmAnkerMeter != null) 1 else 0\n'
    '                appendLine("KM-Anker gesamt: ${hauptanker + kontrollanker} · Hauptanker: $hauptanker · Kontrollanker: $kontrollanker")\n'
    '                appendLine("KM-Sprünge: ${punkte.count { it.typ == TrassenAbschnittKmPunktEntity.TYP_KM_SPRUNG }}")\n',
    "Ankerzahl Zusammenfassung",
)

replace_once(
    '                minKm != null && maxKm != null && kotlin.math.abs(maxKm - minKm) >= 0.5 ->\n'
    '                    "${formatKmOderStationMitLabel(minKm)} bis ${formatKmOderStationMitLabel(maxKm)}"\n'
    '                minKm != null -> formatKmOderStationMitLabel(minKm)\n',
    '                minKm != null && maxKm != null && kotlin.math.abs(maxKm - minKm) >= 0.5 ->\n'
    '                    "km ${formatKmMeter(minKm)} bis km ${formatKmMeter(maxKm)}"\n'
    '                minKm != null -> "km ${formatKmMeter(minKm)}"\n',
    "Grafik KM Text",
)

replace_once(
    '                appendLine("Manuelle KM-Anker: ${manuelleAnker.size}/2")\n',
    '                appendLine("Manuelle KM-Anker: ${manuelleAnker.size}")\n',
    "Grafik Ankerzahl",
)

replace_once(
    '            text = "GPS und km sind optional. Eine TRA kann eine Geometrie besitzen, ohne kilometriert zu sein."\n',
    '            text = "WA/WE/WEZ können per Vermarkungspunkt oder GPS gesetzt werden. km bleibt optional; vorhandener Datei-km wird übernommen."\n',
    "Weichen Editor Hinweis",
)

replace_once(
    '            p.km != null -> "km ${formatKmEingabe(p.km)} eingetragen · GPS noch offen"\n',
    '            p.achseId != null && p.station != null -> buildString {\n'
    '                append("Vermarkung ✓")\n'
    '                p.achseName?.let { append(" · TRA $it") }\n'
    '                append(" · Station ${format1(p.station)} m")\n'
    '                p.achsabstandM?.let { append(" · ${format1(it)} m") }\n'
    '                if (p.km != null) append("\\nkm ${formatKmEingabe(p.km)}${if (p.kmManuell) " manuell" else " aus Datei"}")\n'
    '            }\n'
    '            p.km != null -> "km ${formatKmEingabe(p.km)} eingetragen · GPS/Vermarkung noch offen"\n',
    "Weichen Status Vermarkung",
)

marker = '        rollen.forEach { rolle ->\n'
if text.count(marker) != 1:
    raise SystemExit(f"Vermarkung helper marker: erwartet 1 Treffer, gefunden {text.count(marker)}")
helper = '''        fun vermarkungWaehlen(rolle: String) {
            viewLifecycleOwner.lifecycleScope.launch {
                val (festpunkte, achsen) = withContext(Dispatchers.IO) {
                    ladeGrafikVermessungspunkte() to ladeRohGrafikAchsen()
                }
                val rollenRegex = Regex("(?i)^${Regex.escape(rolle)}(?:[_\\\\s-]|\\\\d|$)")
                val direktPassend = festpunkte.filter { punkt ->
                    rollenRegex.containsMatchIn(punkt.label.trim()) ||
                        rollenRegex.containsMatchIn(punkt.punktId.orEmpty().trim())
                }
                val kandidaten = direktPassend.ifEmpty {
                    festpunkte.filter { it.art.equals("WEICHENPUNKT", ignoreCase = true) }
                }.sortedWith(compareBy<TrassenGrafikFestpunkt> { it.label }.thenBy { it.punktId.orEmpty() })

                if (kandidaten.isEmpty()) {
                    Toast.makeText(context, "Keine passenden WA/WE/WEZ-Vermarkungspunkte vorhanden.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                fun uebernehmen(punkt: TrassenGrafikFestpunkt) {
                    val beste = achsen.mapNotNull { achse ->
                        projiziereVermessungspunktAufAchse(punkt, achse)?.let { projektion -> achse to projektion }
                    }.minByOrNull { it.second.abstandM }
                    val achse = beste?.first
                    val projektion = beste?.second
                    if (projektion == null || achse == null) {
                        Toast.makeText(context, "Vermarkungspunkt konnte keiner TRA sicher zugeordnet werden.", Toast.LENGTH_LONG).show()
                        return
                    }
                    val objektPunkt = StreckenObjektSpeicher.Punkt(
                        rolle = rolle,
                        km = punkt.kmMeter,
                        kmManuell = false,
                        achseId = achse.achseId,
                        achseName = achse.label.takeIf { it.isNotBlank() } ?: achse.name,
                        station = projektion.station,
                        achsabstandM = projektion.abstandM
                    )
                    val kmText = punkt.kmMeter?.let { "km ${formatKmMeter(it)} aus Datei" } ?: "kein Datei-km"
                    MaterialAlertDialogBuilder(context)
                        .setTitle("$rolle aus Vermarkung übernehmen?")
                        .setMessage("${punkt.label.ifBlank { punkt.punktId ?: rolle }}\\n$kmText\\nTRA: ${objektPunkt.achseName} · Station ${format1(projektion.station)} m · Abstand ${format1(projektion.abstandM)} m")
                        .setNegativeButton("Abbrechen", null)
                        .setPositiveButton("Übernehmen") { _, _ -> punktUebernehmen(rolle, objektPunkt) }
                        .show()
                }

                MaterialAlertDialogBuilder(context)
                    .setTitle("$rolle · Vermarkungspunkt wählen")
                    .setItems(kandidaten.map { it.toGrafikPunktAuswahlText() }.toTypedArray()) { _, which ->
                        uebernehmen(kandidaten[which])
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            }
        }

'''
text = text.replace(marker, helper + marker, 1)

replace_once(
    '''            val gps = MaterialButton(context).apply {
                text = "GPS setzen"
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val km = EditText(context).apply {
''',
    '''            val gps = MaterialButton(context).apply {
                text = "GPS setzen"
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val vermarkung = if (typ == StreckenObjektSpeicher.Typ.WEICHE) MaterialButton(context).apply {
                text = "Vermarkung wählen"
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(4)
                }
            } else null
            val km = EditText(context).apply {
''',
    "Vermarkung Button Definition",
)

replace_once(
    '''            box.addView(zeile)
            box.addView(status)
''',
    '''            box.addView(zeile)
            vermarkung?.let { box.addView(it) }
            box.addView(status)
''',
    "Vermarkung Button Layout",
)

replace_once(
    '''            gps.setOnClickListener {
                mitStandortBerechtigung {
                    sammleAbschnittGpsMessung("$rolle per GPS setzen") { messung ->
                        val nahe = StreckenObjektSpeicher.findeNahePunkte(context, messung.latitude, messung.longitude, 5.0, vorhanden?.id).firstOrNull()
                        if (nahe != null) {
                            MaterialAlertDialogBuilder(context)
                                .setTitle("Vorhandenen Punkt übernehmen?")
                                .setMessage("${nahe.punkt.rolle} von ${nahe.objekt.bezeichnung} liegt ${format1(nahe.abstandM)} m entfernt und kann als $rolle verwendet werden.")
                                .setNegativeButton("Neuen Punkt") { _, _ -> gpsMessungUebernehmen(rolle, messung) }
                                .setPositiveButton("Übernehmen") { _, _ -> punktUebernehmen(rolle, nahe.punkt.copy(rolle = rolle)) }
                                .show()
                        } else {
                            gpsMessungUebernehmen(rolle, messung)
                        }
                    }
                }
            }
            aktualisiereZeile(rolle)
''',
    '''            gps.setOnClickListener {
                mitStandortBerechtigung {
                    sammleAbschnittGpsMessung("$rolle per GPS setzen") { messung ->
                        val nahe = StreckenObjektSpeicher.findeNahePunkte(context, messung.latitude, messung.longitude, 5.0, vorhanden?.id).firstOrNull()
                        if (nahe != null) {
                            MaterialAlertDialogBuilder(context)
                                .setTitle("Vorhandenen Punkt übernehmen?")
                                .setMessage("${nahe.punkt.rolle} von ${nahe.objekt.bezeichnung} liegt ${format1(nahe.abstandM)} m entfernt und kann als $rolle verwendet werden.")
                                .setNegativeButton("Neuen Punkt") { _, _ -> gpsMessungUebernehmen(rolle, messung) }
                                .setPositiveButton("Übernehmen") { _, _ -> punktUebernehmen(rolle, nahe.punkt.copy(rolle = rolle)) }
                                .show()
                        } else {
                            gpsMessungUebernehmen(rolle, messung)
                        }
                    }
                }
            }
            vermarkung?.setOnClickListener { vermarkungWaehlen(rolle) }
            aktualisiereZeile(rolle)
''',
    "Vermarkung Button Aktion",
)

replace_once(
    '''                        punkte[rolle]?.takeIf { punkt ->
                            (punkt.lat != null && punkt.lon != null) || punkt.km != null
                        }
''',
    '''                        punkte[rolle]?.takeIf { punkt ->
                            (punkt.lat != null && punkt.lon != null) ||
                                punkt.km != null ||
                                (punkt.achseId != null && punkt.station != null)
                        }
''',
    "Vermarkungspunkt speichern",
)

path.write_text(text, encoding="utf-8")
print("Handytest-Feedback-Patch erfolgreich angewendet.")
