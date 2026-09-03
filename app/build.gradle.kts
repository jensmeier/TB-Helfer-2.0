plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

val kf006OriginalFragment = file("src/main/java/de/tbhelfer/ui/importexport/ImportExportFragment.kt")
val kf006GeneratedDir = layout.buildDirectory.dir("generated/source/kf006").get().asFile
val kf006GeneratedFragment = file("${kf006GeneratedDir.path}/de/tbhelfer/ui/importexport/ImportExportFragment006.kt")

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
        // TB-Helfer 2.0: 200006 -> sichtbare Teststand-Nummer 006.
        // Jeder neue auf dem Handy testbare Stand erhöht diesen Wert um 1.
        versionCode = 200006
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

    // 006: Die grosse ImportExportFragment-Datei bleibt im Repository unveraendert.
    // Fuer den Build wird eine deterministisch gepatchte Kopie erzeugt. AGP 9 mit
    // Built-in Kotlin blendet PatternFilterable direkt auf AndroidSourceDirectorySet aus.
    sourceSets {
        getByName("main") {
            (kotlin as org.gradle.api.tasks.util.PatternFilterable)
                .exclude("de/tbhelfer/ui/importexport/ImportExportFragment.kt")
            kotlin.directories.add(kf006GeneratedDir.path)
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
        var text = kf006OriginalFragment.readText(Charsets.UTF_8)

        fun replaceRequired(old: String, new: String, label: String, all: Boolean = false) {
            if (text.contains(new)) return
            check(text.contains(old)) { "006-Integration fehlgeschlagen: $label nicht gefunden" }
            text = if (all) text.replace(old, new) else text.replaceFirst(old, new)
        }

        replaceRequired(
            "import de.tbhelfer.vermessung.GvpDateiParser\n",
            "import de.tbhelfer.vermessung.GvpDateiParser\nimport de.tbhelfer.vermessung.KfDateiParser\nimport de.tbhelfer.vermessung.VermessungspunktKategorie\n",
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
            "    private fun speichereStreckendatenZuordnung(\n",
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

    private fun speichereStreckendatenZuordnung(
""",
            "KF-Importfunktion"
        )

        replaceRequired(
            "            val result = GvpDateiParser.parse(dateiname, bytes)\n",
            """            val result = if (dateiname.endsWith(".kf", ignoreCase = true) &&
                !dateiname.endsWith(".kf.txt", ignoreCase = true)
            ) {
                KfDateiParser.parse(dateiname, bytes)
            } else {
                GvpDateiParser.parse(dateiname, bytes)
            }
""",
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
            "                        \"GVP\" to \"Festpunkte / Masten\",\n",
            """                        "GVP" to "GVP / Gleisvermarkung (PS4/GVPV)",
                        "FESTPUNKT" to "Festpunkte / sonstige KF-Punkte",
""",
            "Grafik-Anzeigefilter",
            all = true
        )

        kf006GeneratedFragment.parentFile.mkdirs()
        kf006GeneratedFragment.writeText(text, Charsets.UTF_8)
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
