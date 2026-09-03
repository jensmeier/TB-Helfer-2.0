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
'''        val betriebsstelle = EditText(context).apply {
            hint = "Betriebsstelle, z. B. MP · München-Pasing"
            setSingleLine(true)
            setText(vorhanden?.betriebsstelle ?: prefillBetriebsstelle)
        }
''',
'''        val betriebsstellenVorschlaege = ladeBetriebsstellenVorschlaegeAusAssets()
        val betriebsstelleStart = vorhanden?.betriebsstelle
            ?: prefillBetriebsstelle.takeIf { it.isNotBlank() }
            ?: BahnhofSucheUtil.ladeLetzteEingabe(context)
        val betriebsstelle = AutoCompleteTextView(context).apply {
            hint = "Betriebsstelle, z. B. MPE oder Pasing"
            setSingleLine(true)
            threshold = 1
            setText(betriebsstelleStart, false)
        }
        bindeBetriebsstellenVorschlaege(betriebsstelle, betriebsstellenVorschlaege)
''',
    "Betriebsstellenfeld im Streckenobjekt-Editor"
)

replace_once(
'''        input.setOnItemClickListener { parent, _, position, _ ->
            val display = parent.getItemAtPosition(position)?.toString().orEmpty()
            input.tag = vorschlaege.firstOrNull { it.display == display }
        }
''',
'''        input.setOnItemClickListener { parent, _, position, _ ->
            val display = parent.getItemAtPosition(position)?.toString().orEmpty()
            val treffer = vorschlaege.firstOrNull { it.display == display }
            input.tag = treffer
            treffer?.let { BahnhofSucheUtil.speichereLetzteEingabe(requireContext(), it.display) }
        }
''',
    "Betriebsstelle bei Auswahl merken"
)

replace_once(
'''                if (bs.isBlank() || name.isBlank()) {
                    Toast.makeText(context, "Betriebsstelle und Bezeichnung sind Pflicht.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val basis = StreckenObjektSpeicher.Objekt(
''',
'''                if (bs.isBlank() || name.isBlank()) {
                    Toast.makeText(context, "Betriebsstelle und Bezeichnung sind Pflicht.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                BahnhofSucheUtil.speichereLetzteEingabe(context, bs)
                val basis = StreckenObjektSpeicher.Objekt(
''',
    "Betriebsstelle beim Speichern merken"
)

replace_once(
'''            if (fachlichImportiert > 0) {
                Toast.makeText(requireContext(), "ZIP importiert. Daten werden jetzt automatisch berechnet.", Toast.LENGTH_LONG).show()
''',
'''            if (fachlichImportiert > 0) {
                // Nach einem neuen Import alle Punktarten wieder standardmaessig zeigen.
                // So erscheinen neu importierte GVP/Festpunkte sofort in der grafischen Uebersicht,
                // auch wenn vorher eine Anzeigeauswahl aktiv war.
                letzteGrafikSichtbarePunktArten = null
                Toast.makeText(requireContext(), "ZIP importiert. Daten werden jetzt automatisch berechnet.", Toast.LENGTH_LONG).show()
''',
    "GVP nach ZIP-Import wieder sichtbar machen"
)

path.write_text(text, encoding="utf-8")
