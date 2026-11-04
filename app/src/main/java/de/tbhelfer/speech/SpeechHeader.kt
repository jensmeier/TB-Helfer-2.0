package de.tbhelfer.speech

import java.util.Locale

/** Consumes only a leading header. The first unknown phrase ends parsing; no body-wide deletion. */
object SpeechHeader {
    private fun rx(s: String) = Regex(s, RegexOption.IGNORE_CASE)
    private val sectionWords = linkedMapOf(
        4 to "(?:ausgeführte\\s+)?arbeiten?",
        5 to "abnahmen?",
        6 to "(?:einweisung|unterweisung)(?:en)?(?:\\s*[/]\\s*(?:einweisung|unterweisung)(?:en)?)?",
        7 to "(?:besonderheiten?|besondenheiten|besonderheiden)",
        8 to "(?:mängel|maengel|mangel|schäden|schaeden|schaden)(?:\\s*[/]\\s*(?:schäden|schaeden|schaden))?"
    )
    private val point = rx("punkt\\s+(4|vier|5|fünf|funf|6|sechs|7|sieben|8|acht)\\b")
    private val number = "[+-]?\\d+(?:[.,]\\d+)*"
    private val km = rx("(?:kilometer|km)\\s*($number)(?:\\s*(?:bis|[–—-])\\s*(?:(?:kilometer|km)\\s*)?($number))?(?![\\d.,])")
    private val clock = "(?:\\d{1,2}:\\d{2}(?:\\s*uhr)?|\\d{1,2}\\s*uhr(?:\\s+\\d{2}(?!\\d))?)"
    private val time = rx("(?:von\\s+)?($clock)(?:\\s*(?:bis|[–—-])\\s*($clock))?(?!\\d)")
    private val shortTime = rx("(?:von\\s+)?(\\d{1,2})\\s*(?:bis|[–—-])\\s*(\\d{1,2})\\s*uhr\\b")
    private fun trim(s: String) = s.trimStart { it.isWhitespace() || it in ",;:" }

    fun leadingSection(text: String): Pair<Int, Int>? {
        point.find(text)?.takeIf { it.range.first == 0 }?.let {
            val id = when(it.groupValues[1].lowercase(Locale.GERMAN)) {
                "4", "vier" -> 4; "5", "fünf", "funf" -> 5; "6", "sechs" -> 6; "7", "sieben" -> 7; else -> 8
            }
            return id to it.value.length
        }
        return sectionWords.entries.firstNotNullOfOrNull { (id, pattern) ->
            rx("(?:$pattern)\\b").find(text)?.takeIf { it.range.first == 0 }?.let { id to it.value.length }
        }
    }

    fun clockValue(raw: String): String? {
        val m = rx("(\\d{1,2})(?::(\\d{2})|\\s*uhr(?:\\s+(\\d{2}))?)?(?:\\s*uhr)?").matchEntire(raw.trim()) ?: return null
        val h=m.groupValues[1].toIntOrNull() ?: return null
        val minutes=(m.groupValues[2].ifEmpty { m.groupValues[3] }).ifEmpty { "0" }.toIntOrNull() ?: return null
        return if(h in 0..23 && minutes in 0..59) String.format(Locale.ROOT,"%02d:%02d",h,minutes) else null
    }
    fun kmValue(raw: String): Double? {
        if(!Regex("[+-]?\\d+(?:[.,]\\d{1,3})?").matches(raw.trim()))return null
        return raw.trim().replace(',','.').toDoubleOrNull()?.takeIf { it.isFinite() }
    }
    private fun kmText(raw: String) = kmValue(raw)?.let { String.format(Locale.ROOT,"%.3f",it).replace('.',',') }
    fun validation(d: SpeechDraft): String? {
        if(listOf(d.timeFrom,d.timeTo).any { it.isNotBlank() && clockValue(it)==null })return "Uhrzeit bitte als HH:mm prüfen (00:00–23:59)."
        if(listOf(d.kmFrom,d.kmTo).any { it.isNotBlank() && kmValue(it)==null })return "Kilometer bitte als Zahl mit höchstens drei Nachkommastellen prüfen."
        if(d.timeFrom.isBlank() && d.timeTo.isNotBlank())return "Bitte Beginn ergänzen oder Ende leeren."
        if(d.kmFrom.isBlank() && d.kmTo.isNotBlank())return "Bitte KM von ergänzen oder KM bis leeren."
        return null
    }

    fun prepare(d: SpeechDraft, firms: List<Pair<String,String>>, aliases: List<Pair<String,String>> = emptyList()): SpeechDraft {
        if(d.fieldsParsed)return d
        var rest=trim(d.text)
        var section: Int?=null
        var firm=d.firmId
        var from="";var to="";var kmFrom="";var kmTo="";var notice=""
        var firmSeen=false
        while(rest.isNotBlank()) {
            val marker=rx("text\\s*:").find(rest)?.takeIf { it.range.first==0 }
                ?: rx("text\\s+").find(rest)?.takeIf { it.range.first==0 }
            if(marker!=null){rest=rest.substring(marker.value.length).trimStart();break}
            if(rest.startsWith("Firma ",true) && !firmSeen) {
                // Find a unique known company at the head; never remove arbitrary name fragments.
                val body=rest.substring(6)
                val tokens=Regex("\\S+").findAll(body).take(6).toList()
                val candidates=tokens.mapNotNull { token ->
                    val heard=body.substring(0,token.range.last+1).trimEnd(',', ';', ':')
                    val match=SpeechMatching.firm(heard,firms,aliases)
                    // A match inside a larger phrase is insufficient for deleting that phrase.
                    match?.takeIf { SpeechMatching.normalize(it.heard)==SpeechMatching.normalize(heard) }?.let { it.id to token.range.last+1 }
                }
                val selected=candidates.map { it.first }.distinct().singleOrNull()
                val match=candidates.filter { it.first==selected }.maxByOrNull { it.second }
                if(match==null){notice="Firma in der Kopfzeile nicht eindeutig erkannt; Originaltext prüfen.";break}
                firm=match.first;firmSeen=true;rest=trim(body.substring(match.second));continue
            }
            val s=leadingSection(rest)
            if(s!=null) {
                if(section!=null && section!=s.first){notice="Mehrere BTB-Punkte genannt; Abschnitt ausdrücklich auswählen.";section=null;break}
                section=s.first;rest=trim(rest.substring(s.second));continue
            }
            val k=km.find(rest)?.takeIf { it.range.first==0 }
            if(k!=null) {
                val a=kmText(k.groupValues[1]);val b=k.groupValues[2].takeIf { it.isNotEmpty() }?.let { kmText(it) }
                if(k.groupValues.drop(1).any { Regex("[+-]?\\d{4,}").matches(it) } || kmFrom.isNotEmpty() || a==null || (k.groupValues[2].isNotEmpty() && b==null)) {notice="KM-Angabe nicht eindeutig; verbliebenen Text und Original prüfen.";break}
                val tail=trim(rest.substring(k.value.length))
                if(rx("^(?:bis\\b|[–—-]|\\d)").containsMatchIn(tail)){notice="KM-Bereich unvollständig; Original prüfen.";break}
                kmFrom=a;kmTo=b.orEmpty();rest=tail;continue
            }
            val tm=time.find(rest)?.takeIf { it.range.first==0 } ?: shortTime.find(rest)?.takeIf { it.range.first==0 }
            if(tm!=null) {
                val a=clockValue(tm.groupValues[1]);val b=tm.groupValues[2].takeIf { it.isNotEmpty() }?.let { clockValue(it) }
                if(from.isNotEmpty() || a==null || (tm.groupValues[2].isNotEmpty() && b==null)){notice="Zeitangabe nicht eindeutig; verbliebenen Text und Original prüfen.";break}
                val tail=trim(rest.substring(tm.value.length))
                if(rx("^(?:bis\\b|[–—-])").containsMatchIn(tail)){notice="Zeitbereich unvollständig; Original prüfen.";break}
                from=a;to=b.orEmpty();rest=tail;continue
            }
            if(rx("^(?:kilometer|km|von\\s+\\d|\\d+\\s*uhr)\\b").containsMatchIn(rest))notice="Kopfzeile nicht vollständig erkannt; verbliebenen Text und Original prüfen."
            break
        }
        return d.copy(text=rest,originalText=d.originalText ?: d.text,fieldsParsed=true,
            section=if(notice.startsWith("Mehrere BTB"))null else section ?: d.section,
            firmId=firm,timeFrom=from,timeTo=to,kmFrom=kmFrom,kmTo=kmTo,parseNotice=notice)
    }
}
