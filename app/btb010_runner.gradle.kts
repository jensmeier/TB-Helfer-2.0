// Kleiner Preflight-Loader fuer den grossen 010-BTB-Generator.
// Der Generator wird als Text vorbereitet und erst danach als Gradle-Script angewendet.
// So korrigieren wir problematische Kotlin-Stringliterale und kleine UX-Details
// deterministisch, ohne den 360-kB-Bautagebuch-Quelltext direkt anzufassen.

val sourceScript = file("btb010.gradle.kts")
check(sourceScript.exists()) { "BTB 010 Generator fehlt: ${sourceScript.path}" }

val literalDollarExpression = "\$" + "{'\$'}"
val sourceText = sourceScript.readText(Charsets.UTF_8)
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

val visibleMarker = "cardNewBtbStartMode.visibility = View.VISIBLE"
check(sourceText.contains(visibleMarker)) { "BTB 010 Preflight: Startkarten-Marker fehlt" }

val fixedText = sourceText
    .replace("\$sourceDate", literalDollarExpression + "sourceDate")
    .replace("\$sourceTyp", literalDollarExpression + "sourceTyp")
    .replace("android.app.AlertDialog.BUTTON_POSITIVE", "android.content.DialogInterface.BUTTON_POSITIVE")
    .replace("android.app.AlertDialog.BUTTON_NEUTRAL", "android.content.DialogInterface.BUTTON_NEUTRAL")
    .replace("private var newBtbEditingCurrent: Boolean = false", setupIsolationCode)
    .replace(
        visibleMarker,
        visibleMarker + "\n                setNewBtbSetupIsolation(true)"
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

check(!fixedText.contains("\$sourceDate")) { "BTB 010 Preflight: sourceDate wurde nicht escaped" }
check(!fixedText.contains("\$sourceTyp")) { "BTB 010 Preflight: sourceTyp wurde nicht escaped" }
check(fixedText.contains("output.versionCode.set(200010)")) { "BTB 010 Preflight: Varianten-VersionCode 200010 fehlt" }
check(fixedText.contains("private fun setNewBtbSetupIsolation(active: Boolean)")) { "BTB 010 Preflight: Setup-Isolation-Funktion fehlt" }
check(fixedText.contains("setNewBtbSetupIsolation(true)")) { "BTB 010 Preflight: Setup-Isolation-Aufruf fehlt" }
check(fixedText.contains("setNewBtbSetupIsolation(false)")) { "BTB 010 Preflight: Setup-Isolation-Rueckstellung fehlt" }
check(fixedText.contains("Daraus wird:")) { "BTB 010 Preflight: Zeitraum-Vorschau fehlt" }
check(fixedText.contains("Aus letztem BTB übernehmen")) { "BTB 010 Preflight: Übernahme-Text fehlt" }

val runtimeScript = layout.buildDirectory.file("generated/btb010/btb010_runtime.gradle.kts").get().asFile
runtimeScript.parentFile.mkdirs()
runtimeScript.writeText(fixedText, Charsets.UTF_8)

apply(from = runtimeScript)
