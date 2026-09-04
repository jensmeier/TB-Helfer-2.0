plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

val kf006OriginalFragment = file("src/main/java/de/tbhelfer/ui/importexport/ImportExportFragment.kt")
val kf006GeneratedDir = layout.buildDirectory.dir("generated/source/kf006").get().asFile
val kf006GeneratedFragment = file("${kf006GeneratedDir.path}/de/tbhelfer/ui/importexport/ImportExportFragment006.kt")

// 007: Die Kamera bekommt fuer den Teststand eine kleine, deterministische Build-Kopie.
// So bleibt die sehr grosse KameraFragment.kt im Repository unangetastet, waehrend wir
// die neue Live-KM-Anzeige und die Sicherheitsgrenze auf dem Handy testen.
val km007OriginalCamera = file("src/main/java/de/tbhelfer/ui/kamera/KameraFragment.kt")
val km007GeneratedDir = layout.buildDirectory.dir("generated/source/km007").get().asFile
val km007GeneratedCamera = file("${km007GeneratedDir.path}/de/tbhelfer/ui/kamera/KameraFragment007.kt")

android {
    namespace = "de.tbhelfer"
    // AGP 9 unterstützt bis API 36 (Android 16). Einige AndroidX-Versionen
    // (z.B. core-ktx 1.17.0) verlangen compileSdk 36.
    compileSdk = 36

    defaultConfig {
        applicationId = "de.tbhelfer"
        // CameraX 1.5.x (insb. camera-video) fordert minSdk 23.
        // Wenn du minSdk 21 behalten MUSST, siehe REPORT (Downgrade/Exclude-Option).
        minSdk = 23

        // targetSdk kann separat von compileSdk entschieden werden.
        // Für die "AGP9-Migration - alles aktuell" Variante setzen wir hier 36.
        targetSdk = 36
        // TB-Helfer 2.0: 200007 -> sichtbare Teststand-Nummer 007.
        // Jeder neue auf dem Handy testbare Stand erhöht diesen Wert um 1.
        versionCode = 200007
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // HIGH-IMPACT: Release kleiner + schneller, aber trotzdem safe (Room/DataStore/CameraX sind i.d.R. ok)
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

    // 006/007: Die grossen Original-Fragmente bleiben im Repository unveraendert.
    // Fuer den Build werden deterministisch gepatchte Kopien erzeugt. AGP 9 mit
    // Built-in Kotlin blendet PatternFilterable direkt auf AndroidSourceDirectorySet aus.
    sourceSets {
        getByName("main") {
            (kotlin as org.gradle.api.tasks.util.PatternFilterable)
                .exclude("de/tbhelfer/ui/importexport/ImportExportFragment.kt")
                .exclude("de/tbhelfer/ui/kamera/KameraFragment.kt")
            kotlin.directories.add(kf006GeneratedDir.path)
            kotlin.directories.add(km007GeneratedDir.path)
        }
    }

    // Verhindert gelegentliche META-INF Doppel-Einträge (klassischer Gradle/AndroidX Painpoint)
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // Runde 3: "UI/Lint auf 0 ohne Noise"
        // -> wir nutzen eine Lint-Config (root/lint.xml), die reine Noise-Checks deaktiviert.
        //    So bleiben relevante Checks aktiv, aber die IDE/CI wird nicht zugemüllt.
        abortOnError = true
        checkReleaseBuilds = true
        checkDependencies = true
        lintConfig = file("$rootDir/lint.xml")
    }
}

val generateKf006ImportFragment by tasks.registering {
    group = "build setup"
    description = "Erzeugt ImportExportFragment fuer TB-Helfer 2.0 (006) mit direktem KF-Import."
    inputs.file(kf006OriginalFragment)
    outputs.file(kf006GeneratedFragment)

    doLast {
        // Nur Task-eigene Inputs/Outputs verwenden. Direkte Zugriffe auf Properties
        // der kompilierten Kotlin-DSL-Scriptklasse koennen unter AGP/Gradle 9 beim
        // Ausfuehren der Task-Action mit einem null this$0 enden.
        val originalFragment = inputs.files.singleFile
        val generatedFragment = outputs.files.singleFile
        // Windows-Checkouts koennen die Quelldatei als CRLF ausliefern. Die 006-Patches
        // arbeiten absichtlich auf normalisierten LF-Zeilen, damit mehrzeilige Muster
        // unter Windows und Linux identisch funktionieren.
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

val generateKm007KameraFragment by tasks.registering {
    group = "build setup"
    description = "Erzeugt KameraFragment fuer Teststand 007 mit grosser Live-KM-Anzeige."
    inputs.file(km007OriginalCamera)
    outputs.file(km007GeneratedCamera)

    doLast {
        val originalCamera = inputs.files.singleFile
        val generatedCamera = outputs.files.singleFile
        var text = originalCamera.readText(Charsets.UTF_8)
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        fun replaceRequired007(old: String, new: String, label: String) {
            if (text.contains(new)) return
            check(text.contains(old)) { "007-Kamera-Integration fehlgeschlagen: $label nicht gefunden" }
            text = text.replaceFirst(old, new)
        }

        replaceRequired007(
            "import android.graphics.Color",
            """import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan""",
            "Live-KM-Text-Imports"
        )

        // Die sichtbare Fotoqualitaets-Einstellung muss die CameraX-Aufnahme wirklich steuern.
        replaceRequired007(
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
        replaceRequired007(
            """        val naechsteLinie = linien.minByOrNull { it.abstandM } ?: return null
        if (letztesStreckenObjektId != objekt.id) {
""",
            """        val naechsteLinie = linien.minByOrNull { it.abstandM } ?: return null
        if (naechsteLinie.abstandM > liveKmMaxAbstandM(loc)) return null
        if (letztesStreckenObjektId != objekt.id) {
""",
            "Weichen-Live-Distanzgrenze"
        )

        replaceRequired007(
            "    private fun berechneTrassenTreffer(loc: Location): TrassenNaheBerechner.Ergebnis? {",
            """    private fun liveKmMaxAbstandM(loc: Location): Double {
        // Sicherheitsregel fuer die Live-Anzeige: Standard 25 m. Bei schlechterem
        // Handy-GPS etwas toleranter, aber niemals mehr als 40 m. Ein weiter entferntes
        // Gleis darf keinen vermeintlich exakten Bahn-km liefern.
        val gpsGenauigkeit = if (loc.hasAccuracy()) loc.accuracy.toDouble().coerceAtLeast(1.0) else 10.0
        return maxOf(25.0, gpsGenauigkeit * 2.0).coerceAtMost(40.0)
    }

    private fun berechneTrassenTreffer(loc: Location): TrassenNaheBerechner.Ergebnis? {""",
            "Live-KM-Distanzfunktion"
        )

        replaceRequired007(
            "            maxAbstandM = if (aktuellerKontext == null) 250.0 else 500.0,",
            "            maxAbstandM = liveKmMaxAbstandM(loc),",
            "TRA-Live-Distanzgrenze"
        )

        val overlayStartMarker =
            "    private fun aktualisiereOverlayText(baustelleId: Long, loc: Location, res: KmBerechner.Ergebnis) {"
        val overlayEndMarker =
            "    private fun TrassenNaheBerechner.Ergebnis.toKameraKurzzeile(): String {"
        val overlayStart = text.indexOf(overlayStartMarker)
        val overlayEnd = text.indexOf(overlayEndMarker, overlayStart.coerceAtLeast(0))
        check(overlayStart >= 0 && overlayEnd > overlayStart) {
            "007-Kamera-Integration fehlgeschlagen: Live-KM-Anzeigefunktion nicht gefunden"
        }

        val neueOverlayFunktion = """    private fun aktualisiereOverlayText(baustelleId: Long, loc: Location, res: KmBerechner.Ergebnis) {
        val trassen = letzterTrassenTreffer
        val gpsText = if (loc.hasAccuracy()) {
            "GPS ±" + String.format(Locale.GERMANY, "%.1f m", loc.accuracy)
        } else {
            "GPS-Genauigkeit -"
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

        // Legacy-Fallback nur dann anzeigen, wenn auch dessen Achsabstand innerhalb
        // derselben Sicherheitsgrenze liegt. Sonst bleibt der KM bewusst leer.
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

        generatedCamera.parentFile.mkdirs()
        generatedCamera.writeText(text, Charsets.UTF_8)
    }
}

// preBuild ist in den Android-Taskgraphen enthalten; die zusaetzlichen Abhaengigkeiten
// decken direkte KSP-/Kotlin-Aufrufe aus Android Studio ebenfalls ab.
tasks.configureEach {
    if (
        name == "preBuild" ||
        name.startsWith("ksp") ||
        (name.startsWith("compile") && name.endsWith("Kotlin"))
    ) {
        dependsOn(generateKf006ImportFragment)
        dependsOn(generateKm007KameraFragment)
    }
}

// Built-in Kotlin (AGP 9) übernimmt den JVM Target standardmäßig aus
// android.compileOptions.targetCompatibility.

// HIGH-IMPACT: Room Schema-Export (hilft bei Migrationen & Tests; kostet im Build praktisch nichts)
// Ordner: app/schemas/
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

    // Einstellungen / Key-Value
    implementation(libs.androidx.datastore.preferences)

    // Hintergrundaufgaben (Speicher-Cleanup / Orphan-Checks)
    implementation(libs.androidx.work.runtime.ktx)

    // Room Datenbank (KSP)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Kamera (CameraX)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.extensions)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
}

// -----------------------------------------------------------------------------
// Compatibility task
// -----------------------------------------------------------------------------
// Some IDE/CI templates (especially from pure Java projects) call `:app:testClasses`.
// The Android Gradle Plugin does not create that task, so we provide it as an alias
// that forwards to the most common Android unit-test task.
tasks.register("testClasses") {
    group = "verification"
    description = "Compatibility alias for Java 'testClasses' (runs Android unit tests)."
    // Prefer unit tests; if none exist, this will simply have no dependencies.
    dependsOn(tasks.matching { it.name == "testDebugUnitTest" || it.name == "test" })
}
