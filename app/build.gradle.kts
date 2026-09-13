plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

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
        // Android 200027: BTB-Foto-Isolation, rote Original-Abweichungen und sichtbare Löschungen.
        versionCode = 200027
        versionName = "1.3"

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

// Built-in Kotlin (AGP 9) übernimmt den JVM Target standardmäßig aus
// android.compileOptions.targetCompatibility.

// HIGH-IMPACT: Room Schema-Export (hilft bei Migrationen & Tests; kostet im Build praktisch nichts)
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

    // Geräteaktivierung / Baustelleneinladung per QR-Code.
    implementation(libs.play.services.code.scanner)
    // QR-Erzeugung bleibt lokal auf dem Gerät; der Einladungscode verlässt die App nur beim Teilen.
    implementation(libs.zxing.core)

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
