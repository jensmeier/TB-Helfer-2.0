import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

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
                        // Android 200043: Fotoquelle direkt im Dialog „Eintrag für <Firma>“.
                        // Der bestehende Kamera-/Galeriepfad wird wiederverwendet; kein zweiter Foto- oder Overlaypfad.
                        if (fotoType == de.tbhelfer.datenbank.Bildtyp.FOTODOKUMENTATION) {
                            BtbEntryPhotoActionBinder.bind(
                                fragment = this@BautagebuchFragment,
                                container = root,
                                entryDialog = dlg,
                                sectionDialog = dialog,
                                firma = firm,
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
                                        draft.kmBis
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

        // Android 200047: Firmen-Eintraege in der Hauptbaustelle brauchen ebenfalls einen
        // expliziten Ortsmarker. Ohne @ORT=0 konnte ein vorheriger Unterbaustellenmarker
        // (z. B. Schacht 1) auf die folgende Hauptbaustellen-Zeile durchrutschen.
        val mainSiteMarkerOld = "if (effectiveOrtId != 0L) append(\"@ORT=\").append(effectiveOrtId).append(\" \")"
        val mainSiteMarkerNew = "if (f != null) append(\"@ORT=\").append(effectiveOrtId).append(\" \")"
        check(patched.contains(mainSiteMarkerOld)) {
            "Android 200047: Hauptbaustellen-Ortsmarker-Anker wurde nicht gefunden."
        }
        patched = patched.replace(mainSiteMarkerOld, mainSiteMarkerNew)

        // Bestehende Daten reparieren, ohne alte Standalone-@ORT-Gruppen zu zerstoeren:
        // ein moderner Inline-@ORT gilt nur fuer diese eine Zeile; nur ein eigener
        // @ORT=<id>-Datensatz bleibt als historischer Gruppenmarker kleben.
        val ortStateOld = "var currentOrtId: Long = 0L"
        val ortStateNew = "var currentOrtId: Long = 0L\n                    var currentOrtIstStandaloneMarker: Boolean = false"
        check(patched.contains(ortStateOld)) {
            "Android 200047: Ortsparser-Statusanker wurde nicht gefunden."
        }
        patched = patched.replace(ortStateOld, ortStateNew, ignoreCase = false)

        val ortLineOld = """
                        var line = line0.trim()
                        if (line.isBlank()) return@forEachIndexed

                        // Ort-Marker: entweder eigene Zeile oder Inline-Prefix
                        val mo = reOrt.matchEntire(line)
                        if (mo != null) {
                            currentOrtId = mo.groupValues[1].toLongOrNull() ?: 0L
                            line = mo.groupValues.getOrNull(2).orEmpty().trim()
                            if (line.isBlank()) return@forEachIndexed
                        }
""".trim('\n')
        val ortLineNew = """
                        var line = line0.trim()
                        if (line.isBlank()) return@forEachIndexed
                        var lineOrtId: Long? = null

                        // Ort-Marker: entweder eigene Legacy-Gruppenzeile oder moderner Inline-Prefix.
                        // Inline darf nicht auf die naechste Zeile weiterwirken.
                        val mo = reOrt.matchEntire(line)
                        if (mo != null) {
                            val markerOrtId = mo.groupValues[1].toLongOrNull() ?: 0L
                            line = mo.groupValues.getOrNull(2).orEmpty().trim()
                            if (line.isBlank()) {
                                currentOrtId = markerOrtId
                                currentOrtIstStandaloneMarker = true
                                return@forEachIndexed
                            }
                            lineOrtId = markerOrtId
                            currentOrtIstStandaloneMarker = false
                        }
""".trim('\n')
        check(patched.contains(ortLineOld)) {
            "Android 200047: Ortsparser-Inlineanker wurde nicht gefunden."
        }
        patched = patched.replace(ortLineOld, ortLineNew)

        val effectiveOrtOld = "val effectiveOrtId = if (forcedGeneral || firma == null) 0L else currentOrtId"
        val effectiveOrtNew = "val effectiveOrtId = if (forcedGeneral || firma == null) 0L else lineOrtId ?: if (currentOrtIstStandaloneMarker) currentOrtId else 0L"
        check(patched.contains(effectiveOrtOld)) {
            "Android 200047: effektiver Ortsparser-Anker wurde nicht gefunden."
        }
        patched = patched.replace(effectiveOrtOld, effectiveOrtNew)

        // Android 200047: Beim normalen Oeffnen ist die aktive btbId die Identitaet.
        // Nicht erneut nur 'heute TAG/NACHT' durchsuchen: das verlor einen Nacht-BTB,
        // der vor Mitternacht begonnen hat, nach 00:00 aus dem sichtbaren Kontext.
        val activeLoadOld = """
            val entry = withContext(Dispatchers.IO) {
                if (!autoPickSchichtForToday) {
                    BautagebuchSpeicher.laden(requireContext(), ctx.id, ctx.name, selectedDate, selectedSchichtTyp)
                } else {
                    // Heute: beide Schichten prüfen und den zuletzt geänderten Entwurf öffnen
                    val tag = BautagebuchSpeicher.laden(requireContext(), ctx.id, ctx.name, selectedDate, SchichtTyp.TAG)
                    val nacht = BautagebuchSpeicher.laden(requireContext(), ctx.id, ctx.name, selectedDate, SchichtTyp.NACHT)
                    listOfNotNull(tag, nacht).maxByOrNull { it.geaendertAmEpochMs }
                }
            }
""".trim('\n')
        val activeLoadNew = """
            val entry = withContext(Dispatchers.IO) {
                if (autoPickSchichtForToday) {
                    // Aktiver Pointer -> btbId ist kanonisch, auch ueber 23:59/00:00 hinweg.
                    BtbActiveContext.laden(requireContext(), ctx.id)?.entry
                } else {
                    BautagebuchSpeicher.laden(requireContext(), ctx.id, ctx.name, selectedDate, selectedSchichtTyp)
                }
            }
""".trim('\n')
        check(patched.contains(activeLoadOld)) {
            "Android 200047: aktiver-BTB-Ladeanker wurde nicht gefunden."
        }
        patched = patched.replace(activeLoadOld, activeLoadNew)

        // Wenn ein aktiver Nacht-BTB nach Mitternacht geladen wird, muss auch das UI-Datum
        // wieder das Start-/BTB-Datum dieses Datensatzes tragen; sonst koennte der naechste
        // Autosave denselben btbId-Datensatz faelschlich unter dem Folgetag aufbauen.
        val activeDateOld = """
                currentBtbStartEpochMs = entry.erstelltAmEpochMs
                selectedSchichtTyp = entry.schicht
""".trim('\n')
        val activeDateNew = """
                currentBtbStartEpochMs = entry.erstelltAmEpochMs
                runCatching { LocalDate.parse(entry.datumIso, isoFmt) }.getOrNull()?.let { selectedDate = it }
                selectedSchichtTyp = entry.schicht
""".trim('\n')
        check(patched.contains(activeDateOld)) {
            "Android 200047: BTB-Datumsanker wurde nicht gefunden."
        }
        patched = patched.replace(activeDateOld, activeDateNew)

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
        // Android 200048: beschrifteter Bilder-Button + Roh-KF/GVP-KM auch ohne GRA.
        versionCode = 200048
        versionName = "1.3"
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
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { buildConfig = false }

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
    doLast {
        val kspRoot = project.layout.buildDirectory.dir("generated/ksp").get().asFile
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
    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
}

tasks.register("testClasses") {
    group = "verification"
    description = "Compatibility alias for Java 'testClasses' (runs Android unit tests)."
    dependsOn(tasks.matching { it.name == "testDebugUnitTest" || it.name == "test" })
}