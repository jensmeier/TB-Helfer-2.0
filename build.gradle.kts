plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
}

project(":app") {
    apply(from = rootProject.file("app/btb010_runner.gradle.kts"))
}
