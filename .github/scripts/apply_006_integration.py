from pathlib import Path

path = Path("app/src/main/java/de/tbhelfer/ui/importexport/ImportExportFragment.kt")
text = path.read_text(encoding="utf-8")

if "import de.tbhelfer.vermessung.KfDateiParser" in text:
    print("006 integration already applied")
    raise SystemExit(0)


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    text = text.replace(old, new, 1)

replace_once(
    "import de.tbhelfer.vermessung.GvpDateiParser\n",
    "import de.tbhelfer.vermessung.GvpDateiParser\nimport de.tbhelfer.vermessung.KfDateiParser\n",
    "KfDateiParser import",
)

replace_once(
    '''            val ergebnisse = withContext(Dispatchers.IO) {
                scan.dateien
                    .filter { it.sollImportiertWerden(objektZuordnungen.keys) }
                    .map { datei -> importiereZipDatei(datei, objektZuordnungen[datei.rawKey]) }
            }
''',
    '''            val ergebnisse = withContext(Dispatchers.IO) {
                val kandidaten = scan.dateien
                    .filter { it.sollImportiertWerden(objektZuordnungen.keys) }
                // Roh-KF ist ab 2.0 (006) die bevorzugte Originalquelle. Eine gleichnamige
                // KF.TXT bleibt nur Fallback, wenn keine rohe .kf im ZIP vorhanden ist.
                val roheKfBasen = kandidaten
                    .filter { it.typ == ZipDateiTyp.KF_RAW }
                    .map { it.dateiname.lowercase(Locale.GERMANY) }
                    .toSet()
                kandidaten
                    .filterNot { datei ->
                        datei.typ == ZipDateiTyp.KF_TXT &&
                            datei.dateiname.lowercase(Locale.GERMANY).removeSuffix(".txt") in roheKfBasen
                    }
                    .map { datei -> importiereZipDatei(datei, objektZuordnungen[datei.rawKey]) }
            }
''',
    "raw KF preference",
)

replace_once(
    '''            ZipDateiTyp.KF_RAW -> ImportErgebnis(datei.dateiname, true, "nicht direkt importiert: KF-Original erkannt; falls vorhanden wird KF.TXT verwendet")
''',
    '''            ZipDateiTyp.KF_RAW -> importiereZipKfDatei(datei)
''',
    "KF_RAW dispatch",
)

replace_once(
    '''    private suspend fun importiereZipGvpDatei(datei: StreckendatenZipDatei): ImportErgebnis = runCatching {
        val result = GvpDateiParser.parse(datei.dateiname, datei.bytes)
        ServiceLocator.trassenRepository.gvpImportSpeichern(datei.dateiname, result)
        ImportErgebnis(datei.dateiname, true, "${result.punkte.size} GVP/Festpunkte")
    }.getOrElse { throwable ->
        ImportErgebnis(datei.dateiname, false, throwable.message ?: throwable.javaClass.simpleName)
    }

''',
    '''    private suspend fun importiereZipGvpDatei(datei: StreckendatenZipDatei): ImportErgebnis = runCatching {
        val result = GvpDateiParser.parse(datei.dateiname, datei.bytes)
        ServiceLocator.trassenRepository.gvpImportSpeichern(datei.dateiname, result)
        ImportErgebnis(datei.dateiname, true, "${result.punkte.size} GVP/Festpunkte")
    }.getOrElse { throwable ->
        ImportErgebnis(datei.dateiname, false, throwable.message ?: throwable.javaClass.simpleName)
    }

    private suspend fun importiereZipKfDatei(datei: StreckendatenZipDatei): ImportErgebnis = runCatching {
        val result = KfDateiParser.parse(datei.dateiname, datei.bytes)
        ServiceLocator.trassenRepository.gvpImportSpeichern(datei.dateiname, result)
        val mitKm = result.punkte.count { it.kmMeter != null && !it.strecke.isNullOrBlank() }
        val gvp = result.punkte.count {
            it.typ.equals("PS4", true) || it.typ.equals("GVPV", true)
        }
        ImportErgebnis(
            datei.dateiname,
            true,
            "KF 7.0 direkt: ${result.punkte.size} Punkte · $gvp GVP/Gleisvermarkung · $mitKm mit Strecke+km"
        )
    }.getOrElse { throwable ->
        ImportErgebnis(datei.dateiname, false, throwable.message ?: throwable.javaClass.simpleName)
    }

''',
    "zip KF importer",
)

replace_once(
    '''            val result = GvpDateiParser.parse(dateiname, bytes)
            ServiceLocator.trassenRepository.gvpImportSpeichern(dateiname, result)
''',
    '''            val result = if (dateiname.endsWith(".kf", ignoreCase = true) &&
                !dateiname.endsWith(".kf.txt", ignoreCase = true)
            ) {
                KfDateiParser.parse(dateiname, bytes)
            } else {
                GvpDateiParser.parse(dateiname, bytes)
            }
            ServiceLocator.trassenRepository.gvpImportSpeichern(dateiname, result)
''',
    "standalone KF importer",
)

replace_once(
    '''                TrassenGrafikFestpunkt(
                    label = it.punktName,
                    art = "GVP",
                    x = it.xDbRef.toFloat(),
''',
    '''                val originalTyp = it.typ?.trim()?.uppercase(Locale.GERMANY)
                val grafikArt = if (originalTyp == "PS4" || originalTyp == "GVPV") "GVP" else "FESTPUNKT"
                TrassenGrafikFestpunkt(
                    label = it.punktName,
                    art = grafikArt,
                    x = it.xDbRef.toFloat(),
''',
    "GVP/Festpunkt classification",
)

replace_once(
    '''                    val punktKonfiguration = listOf(
                        "GVP" to "Festpunkte / Masten",
                        "AP" to "Absteckpunkte",
                        "WEICHENPUNKT" to "Weichenpunkte"
                    ).filter { (art, _) -> festpunkte.any { it.art == art } }
''',
    '''                    val punktKonfiguration = listOf(
                        "GVP" to "GVP / Gleisvermarkung (PS4/GVPV)",
                        "FESTPUNKT" to "Festpunkte / sonstige KF-Punkte",
                        "AP" to "Absteckpunkte",
                        "WEICHENPUNKT" to "Weichenpunkte"
                    ).filter { (art, _) -> festpunkte.any { it.art == art } }
''',
    "graphics filter labels",
)

path.write_text(text, encoding="utf-8")
print("006 integration applied")
