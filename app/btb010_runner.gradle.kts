// Kleiner Preflight-Loader fuer den grossen 010-BTB-Generator.
// Der Generator wird als Text vorbereitet und erst danach als Gradle-Script angewendet.
// So koennen wir problematische Kotlin-Stringliterale und fragile AGP-Stellen
// deterministisch korrigieren, ohne den 360-kB-Bautagebuch-Quelltext direkt anzufassen.

val sourceScript = file("btb010.gradle.kts")
check(sourceScript.exists()) { "BTB 010 Generator fehlt: ${sourceScript.path}" }

val literalDollarExpression = "\$" + "{'\$'}"
val sourceText = sourceScript.readText(Charsets.UTF_8)
val oldVersionBlock = """    // VersionCode nur auf Variantenebene ueberschreiben, damit app/build.gradle.kts nicht erneut
    // als grosse Generator-Datei angefasst werden muss.
    val androidComponents = extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
    androidComponents.onVariants { variant ->
        variant.outputs.forEach { output -> output.versionCode.set(200010) }
    }
"""

val fixedText = sourceText
    .replace("\$sourceDate", literalDollarExpression + "sourceDate")
    .replace("\$sourceTyp", literalDollarExpression + "sourceTyp")
    .replace("import com.android.build.api.variant.ApplicationAndroidComponentsExtension\n", "")
    .replace(oldVersionBlock, "    android.defaultConfig.versionCode = 200010\n")
    .replace("android.app.AlertDialog.BUTTON_POSITIVE", "android.content.DialogInterface.BUTTON_POSITIVE")
    .replace("android.app.AlertDialog.BUTTON_NEUTRAL", "android.content.DialogInterface.BUTTON_NEUTRAL")

check(!fixedText.contains("\$sourceDate")) { "BTB 010 Preflight: sourceDate wurde nicht escaped" }
check(!fixedText.contains("\$sourceTyp")) { "BTB 010 Preflight: sourceTyp wurde nicht escaped" }
check(!fixedText.contains("ApplicationAndroidComponentsExtension")) { "BTB 010 Preflight: fragile Variant-API noch vorhanden" }
check(fixedText.contains("android.defaultConfig.versionCode = 200010")) { "BTB 010 Preflight: VersionCode 200010 fehlt" }

val runtimeScript = layout.buildDirectory.file("generated/btb010/btb010_runtime.gradle.kts").get().asFile
runtimeScript.parentFile.mkdirs()
runtimeScript.writeText(fixedText, Charsets.UTF_8)

apply(from = runtimeScript)
