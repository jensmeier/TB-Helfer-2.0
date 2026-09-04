plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

val kf006OriginalFragment = file("src/main/java/de/tbhelfer/ui/importexport/ImportExportFragment.kt")
val kf006GeneratedDir = layout.buildDirectory.dir("generated/source/kf006").get().asFile
val kf006GeneratedFragment = file("${kf006GeneratedDir.path}/de/tbhelfer/ui/importexport/ImportExportFragment006.kt")

// 009: Die Kamera wird fuer den Handy-Test als deterministisch gepatchte Build-Kopie erzeugt.
// Neben der Live-KM-Anzeige aus 008 wird beim Ausloesen jetzt ein dauerhafter
// Vermessungs-Schnappschuss fuer das spaetere Foto/BTB/PDF vorbereitet.
val km009OriginalCamera = file("src/main/java/de/tbhelfer/ui/kamera/KameraFragment.kt")
val km009GeneratedDir = layout.buildDirectory.dir("generated/source/km009").get().asFile
val km009GeneratedCamera = file("${km009GeneratedDir.path}/de/tbhelfer/ui/kamera/KameraFragment009.kt")

android {
    namespace = "de.tbhelfer"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.tbhelfer"
        minSdk = 23
        targetSdk = 36

        // TB-Helfer 2.0: sichtbarer Handy-Teststand 009.
        versionCode = 200009
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    // Die grossen Original-Fragmente bleiben im Repository unveraendert.
    // Fuer den Build werden die bestaetigten 006-/009-Kopien erzeugt.
    sourceSets {
        getByName("main") {
            (kotlin as org.gradle.api.tasks.util.PatternFilterable)
                .exclude("de/tbhelfer/ui/importexport/ImportExportFragment.kt")
                .exclude("de/tbhelfer/ui/kamera/KameraFragment.kt")
            kotlin.directories.add(kf006GeneratedDir.path)
            kotlin.directories.add(km009GeneratedDir.path)
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        checkDependencies = true
        lintConfig = file("$rootDir/lint.xml")
    }
}

val generateKf006ImportFragment by tasks.registering {
    group = "build setup"
    description = "Erzeugt ImportExportFragment fuer TB-Helfer 2.0 mit direktem KF-Import."
    inputs.file(kf006OriginalFragment)
    outputs.file(kf006GeneratedFragment)

    doLast {
        val originalFragment = inputs.files.singleFile
        val generatedFragment = outputs.files.singleFile
        var text = originalFragment.readText(Charsets.UTF_8)
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        fun replaceRequired(old: String, new: String, label: String, all: Boolean = false) {
            if (text.contains(new)) return
            check(text.contains(old)) { "006-Integration fehlgeschlagen: $label nicht gefunden" }
            text = if (all) text.replace(old, new) else text.replaceFirst(old, new)
        }

        replaceRequired(
            "import de.tbhelfer.vermessung.GvpDateiParser",
            """import de.tbhelfer.vermessung.GvpDateiParser
import de.tbhelfer.vermessung.KfDateiParser
import de.tbhelfer.vermessung.VermessungspunktKategorie""",
            "KF-Parser-Import"
        )

        replaceRequired(
            """            val ergebnisse = withContext(Dispatchers.IO) {
                scan.dateien
                    .filter { it.sollImportiertWerden(objektZuordnungen.keys) }
                    .map { datei -> importiereZipDatei(datei, objektZuordnungen[datei.rawKey]) }
            }
""",
            """            val ergebnisse = withContext(Dispatchers.IO) {
                val kandidaten = scan.dateien
                    .filter { it.sollImportiertWerden(objektZuordnungen.keys) }
                // Roh-KF ist die Primaerquelle. Liegt dieselbe Datei zusaetzlich als
                // .kf.txt vor, wird die Textableitung nicht ein zweites Mal importiert.
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
""",
            "Roh-KF-Prioritaet"
        )

        replaceRequired(
            "            ZipDateiTyp.KF_RAW -> ImportErgebnis(datei.dateiname, true, \"nicht direkt importiert: KF-Original erkannt; falls vorhanden wird KF.TXT verwendet\")",
            "            ZipDateiTyp.KF_RAW -> importiereZipKfDatei(datei)",
            "KF_RAW-Dispatch"
        )

        replaceRequired(
            "    private fun speichereStreckendatenZuordnung(",
            """    private suspend fun importiereZipKfDatei(datei: StreckendatenZipDatei): ImportErgebnis = runCatching {
        val result = KfDateiParser.parse(datei.dateiname, datei.bytes)
        ServiceLocator.trassenRepository.gvpImportSpeichern(datei.dateiname, result)
        val mitKm = result.punkte.count { it.kmMeter != null && !it.strecke.isNullOrBlank() }
        val gvp = result.punkte.count {
            VermessungspunktKategorie.grafikArt(it.typ, datei.dateiname) == VermessungspunktKategorie.GVP
        }
        ImportErgebnis(
            datei.dateiname,
            true,
            "KF 7.0 direkt: ${'$'}{result.punkte.size} Punkte · ${'$'}gvp GVP/Gleisvermarkung · ${'$'}mitKm mit Strecke+km"
        )
    }.getOrElse { throwable ->
        ImportErgebnis(datei.dateiname, false, throwable.message ?: throwable.javaClass.simpleName)
    }

    private fun speichereStreckendatenZuordnung(""",
            "KF-Importfunktion"
        )

        replaceRequired(
            "            val result = GvpDateiParser.parse(dateiname, bytes)",
            """            val result = if (dateiname.endsWith(".kf", ignoreCase = true) &&
                !dateiname.endsWith(".kf.txt", ignoreCase = true)
            ) {
                KfDateiParser.parse(dateiname, bytes)
            } else {
                GvpDateiParser.parse(dateiname, bytes)
            }""",
            "Einzeldatei-KF-Import"
        )

        replaceRequired(
            """                TrassenGrafikFestpunkt(
                    label = it.punktName,
                    art = "GVP",
""",
            """                TrassenGrafikFestpunkt(
                    label = it.punktName,
                    art = VermessungspunktKategorie.grafikArt(it.typ, it.quelleDatei),
""",
            "Grafik-Kategorisierung",
            all = true
        )

        replaceRequired(
            "                        \"GVP\" to \"Festpunkte / Masten\",",
            """                        "GVP" to "GVP / Gleisvermarkung (PS4/GVPV)",
                        "FESTPUNKT" to "Festpunkte / sonstige KF-Punkte",""",
            "Grafik-Anzeigefilter",
            all = true
        )

        generatedFragment.parentFile.mkdirs()
        generatedFragment.writeText(text, Charsets.UTF_8)
    }
}

val generateKm009KameraFragment by tasks.registering {
    group = "build setup"
    description = "Erzeugt KameraFragment fuer Teststand 009 mit Live-KM und Foto-Snapshot."
    inputs.file(km009OriginalCamera)
    outputs.file(km009GeneratedCamera)

    doLast {
        val originalCamera = inputs.files.singleFile
        val generatedCamera = outputs.files.singleFile
        var text = originalCamera.readText(Charsets.UTF_8)
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        fun replaceRequired009(old: String, new: String, label: String) {
            if (text.contains(new)) return
            check(text.contains(old)) { "009-Kamera-Integration fehlgeschlagen: $label nicht gefunden" }
            text = text.replaceFirst(old, new)
        }

        replaceRequired009(
            "import android.graphics.Color",
            """import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan""",
            "Live-KM-Text-Imports"
        )

        replaceRequired009(
            "import de.tbhelfer.datenbank.GvpPointEntity",
            """import de.tbhelfer.datenbank.GvpPointEntity
import de.tbhelfer.datenbank.FotoVermessungsSnapshotSpeicher""",
            "Foto-Snapshot-Import"
        )

        // Die sichtbare Fotoqualitaets-Einstellung steuert die CameraX-Aufnahme wirklich.
        replaceRequired009(
            """        imageCapture = ImageCapture.Builder()
            .setCaptureMode(captureMode)
            .setTargetRotation(displayRotation)
            .build()
""",
            """        imageCapture = ImageCapture.Builder()
            .setCaptureMode(captureMode)
            .setJpegQuality(kameraEinstellungen.jpegQualitaet.coerceIn(70, 100))
            .setTargetRotation(displayRotation)
            .build()
""",
            "JPEG-Qualitaet anwenden"
        )

        // Ein Objekt-/Weichentreffer darf niemals nur deshalb einen KM liefern, weil
        // der Kontext fest gewaehlt ist. Ab einer realistisch nicht mehr sicheren
        // Entfernung gilt: kein Live-Treffer.
        replaceRequired009(
            """        val naechsteLinie = linien.minByOrNull { it.abstandM } ?: return null
        if (letztesStreckenObjektId != objekt.id) {
""",
            """        val naechsteLinie = linien.minByOrNull { it.abstandM } ?: return null
        if (naechsteLinie.abstandM > liveKmMaxAbstandM(loc)) return null
        if (letztesStreckenObjektId != objekt.id) {
""",
            "Weichen-Live-Distanzgrenze"
        )

        replaceRequired009(
            "    private fun berechneTrassenTreffer(loc: Location): TrassenNaheBerechner.Ergebnis? {",
            """    private fun gpsFuerLiveKmBrauchbar(loc: Location): Boolean {
        // Ein scheinbar millimetergenauer Bahn-km ist bei schlechtem Handy-GPS irrefuehrend.
        // Bis einschliesslich 15 m darf ein Live-KM berechnet werden; darueber bleibt KM leer.
        return loc.hasAccuracy() && loc.accuracy <= 15.0f
    }

    private fun liveKmMaxAbstandM(loc: Location): Double {
        // Bei brauchbarem GPS maximal 25 m zur TRA akzeptieren. Die Toleranz waechst
        // nur leicht mit der gemeldeten GPS-Genauigkeit und bleibt bewusst begrenzt.
        val gpsGenauigkeit = if (loc.hasAccuracy()) loc.accuracy.toDouble().coerceAtLeast(1.0) else 15.0
        return maxOf(20.0, gpsGenauigkeit * 1.5).coerceAtMost(25.0)
    }

    private fun berechneTrassenTreffer(loc: Location): TrassenNaheBerechner.Ergebnis? {
        if (!gpsFuerLiveKmBrauchbar(loc)) return null""",
            "Live-KM-GPS-Sicherheitsfunktion"
        )

        replaceRequired009(
            "            maxAbstandM = if (aktuellerKontext == null) 250.0 else 500.0,",
            "            maxAbstandM = liveKmMaxAbstandM(loc),",
            "TRA-Live-Distanzgrenze"
        )

        // Auch der alte Streckenpunkte-Fallback darf bei schlechtem GPS keinen KM liefern.
        replaceRequired009(
            "                val res = if (trassenTreffer == null && streckenpunkteCache.size >= 2) {",
            "                val res = if (gpsFuerLiveKmBrauchbar(loc) && trassenTreffer == null && streckenpunkteCache.size >= 2) {",
            "Legacy-KM-GPS-Sicherheitsgrenze"
        )

        val overlayStartMarker =
            "    private fun aktualisiereOverlayText(baustelleId: Long, loc: Location, res: KmBerechner.Ergebnis) {"
        val overlayEndMarker =
            "    private fun TrassenNaheBerechner.Ergebnis.toKameraKurzzeile(): String {"
        val overlayStart = text.indexOf(overlayStartMarker)
        val overlayEnd = text.indexOf(overlayEndMarker, overlayStart.coerceAtLeast(0))
        check(overlayStart >= 0 && overlayEnd > overlayStart) {
            "009-Kamera-Integration fehlgeschlagen: Live-KM-Anzeigefunktion nicht gefunden"
        }

        val neueOverlayFunktion = """    private fun aktualisiereOverlayText(baustelleId: Long, loc: Location, res: KmBerechner.Ergebnis) {
        val trassen = letzterTrassenTreffer
        val gpsText = if (loc.hasAccuracy()) {
            "GPS ±" + String.format(Locale.GERMANY, "%.1f m", loc.accuracy)
        } else {
            "GPS-Genauigkeit unbekannt"
        }

        fun streckeObjektZeile(treffer: TrassenNaheBerechner.Ergebnis): String {
            val strecke = treffer.strecke?.trim().takeUnless { it.isNullOrBlank() }
            val richtung = treffer.richtung?.trim().takeUnless { it.isNullOrBlank() }
            val streckeText = strecke?.let { nummer ->
                if (richtung != null && !nummer.endsWith("-" + richtung)) {
                    "Str " + nummer + "-" + richtung
                } else {
                    "Str " + nummer
                }
            }
            val objektText = if (treffer.achsTyp == TrassenAchseEntity.ACHSTYP_STRECKE) {
                null
            } else {
                treffer.kameraObjektText().trim().takeIf { it.isNotBlank() }
            }
            return listOfNotNull(streckeText, objektText)
                .distinct()
                .joinToString(" · ")
                .ifBlank { aktuellerKontext?.labelKurz ?: (aktuelleBaustellenName ?: "Baustelle-ID: " + baustelleId) }
        }

        val ausgabe = SpannableStringBuilder()
        fun zeile(text: String, faktor: Float = 1.0f, fett: Boolean = false) {
            if (text.isBlank()) return
            val start = ausgabe.length
            ausgabe.append(text)
            val ende = ausgabe.length
            if (faktor != 1.0f) {
                ausgabe.setSpan(RelativeSizeSpan(faktor), start, ende, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (fett) {
                ausgabe.setSpan(StyleSpan(Typeface.BOLD), start, ende, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            ausgabe.append('\n')
        }
        fun anzeigen() {
            if (ausgabe.isNotEmpty() && ausgabe[ausgabe.length - 1] == '\n') {
                ausgabe.delete(ausgabe.length - 1, ausgabe.length)
            }
            textStatus.text = ausgabe
        }

        if (!gpsFuerLiveKmBrauchbar(loc)) {
            zeile("KM —", faktor = 2.05f, fett = true)
            if (loc.hasAccuracy()) {
                zeile("GPS zu ungenau · " + gpsText, faktor = 1.02f, fett = true)
            } else {
                zeile("GPS-Genauigkeit unbekannt", faktor = 1.02f, fett = true)
            }
            anzeigen()
            return
        }

        if (trassen != null) {
            val kmText = trassen.kmMeter?.let {
                "KM " + String.format(Locale.GERMANY, "%.3f", it / 1000.0)
            } ?: "KM fehlt"
            zeile(kmText, faktor = 2.05f, fett = true)
            zeile(streckeObjektZeile(trassen), faktor = 1.08f, fett = true)
            zeile(
                "Achsabstand " + String.format(Locale.GERMANY, "%.1f m", trassen.abstandM) + " · " + gpsText,
                faktor = 0.86f
            )
            anzeigen()
            return
        }

        if (trassenAchsenCache.isNotEmpty() && trassenSamplepunkteCache.isNotEmpty()) {
            zeile("KM —", faktor = 2.05f, fett = true)
            zeile("Keine passende TRA in Reichweite", faktor = 1.02f, fett = true)
            zeile(gpsText, faktor = 0.86f)
            anzeigen()
            return
        }

        val legacySicher = res.km != null &&
            res.achsabstandM != null &&
            res.achsabstandM <= liveKmMaxAbstandM(loc)
        if (legacySicher) {
            zeile(
                "KM " + String.format(Locale.GERMANY, "%.3f", rundeKmAuf10m(res.km ?: 0.0)),
                faktor = 2.05f,
                fett = true
            )
            zeile(aktuellerKontext?.labelKurz ?: "Streckenpunkte", faktor = 1.08f, fett = true)
            zeile(
                "Achsabstand " + String.format(Locale.GERMANY, "%.1f m", res.achsabstandM ?: 0.0) + " · " + gpsText,
                faktor = 0.86f
            )
        } else {
            zeile("KM —", faktor = 2.05f, fett = true)
            zeile("Keine passende TRA in Reichweite", faktor = 1.02f, fett = true)
            zeile(gpsText, faktor = 0.86f)
        }
        anzeigen()
    }

"""
        text = text.substring(0, overlayStart) + neueOverlayFunktion + text.substring(overlayEnd)

        // Der Vermessungstreffer wird exakt zum Aufnahmezeitpunkt persistent zwischengespeichert.
        // Die Foto-Maske darf den vorgeschlagenen KM danach aendern; beim finalen Speichern
        // wird daraus automatisch ORIGINAL oder MANUELL abgeleitet.
        replaceRequired009(
            """                        val trassenTreffer = letzterTrassenTreffer
                        if (trassenTreffer?.kmMeter != null) {
""",
            """                        val trassenTreffer = letzterTrassenTreffer
                        val snapshotStrecke = aktuellerKontext?.streckennummer
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?: trassenTreffer?.strecke?.trim()?.takeIf { it.isNotBlank() }?.let { nummer ->
                                val ri = trassenTreffer.richtung?.trim().orEmpty()
                                if (ri.isNotBlank() && !nummer.endsWith("-" + ri)) nummer + "-" + ri else nummer
                            }
                        val snapshotAchse = trassenTreffer?.let { treffer ->
                            if (treffer.achsTyp == TrassenAchseEntity.ACHSTYP_STRECKE) {
                                snapshotStrecke?.let { "Str " + it } ?: treffer.achseName
                            } else {
                                treffer.kameraObjektText()
                            }
                        }
                        val snapshotKmVorschlag = trassenTreffer?.kmMeter?.div(1000.0)
                            ?: kmRes?.km
                                ?.takeIf { kmRes.indikator != KmBerechner.Indikator.ROT }
                                ?.let(::rundeKmAuf10m)
                        FotoVermessungsSnapshotSpeicher.speicherePending(
                            requireContext().applicationContext,
                            FotoVermessungsSnapshotSpeicher.Pending(
                                baustelleId = baustelleId,
                                gpsLat = standort.latitude,
                                gpsLon = standort.longitude,
                                gpsAccuracyM = if (standort.hasAccuracy()) standort.accuracy.toDouble() else null,
                                kmVorschlag = snapshotKmVorschlag,
                                kmQuelleRoh = trassenTreffer?.kmQuelle,
                                strecke = snapshotStrecke,
                                achseId = trassenTreffer?.achseId,
                                achseName = snapshotAchse,
                                achseTyp = trassenTreffer?.achsTyp,
                                achsabstandM = trassenTreffer?.abstandM ?: kmRes?.achsabstandM
                            )
                        )
                        if (trassenTreffer?.kmMeter != null) {
""",
            "Foto-Vermessungs-Snapshot"
        )

        generatedCamera.parentFile.mkdirs()
        generatedCamera.writeText(text, Charsets.UTF_8)
    }
}

tasks.configureEach {
    if (
        name == "preBuild" ||
        name.startsWith("ksp") ||
        (name.startsWith("compile") && name.endsWith("Kotlin"))
    ) {
        dependsOn(generateKf006ImportFragment)
        dependsOn(generateKm009KameraFragment)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
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

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
}

tasks.register("testClasses") {
    group = "verification"
    description = "Compatibility alias for Java 'testClasses' (runs Android unit tests)."
    dependsOn(tasks.matching { it.name == "testDebugUnitTest" || it.name == "test" })
}
