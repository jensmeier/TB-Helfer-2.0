import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

val localTbProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

val debugServerBaseUrl = providers.gradleProperty("tbServerBaseUrl")
    .orElse(providers.environmentVariable("TB_SERVER_BASE_URL"))
    .orElse(localTbProperties.getProperty("tb.server.base.url") ?: "https://api-v1.tb-helfer.de")
    .get()
    .trim()
    .trimEnd('/')

require(
    debugServerBaseUrl.startsWith("http://") || debugServerBaseUrl.startsWith("https://")
) {
    "tb.server.base.url muss mit http:// oder https:// beginnen."
}

val escapedDebugServerBaseUrl = debugServerBaseUrl
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

abstract class GenerateBtb200043Sources : DefaultTask() {
    @get:InputDirectory
    abstract val inputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val inputRoot = inputDirectory.get().asFile
        val outputRoot = outputDirectory.get().asFile
        outputRoot.deleteRecursively()
        check(inputRoot.copyRecursively(outputRoot, overwrite = true)) {
            "Android 200043: Android-Quellbaum konnte nicht in das Build-Verzeichnis kopiert werden."
        }

        val target = outputRoot.resolve(
            "de/tbhelfer/ui/bautagebuch/BautagebuchFragment.kt"
        )
        check(target.isFile) {
            "Android 200043: BautagebuchFragment.kt wurde im Build-Quellbaum nicht gefunden."
        }

        // Windows/Git kann einzelne Quelldateien als CRLF auschecken. Alle Build-Source-
        // Patches arbeiten deshalb intern mit LF, damit ein reiner Zeilenumbruchwechsel
        // keinen fachlich unveränderten Build mehr blockiert.
        val source = target.readText().replace("\r\n", "\n")
        val anchor = """
                        val firmTitle = firm ?: getString(R.string.btb_company_general)

                        val dlg = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle(getString(R.string.btb_entry_dialog_title, firmTitle))
                            .setView(root)
                            .setNegativeButton(R.string.action_cancel, null)
                            .setPositiveButton(R.string.action_add, null)
                            .create()
""".trim('\n')

        check(source.contains(anchor)) {
            "Android 200043: BTB-Eintrag-Dialog-Anker wurde nicht gefunden. BautagebuchFragment.kt hat sich geändert."
        }

        val photoActions = """
                        // Android 200050: derselbe direkte Bildpfad fuer BTB-Punkte 4 bis 8.
                        // Der bestehende Kamera-/Galeriepfad wird wiederverwendet; kein zweiter Foto- oder Overlaypfad.
                        if (fotoType != null) {
                            BtbEntryPhotoActionBinder.bind(
                                fragment = this@BautagebuchFragment,
                                container = root,
                                entryDialog = dlg,
                                sectionDialog = dialog,
                                firma = firm,
                                bildtyp = fotoType,
                                textField = etText,
                                textLayout = tilText,
                                timeField = etTime,
                                timeLayout = tilTime,
                                kmVonField = etKmVon,
                                kmVonLayout = tilKmVon,
                                kmBisField = etKmBis,
                                kmBisLayout = tilKmBis,
                                ortProvider = {
                                    selectedOrtMarkerId to labelForOrtCached(selectedOrtMarkerId)
                                },
                                onCommitManualEntry = { draft ->
                                    val line = buildManualLine(
                                        draft.ortId,
                                        firm,
                                        draft.time,
                                        draft.text,
                                        draft.kmVon,
                                        draft.kmBis,
                                        clientEntryKey = draft.clientEntryKey
                                    )
                                    val curLines = etFull.text?.toString().orEmpty().lines().toMutableList()
                                    val base = if (curLines.size == 1 && curLines.first().isBlank()) {
                                        mutableListOf<String>()
                                    } else {
                                        curLines
                                    }
                                    setRawLines((base + line).filter { it.isNotBlank() })
                                    renderMerged?.invoke(lastGroups)
                                    onSave(etFull.text?.toString().orEmpty())
                                },
                                snapshotProvider = {
                                    val ctx = resolveAktuelleBaustelle()
                                    if (ctx == null) {
                                        null
                                    } else {
                                        val existing = withContext(Dispatchers.IO) {
                                            BautagebuchSpeicher.laden(
                                                requireContext(),
                                                ctx.id,
                                                ctx.name,
                                                selectedDate,
                                                selectedSchichtTyp
                                            )
                                        }
                                        val built = buildEntry(ctx)
                                        built.copy(
                                            btbId = existing?.btbId ?: built.btbId,
                                            maxFotosInPdf = existing?.maxFotosInPdf ?: built.maxFotosInPdf,
                                            pdfAuswahl = existing?.pdfAuswahl ?: built.pdfAuswahl,
                                            erstelltAmEpochMs = existing?.erstelltAmEpochMs ?: built.erstelltAmEpochMs,
                                            geaendertAmEpochMs = System.currentTimeMillis()
                                        )
                                    }
                                }
                            )
                        }
""".trim('\n')

        var patched = source.replace(anchor, anchor + "\n\n" + photoActions)
        check(patched != source) { "Android 200043: BTB-Quelldatei wurde nicht erweitert." }

        // Android 200047 / BTB-EDIT-001:
        // Ortsmarker-Semantik liegt jetzt kanonisch in BautagebuchFragment.kt.
        // Kein Build-time-Source-Patch mehr: neue Zeilen tragen immer @ORT=<lokale UI-ID>,
        // waehrend @NODE=<clientNodeKey> die geräteübergreifende Sync-Identität ist.
        // Legacy-Standalone-@ORT-Gruppen werden direkt im Parser kompatibel behandelt.

        // Android 200063: Die aktive-btbId-Ladelogik ist jetzt kanonisch in
        // BautagebuchFragment.kt umgesetzt. Kein Build-time-Patch mehr fuer diesen Pfad,
        // damit Quellstand und tatsaechlich kompilierter Stand identisch bleiben.

        target.writeText(patched)

        // Android 200046: Der ZIP-Scanner erkannte rohe .kf bereits, hat sie bisher aber
        // bewusst nicht importiert. Der vorhandene GVP/Festpunktpfad verarbeitet jetzt auch
        // KF_RAW; GvpDateiParser liest das 102-Byte-KF-7.0-Format direkt. Die kanonische
        // ImportExportFragment-Datei bleibt bis zum Rueckbau der 200043-Source-Bruecke
        // unveraendert, deshalb wird die gleiche kleine Routing-Aenderung hier in die
        // generierte Build-Kopie eingesetzt.
        val importExportTarget = outputRoot.resolve(
            "de/tbhelfer/ui/importexport/ImportExportFragment.kt"
        )
        check(importExportTarget.isFile) {
            "Android 200046: ImportExportFragment.kt wurde im Build-Quellbaum nicht gefunden."
        }
        val importSource = importExportTarget.readText().replace("\r\n", "\n")
        val rawKfIgnored = """
            ZipDateiTyp.GVP_TXT,
            ZipDateiTyp.KF_TXT,
            ZipDateiTyp.KV -> importiereZipGvpDatei(datei)
            ZipDateiTyp.KF_RAW -> ImportErgebnis(datei.dateiname, true, "nicht direkt importiert: KF-Original erkannt; falls vorhanden wird KF.TXT verwendet")
""".trim('\n')
        val rawKfImported = """
            ZipDateiTyp.GVP_TXT,
            ZipDateiTyp.KF_TXT,
            ZipDateiTyp.KF_RAW,
            ZipDateiTyp.KV -> importiereZipGvpDatei(datei)
""".trim('\n')
        check(importSource.contains(rawKfIgnored)) {
            "Android 200046: KF_RAW-Importanker wurde nicht gefunden. ImportExportFragment.kt hat sich geändert."
        }
        val importPatched = importSource.replace(rawKfIgnored, rawKfImported)
        check(importPatched != importSource) {
            "Android 200046: KF_RAW wurde nicht in den bestehenden GVP-Importpfad aufgenommen."
        }
        importExportTarget.writeText(importPatched)

        // Android 200049: Kamera/Fotopfad verwendet eine einheitliche 10/30-m-Ampel,
        // beruecksichtigt die GPS-Genauigkeit und reicht sie an die Foto-Maske weiter.
        val kameraTarget = outputRoot.resolve("de/tbhelfer/ui/kamera/KameraFragment.kt")
        check(kameraTarget.isFile) { "Android 200049: KameraFragment.kt fehlt im Build-Quellbaum." }
        var kameraSource = kameraTarget.readText().replace("\r\n", "\n")

        val kameraTrefferOld = """
        val verbessert = kandidaten.map { ueberlagereManuellenAbschnitt(it) }
        return if (aktuellerKontext == null) {
            verbessert.firstOrNull()
        } else {
            verbessert.firstOrNull { trefferPasstZumKontext(it, aktuellerKontext) }
        }
""".trim('\n')
        val kameraTrefferNew = """
        val gpsAccuracyM = loc.accuracy.toDouble().takeIf { loc.hasAccuracy() }
        val verbessert = kandidaten.map { kandidat ->
            val mitAbschnitt = ueberlagereManuellenAbschnitt(kandidat)
            mitAbschnitt.copy(
                qualitaet = FotoKmQualitaet.trassenQualitaet(mitAbschnitt.abstandM, gpsAccuracyM)
            )
        }
        return if (aktuellerKontext == null) {
            verbessert.firstOrNull()
        } else {
            verbessert.firstOrNull { trefferPasstZumKontext(it, aktuellerKontext) }
        }
""".trim('\n')
        check(kameraSource.contains(kameraTrefferOld)) {
            "Android 200049: Kamera-TRA-Qualitaetsanker wurde nicht gefunden."
        }
        kameraSource = kameraSource.replace(kameraTrefferOld, kameraTrefferNew)

        val objektQualitaetOld = """
        val qualitaet = when {
            linie.abstandM <= 2.0 -> TrassenNaheBerechner.Qualitaet.GRUEN
            linie.abstandM <= 5.0 -> TrassenNaheBerechner.Qualitaet.GELB
            linie.abstandM <= 15.0 -> TrassenNaheBerechner.Qualitaet.ORANGE
            else -> TrassenNaheBerechner.Qualitaet.ROT
        }
""".trim('\n')
        val objektQualitaetNew = """
        val qualitaet = FotoKmQualitaet.trassenQualitaet(
            linie.abstandM,
            loc.accuracy.toDouble().takeIf { loc.hasAccuracy() }
        )
""".trim('\n')
        check(kameraSource.contains(objektQualitaetOld)) {
            "Android 200049: Kamera-Objekt-Qualitaetsanker wurde nicht gefunden."
        }
        kameraSource = kameraSource.replace(objektQualitaetOld, objektQualitaetNew)

        val gpsArgsOld = """
                        bundle.putDouble(FotoErfassenFragment.ARG_LAT, standort.latitude)
                        bundle.putDouble(FotoErfassenFragment.ARG_LON, standort.longitude)
                        standort.altitude.let { bundle.putDouble(FotoErfassenFragment.ARG_ALT, it) }
""".trim('\n')
        val gpsArgsNew = """
                        bundle.putDouble(FotoErfassenFragment.ARG_LAT, standort.latitude)
                        bundle.putDouble(FotoErfassenFragment.ARG_LON, standort.longitude)
                        if (standort.hasAccuracy()) {
                            bundle.putDouble(FotoErfassenFragment.ARG_GPS_ACCURACY, standort.accuracy.toDouble())
                        }
                        standort.altitude.let { bundle.putDouble(FotoErfassenFragment.ARG_ALT, it) }
""".trim('\n')
        check(kameraSource.contains(gpsArgsOld)) {
            "Android 200049: Kamera-GPS-Arg-Anker wurde nicht gefunden."
        }
        kameraSource = kameraSource.replace(gpsArgsOld, gpsArgsNew)
        kameraTarget.writeText(kameraSource)

        // Foto-Erfassung: gleiche Ampel, automatischer km bis zur roten Zone und
        // konsistente Hauptbaustellen-/Strecke-Gleis-Anzeige.
        val fotoTarget = outputRoot.resolve("de/tbhelfer/ui/kamera/FotoErfassenFragment.kt")
        check(fotoTarget.isFile) { "Android 200049: FotoErfassenFragment.kt fehlt im Build-Quellbaum." }
        var fotoSource = fotoTarget.readText().replace("\r\n", "\n")

        val fotoArgsOld = """
        val achsabstand = args.getDouble(ARG_ACHsA, Double.NaN).takeIf { !it.isNaN() }
        val indikator = Indikator.fromString(args.getString(ARG_INDIKATOR)) ?: Indikator.ROT
""".trim('\n')
        val fotoArgsNew = """
        val achsabstand = args.getDouble(ARG_ACHsA, Double.NaN).takeIf { !it.isNaN() }
        val gpsAccuracyM = args.getDouble(ARG_GPS_ACCURACY, Double.NaN).takeIf { !it.isNaN() }
        val indikatorFallback = Indikator.fromString(args.getString(ARG_INDIKATOR)) ?: Indikator.ROT
        val kmBewertung = FotoKmQualitaet.bewerten(achsabstand, gpsAccuracyM, indikatorFallback)
        val indikator = kmBewertung.indikator
""".trim('\n')
        check(fotoSource.contains(fotoArgsOld)) { "Android 200049: Foto-GPS/Ampel-Arg-Anker fehlt." }
        fotoSource = fotoSource.replace(fotoArgsOld, fotoArgsNew)

        val warnOld = """
        // Warnung: wenn Achsabstand > 5m => km muss als "manuell" gelten
        if (achsabstand != null && achsabstand > 5.0) {
            txtWarnKm.visibility = View.VISIBLE
            txtWarnKm.text = "Achtung: Abstand zur berechneten Achse > 3 m (%.1f m). km bitte prüfen/ggf. manuell eintragen.".format(Locale.GERMANY, achsabstand)
        }
""".trim('\n')
        val warnNew = """
        // 200049: feste 10/30-m-Ampel; GPS-Genauigkeit darf nur abwerten, niemals verbessern.
        val gpsHinweis = gpsAccuracyM?.let { " · GPS ±%.1f m".format(Locale.GERMANY, it) }.orEmpty()
        when (indikator) {
            Indikator.GRUEN -> txtWarnKm.visibility = View.GONE
            Indikator.GELB -> {
                txtWarnKm.visibility = View.VISIBLE
                txtWarnKm.setTextColor(Color.rgb(230, 150, 0))
                txtWarnKm.text = "Hinweis: Achsabstand %.1f m%s. Automatischer km bleibt aktiv.".format(
                    Locale.GERMANY, achsabstand ?: 0.0, gpsHinweis
                )
            }
            Indikator.ROT -> {
                txtWarnKm.visibility = View.VISIBLE
                txtWarnKm.setTextColor(themeColor(android.R.attr.colorError, Color.RED))
                txtWarnKm.text = "Achtung: Achsabstand %.1f m%s. km bitte prüfen/ggf. manuell eintragen.".format(
                    Locale.GERMANY, achsabstand ?: 0.0, gpsHinweis
                )
            }
        }
""".trim('\n')
        check(fotoSource.contains(warnOld)) { "Android 200049: Foto-KM-Warnanker fehlt." }
        fotoSource = fotoSource.replace(warnOld, warnNew)

        val kmManuellOld = "var kmManuell = (kmVorschlag == null) || (achsabstand != null && achsabstand > 5.0)"
        val kmManuellNew = "var kmManuell = (kmVorschlag == null) || indikator == Indikator.ROT"
        check(fotoSource.contains(kmManuellOld)) { "Android 200049: Foto-kmManuell-Anker fehlt." }
        fotoSource = fotoSource.replace(kmManuellOld, kmManuellNew)

        val knotenOld = """
            val knoten = knotenId?.let { ServiceLocator.baustellenBaumRepository.knotenMitId(it) }
            val untertitel = BaustellenObjektLogik.unterbaustellenName(knoten)
""".trim('\n')
        val knotenNew = """
            val knoten = knotenId?.let { ServiceLocator.baustellenBaumRepository.knotenMitId(it) }
            val baustellenAnzeigeName = FotoStreckenAnzeige.baustellenName(knoten, b?.baustellenname)
            val streckeGleisAnzeige = FotoStreckenAnzeige.streckeGleis(knoten, b?.streckennummer, b?.bahnhof)
            val untertitel = BaustellenObjektLogik.unterbaustellenName(knoten)
""".trim('\n')
        check(fotoSource.contains(knotenOld)) { "Android 200049: Foto-Knoten-Anker fehlt." }
        fotoSource = fotoSource.replace(knotenOld, knotenNew)
        fotoSource = fotoSource.replace(
            "append(\"Baustelle: ${'$'}{b?.baustellenname ?: baustelleId}\\n\")",
            "append(\"Baustelle: ${'$'}baustellenAnzeigeName\\n\")"
        )
        fotoSource = fotoSource.replace(
            "append(\"Strecke/Bahnhof: ${'$'}streckeOderBahnhof\\n\")",
            "append(\"Strecke/Gleis: ${'$'}streckeGleisAnzeige\\n\")"
        )

        val saveKnotenOld = """
                        val fotoKnoten = knotenId?.let { ServiceLocator.baustellenBaumRepository.knotenMitId(it) }
                        val unterName = BaustellenObjektLogik.unterbaustellenName(fotoKnoten)
""".trim('\n')
        val saveKnotenNew = """
                        val fotoKnoten = knotenId?.let { ServiceLocator.baustellenBaumRepository.knotenMitId(it) }
                        val baustellenAnzeigeName = FotoStreckenAnzeige.baustellenName(fotoKnoten, b?.baustellenname)
                        val streckeGleisAnzeige = FotoStreckenAnzeige.streckeGleis(fotoKnoten, b?.streckennummer, b?.bahnhof)
                        val unterName = BaustellenObjektLogik.unterbaustellenName(fotoKnoten)
""".trim('\n')
        check(fotoSource.contains(saveKnotenOld)) { "Android 200049: Foto-Speicher-Knoten-Anker fehlt." }
        fotoSource = fotoSource.replace(saveKnotenOld, saveKnotenNew)
        fotoSource = fotoSource.replace(
            "val overlayBahnhof = overlayBahnhofName(streckeOderBahnhof, unterName, overlayObjekt)",
            "val overlayBahnhof = streckeGleisAnzeige"
        )
        fotoSource = fotoSource.replace(
            "appendLine(\"Baustelle=${'$'}{b?.baustellenname ?: \"Baustelle\"}\")",
            "appendLine(\"Baustelle=${'$'}baustellenAnzeigeName\")"
        )
        fotoSource = fotoSource.replace(
            "baustellenName = b?.baustellenname ?: \"Baustelle\"",
            "baustellenName = baustellenAnzeigeName"
        )
        fotoSource = fotoSource.replace(
            "val baustellenName = b?.baustellenname ?: \"UnbekannteBaustelle\"",
            "val baustellenName = baustellenAnzeigeName"
        )

        val argOld = "const val ARG_ACHsA = \"achsabstand\""
        val argNew = "const val ARG_ACHsA = \"achsabstand\"\n        const val ARG_GPS_ACCURACY = \"gpsAccuracyM\""
        check(fotoSource.contains(argOld)) { "Android 200049: Foto-Companion-Arg-Anker fehlt." }
        fotoSource = fotoSource.replace(argOld, argNew)
        fotoTarget.writeText(fotoSource)

        // Das vorhandene Overlay bleibt derselbe Renderpfad. Nur die km-Zeile bekommt
        // eine groessere/fette Schrift, damit sie auch im PDF noch lesbar bleibt.
        val overlayTarget = outputRoot.resolve("de/tbhelfer/ui/overlay/OverlayRenderer.kt")
        check(overlayTarget.isFile) { "Android 200049: OverlayRenderer.kt fehlt im Build-Quellbaum." }
        var overlaySource = overlayTarget.readText().replace("\r\n", "\n")
        val overlayPaintOld = """
        val gpsTextPaint = Paint(textPaint).apply {
            // Patch 33: GPS bleibt im Overlay, aber kleiner/ruhiger als die Fachzeilen.
            textSize = textPaint.textSize * 0.82f
        }
        fun paintForLine(line: String): Paint =
            if (line.trimStart().startsWith("GPS", ignoreCase = true)) gpsTextPaint else textPaint
""".trim('\n')
        val overlayPaintNew = """
        val gpsTextPaint = Paint(textPaint).apply {
            // Patch 33: GPS bleibt im Overlay, aber kleiner/ruhiger als die Fachzeilen.
            textSize = textPaint.textSize * 0.82f
        }
        val kmTextPaint = Paint(textPaint).apply {
            textSize = textPaint.textSize * 1.55f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        fun paintForLine(line: String): Paint = when {
            line.trimStart().startsWith("km ", ignoreCase = true) -> kmTextPaint
            line.trimStart().startsWith("GPS", ignoreCase = true) -> gpsTextPaint
            else -> textPaint
        }
""".trim('\n')
        check(overlaySource.contains(overlayPaintOld)) { "Android 200049: Overlay-km-Paint-Anker fehlt." }
        overlaySource = overlaySource.replace(overlayPaintOld, overlayPaintNew)
        val overlayColorOld = """
        textPaint.color = textColor
        gpsTextPaint.color = textColor
""".trim('\n')
        val overlayColorNew = """
        textPaint.color = textColor
        gpsTextPaint.color = textColor
        kmTextPaint.color = textColor
""".trim('\n')
        check(overlaySource.contains(overlayColorOld)) { "Android 200049: Overlay-km-Farbanker fehlt." }
        overlaySource = overlaySource.replace(overlayColorOld, overlayColorNew)
        overlayTarget.writeText(overlaySource)

        // Android 200044: Room/KSP muss die kanonischen Datenbankquellen als normalen
        // Android-Kotlin-Source-Set sehen. Die Build-Kopie darf diese Klassen deshalb nicht
        // ein zweites Mal enthalten; sonst gäbe es doppelte Klassen. Der Datenbank-Ordner
        // wird unten separat aus src/main/java eingebunden und von KSP verarbeitet.
        val generatedDatabaseDir = outputRoot.resolve("de/tbhelfer/datenbank")
        val generatedTrassenRepository = generatedDatabaseDir.resolve("TrassenRepository.kt")
        check(generatedTrassenRepository.isFile && generatedTrassenRepository.readText().contains(
            "if (achsen.isEmpty() || samplesNachAchse.isEmpty())"
        )) {
            "Android 200048: GVP-KM-Kalibrierung ohne GRA ist im TrassenRepository nicht aktiv."
        }
        check(generatedDatabaseDir.deleteRecursively()) {
            "Android 200044: generierte Datenbank-Kopie konnte nicht entfernt werden."
        }
    }
}

val generateBtb200043Sources = tasks.register<GenerateBtb200043Sources>("generateBtb200043Sources") {
    inputDirectory.set(layout.projectDirectory.dir("src/main/java"))
    outputDirectory.set(layout.buildDirectory.dir("generated/source/btb200043"))
}

android {
    namespace = "de.tbhelfer"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.tbhelfer"
        minSdk = 23
        targetSdk = 36
        // Android 200076: kleiner Foto-Sync, mobile Renditions, Foto-Metadaten-PATCH
        // und Wetter-Niederschlagsmenge inkl. bekanntem 0,0-mm-Wert.
        // Jeder auslieferbare Test-/Release-Build erhält einen strikt höheren versionCode.
        versionCode = 200088
        versionName = "1.3.38"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        getByName("main") {
            // 200043: der normale Quellbaum wird fuer den BTB-Hook weiterhin durch die
            // generierte Build-Kopie ersetzt. 200044: die Room-Datenbankquellen bleiben
            // bewusst als kanonischer statischer Kotlin-Source-Ordner sichtbar, damit KSP
            // TBDatenbank_Impl und die DAO-Implementierungen wieder erzeugt.
            java.directories.clear()
            kotlin.directories.clear()
            kotlin.directories += "src/main/java/de/tbhelfer/datenbank"
        }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "SERVER_BASE_URL", "\"$escapedDebugServerBaseUrl\"")
            manifestPlaceholders["usesCleartextTraffic"] =
                debugServerBaseUrl.startsWith("http://").toString()
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("String", "SERVER_BASE_URL", "\"https://api.tb-helfer.de\"")
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        checkDependencies = true
        lintConfig = file("$rootDir/lint.xml")
    }
}

androidComponents.onVariants { variant ->
    // The source generator also copies the pure-Java sync monitor. Register it for javac;
    // Kotlin-only registration is insufficient when the normal Java source set is cleared.
    variant.sources.java?.addGeneratedSourceDirectory(
        generateBtb200043Sources,
        GenerateBtb200043Sources::outputDirectory
    )
    variant.sources.kotlin?.addGeneratedSourceDirectory(
        generateBtb200043Sources,
        GenerateBtb200043Sources::outputDirectory
    )
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
}

val verifyRoomGeneratedImplementation = tasks.register("verifyRoomGeneratedImplementation") {
    group = "verification"
    description = "Fails the Android build when Room did not generate TBDatenbank_Impl."
    val roomGeneratedRoot = project.layout.buildDirectory.dir("generated/ksp")
    doLast {
        val kspRoot = roomGeneratedRoot.get().asFile
        val roomImplExists = kspRoot.isDirectory && kspRoot.walkTopDown().any { file ->
            file.isFile && file.nameWithoutExtension == "TBDatenbank_Impl"
        }
        check(roomImplExists) {
            "Android 200044: Room hat TBDatenbank_Impl nicht erzeugt. APK darf nicht installiert werden."
        }
    }
}

tasks.matching {
    it.name == "compileDebugKotlin" || it.name == "compileReleaseKotlin"
}.configureEach {
    finalizedBy(verifyRoomGeneratedImplementation)
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.extensions)
    implementation(libs.play.services.code.scanner)
    implementation(libs.zxing.core)
    // 200058: nur zum Nachtragen echter URI-Link-Annotationen in bereits gerenderte PDFs.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
}

tasks.register("testClasses") {
    group = "verification"
    description = "Compatibility alias for Java 'testClasses' (runs Android unit tests)."
    dependsOn(tasks.matching { it.name == "testDebugUnitTest" || it.name == "test" })
}







