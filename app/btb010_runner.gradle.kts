// Preflight-Loader fuer den 010-BTB-Generator.
// Das angewendete Kotlin-Script darf keine AGP-Typen direkt importieren, weil
// apply(from=...) in Android Studio dafuer keinen stabilen typisierten Script-Classpath liefert.
// Android sourceSets/versionCode werden deshalb ueber Gradles GroovyBuilder konfiguriert.

val sourceScript = file("btb010.gradle.kts")
check(sourceScript.exists()) { "BTB 010 Generator fehlt: ${sourceScript.path}" }

val sourceDateToken = "\$sourceDate"
val sourceTypToken = "\$sourceTyp"
val literalDollarExpression = "\$" + "{'\$'}"
var fixedText = sourceScript.readText(Charsets.UTF_8)
    .replace("\r\n", "\n")
    .replace('\r', '\n')
    .replace("import com.android.build.api.dsl.ApplicationExtension\n", "")
    .replace("import com.android.build.api.variant.ApplicationAndroidComponentsExtension\n", "")
    .replace("import org.gradle.api.tasks.util.PatternFilterable\n", "")
    .replace(sourceDateToken, literalDollarExpression + "sourceDate")
    .replace(sourceTypToken, literalDollarExpression + "sourceTyp")
    .replace("android.app.AlertDialog.BUTTON_POSITIVE", "android.content.DialogInterface.BUTTON_POSITIVE")
    .replace("android.app.AlertDialog.BUTTON_NEUTRAL", "android.content.DialogInterface.BUTTON_NEUTRAL")

// Setup-Fenster isolieren: waehrend Datum/Personal/Maschinen/Wetter bearbeitet werden,
// sind die eigentlichen BTB-Punkte darunter ausgeblendet.
val setupIsolationCode = """private var newBtbEditingCurrent: Boolean = false
private val newBtbSetupSiblingVisibility = linkedMapOf<View, Int>()

private fun setNewBtbSetupIsolation(active: Boolean) {
    val parent = cardNewBtbStartMode.parent as? ViewGroup ?: return
    val cardIndex = parent.indexOfChild(cardNewBtbStartMode)
    if (cardIndex < 0) return
    if (active) {
        if (newBtbSetupSiblingVisibility.isNotEmpty()) return
        for (i in (cardIndex + 1) until parent.childCount) {
            val child = parent.getChildAt(i)
            newBtbSetupSiblingVisibility[child] = child.visibility
            child.visibility = View.GONE
        }
    } else {
        newBtbSetupSiblingVisibility.forEach { (child, visibility) ->
            if (child.parent === parent) child.visibility = visibility
        }
        newBtbSetupSiblingVisibility.clear()
    }
}"""
fixedText = fixedText
    .replace("private var newBtbEditingCurrent: Boolean = false", setupIsolationCode)
    .replace(
        "cardNewBtbStartMode.visibility = View.VISIBLE",
        "cardNewBtbStartMode.visibility = View.VISIBLE\n                setNewBtbSetupIsolation(true)"
    )
    .replace(
        "            cardNewBtbStartMode.visibility = View.GONE",
        "            setNewBtbSetupIsolation(false)\n            cardNewBtbStartMode.visibility = View.GONE"
    )
    .replace(
        "tvNewBtbDateTimeProposal.text = formatBtbZeitraum(effectiveDate, effectiveStart, effectiveEnd)",
        "tvNewBtbDateTimeProposal.text = \"Daraus wird:\\n\" + formatBtbZeitraum(effectiveDate, effectiveStart, effectiveEnd)"
    )
    .replace(
        "        generated.parentFile.mkdirs()",
        "        text = text.replace(\"Vortag übernehmen\", \"Aus letztem BTB übernehmen\")\n        generated.parentFile.mkdirs()"
    )

// Den typisierten AGP-Block am Ende durch eine dynamische, fuer applied scripts robuste
// Konfiguration ersetzen. Der Generator-Task selbst bleibt unveraendert.
val androidBlockStart = fixedText.indexOf("\npluginManager.withPlugin(\"com.android.application\") {")
val taskBlockStart = fixedText.indexOf("\ntasks.configureEach {", androidBlockStart.coerceAtLeast(0))
check(androidBlockStart >= 0 && taskBlockStart > androidBlockStart) {
    "BTB 010 Preflight: alter Android-Konfigurationsblock nicht gefunden"
}
val dynamicAndroidBlock = """

plugins.withId("com.android.application") {
    extensions.getByName("android").withGroovyBuilder {
        "defaultConfig" {
            setProperty("versionCode", 200010)
        }
        "sourceSets" {
            "main" {
                "kotlin" {
                    "exclude"("de/tbhelfer/ui/bautagebuch/BautagebuchFragment.kt")
                    "srcDir"(btb010GeneratedDir.path)
                }
            }
        }
    }
}
"""
fixedText = fixedText.substring(0, androidBlockStart) + dynamicAndroidBlock + fixedText.substring(taskBlockStart)
fixedText = "import org.gradle.kotlin.dsl.withGroovyBuilder\n" + fixedText

check(!fixedText.contains("com.android.build.api")) { "BTB 010 Preflight: AGP-Typimport noch vorhanden" }
check(!fixedText.contains("ApplicationExtension")) { "BTB 010 Preflight: ApplicationExtension noch vorhanden" }
check(!fixedText.contains("ApplicationAndroidComponentsExtension")) { "BTB 010 Preflight: Variant-API noch vorhanden" }
check(fixedText.contains("setProperty(\"versionCode\", 200010)")) { "BTB 010 Preflight: VersionCode 200010 fehlt" }
check(fixedText.contains("setNewBtbSetupIsolation(true)")) { "BTB 010 Preflight: Setup-Isolation fehlt" }
check(fixedText.contains("Daraus wird:")) { "BTB 010 Preflight: Zeitraum-Vorschau fehlt" }

val runtimeScript = layout.buildDirectory.file("generated/btb010/btb010_runtime.gradle.kts").get().asFile
runtimeScript.parentFile.mkdirs()
runtimeScript.writeText(fixedText, Charsets.UTF_8)

apply(from = runtimeScript)
