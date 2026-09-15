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

        val source = target.readText()
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

        val patched = source.replace(anchor, anchor + "\n\n" + photoActions)
        check(patched != source) { "Android 200043: BTB-Quelldatei wurde nicht erweitert." }
        target.writeText(patched)

        // Android 200044: Room/KSP muss die kanonischen Datenbankquellen als normalen
        // Android-Kotlin-Source-Set sehen. Die Build-Kopie darf diese Klassen deshalb nicht
        // ein zweites Mal enthalten; sonst gäbe es doppelte Klassen. Der Datenbank-Ordner
        // wird unten separat aus src/main/java eingebunden und von KSP verarbeitet.
        val generatedDatabaseDir = outputRoot.resolve("de/tbhelfer/datenbank")
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
        // Android 200045: kompakte, theme-faehige Bildauswahl im BTB-Eintrag.
        versionCode = 200045
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
