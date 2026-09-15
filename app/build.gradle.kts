plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

val generatedBtb200043Dir = layout.buildDirectory.dir("generated/source/btb200043")
val generateBtb200043Source = tasks.register("generateBtb200043Source") {
    val sourceFile = layout.projectDirectory.file(
        "src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchFragment.kt"
    )
    inputs.file(sourceFile)
    outputs.dir(generatedBtb200043Dir)

    doLast {
        val source = sourceFile.asFile.readText()
        val anchor = """
                        val firmTitle = firm ?: getString(R.string.btb_company_general)

                        val dlg = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle(getString(R.string.btb_entry_dialog_title, firmTitle))
                            .setView(root)
                            .setNegativeButton(R.string.action_cancel, null)
                            .setPositiveButton(R.string.action_add, null)
                            .create()
""".trimIndent()

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
""".trimIndent()

        val patched = source.replace(anchor, anchor + photoActions)
        check(patched != source) { "Android 200043: BTB-Quelldatei wurde nicht erweitert." }

        val outputFile = generatedBtb200043Dir.get()
            .file("de/tbhelfer/ui/bautagebuch/BtbFragmentGenerated.kt")
            .asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(patched)
    }
}

android {
    namespace = "de.tbhelfer"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.tbhelfer"
        minSdk = 23
        targetSdk = 36
        // Android 200043: Fotoaktionen direkt im manuellen Arbeitseintrag-Dialog.
        versionCode = 200043
        versionName = "1.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        getByName("main") {
            // Der sehr große Legacy-Fragmentquelltext bleibt unverändert im Repository. Für 200043
            // wird beim Build eine identische Kopie mit genau dem Fotoaktions-Hook erzeugt.
            // Dateiname der generierten Kopie ist absichtlich anders, damit nur das Original
            // ausgeschlossen wird und GitHub Desktop nach dem Build sauber bleibt.
            java.exclude("**/BautagebuchFragment.kt")
            java.srcDir(generateBtb200043Source)
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
