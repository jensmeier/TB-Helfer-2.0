// Kleiner Preflight-Loader fuer den grossen 010-BTB-Generator.
// Der Generator wird als Text vorbereitet und erst danach als Gradle-Script angewendet.
// So koennen wir problematische Kotlin-Stringliterale deterministisch korrigieren,
// ohne den 360-kB-Bautagebuch-Quelltext direkt anzufassen.

val sourceScript = file("btb010.gradle.kts")
check(sourceScript.exists()) { "BTB 010 Generator fehlt: ${sourceScript.path}" }

val literalDollarExpression = "\$" + "{'\$'}"
val sourceText = sourceScript.readText(Charsets.UTF_8)
val fixedText = sourceText
    .replace("\$sourceDate", literalDollarExpression + "sourceDate")
    .replace("\$sourceTyp", literalDollarExpression + "sourceTyp")

check(!fixedText.contains("\$sourceDate")) { "BTB 010 Preflight: sourceDate wurde nicht escaped" }
check(!fixedText.contains("\$sourceTyp")) { "BTB 010 Preflight: sourceTyp wurde nicht escaped" }

val runtimeScript = layout.buildDirectory.file("generated/btb010/btb010_runtime.gradle.kts").get().asFile
runtimeScript.parentFile.mkdirs()
runtimeScript.writeText(fixedText, Charsets.UTF_8)

apply(from = runtimeScript)
