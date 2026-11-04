package de.tbhelfer.speech

import java.text.Normalizer
import java.util.Locale

/** Local, conservative proposals. Never rewrites the user's description. */
object SpeechMatching {
    fun normalize(s: String): String = Normalizer.normalize(s.lowercase(Locale.GERMAN), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace("ß", "ss").replace(Regex("[^a-z0-9]+"), " ").trim()
    private fun contains(text: String, phrase: String) = phrase.isNotBlank() && " $text ".contains(" $phrase ")
    private val words = linkedMapOf(
        4 to listOf("arbeiten", "arbeit", "schwellenwechsel", "schwellen gewechselt", "schwellen getauscht", "einschottern", "einschotter", "weichenmontage"),
        5 to listOf("abnahme", "abnahmen"), 6 to listOf("einweisung", "unterweisung", "einweisungen", "unterweisungen"),
        7 to listOf("besonderheit", "besonderheiten", "besondenheiten", "besonderheiden"), 8 to listOf("mangel", "mängel", "schaden", "schäden", "beschädigung", "beschädigungen"))
    fun section(text: String): Int? {
        val n=normalize(text)
        val explicit=mapOf(4 to "vier",5 to "funf",6 to "sechs",7 to "sieben",8 to "acht")
            .filter { (id,word) -> contains(n,"punkt $id") || contains(n,"punkt $word") }.keys
        if(explicit.isNotEmpty()) return explicit.singleOrNull()
        return words.filterValues { values -> values.any { contains(n,normalize(it)) } }.keys.singleOrNull()
    }
    data class FirmMatch(val id: String, val heard: String)
    fun firm(text: String, firms: List<Pair<String,String>>, aliases: List<Pair<String,String>> = emptyList()): FirmMatch? {
        val n=normalize(text)
        val ids=firms.map { it.first }.toSet()
        val exact=(firms+aliases.filter { it.first in ids }).filter { it.second.isNotBlank() && contains(n,normalize(it.second)) }
        if(exact.isNotEmpty()) return exact.map { it.first }.distinct().singleOrNull()?.let { id -> FirmMatch(id,exact.first { it.first==id }.second) }
        val tokens=n.split(" ").filter { it.isNotBlank() }
        val matches=mutableListOf<Pair<Int,FirmMatch>>()
        firms.filter { it.first.isNotBlank() }.forEach { (id,name) ->
            val target=normalize(name).replace(" ","")
            if(target.length<5) return@forEach
            for(size in 1..minOf(4,tokens.size)) for(start in 0..tokens.size-size) {
                val heard=tokens.subList(start,start+size).joinToString(" ")
                val candidate=heard.replace(" ","")
                val limit=if(target.length>=8)2 else 1
                if(kotlin.math.abs(candidate.length-target.length)>limit)continue
                if(candidate!=target && (candidate.startsWith(target) || target.startsWith(candidate)))continue
                val d=distance(candidate,target)
                if(d<=limit)matches.add(d to FirmMatch(id,heard))
            }
        }
        val best=matches.minOfOrNull { it.first } ?: return null
        // Near-tied companies remain unassigned instead of guessing.
        val candidates=matches.filter { it.first<=best+1 }.map { it.second.id }.distinct()
        return candidates.singleOrNull()?.let { id -> matches.filter { it.second.id==id }.minByOrNull { it.first }?.second }
    }
    fun validAlias(alias: String): Boolean {
        val n=normalize(alias)
        return n.length in 3..60 && n.any { it in 'a'..'z' } && n.split(" ").size<=4 &&
            words.values.flatten().none { normalize(it)==n } && !n.startsWith("punkt ")
    }
    private fun distance(a:String,b:String):Int {
        var row=IntArray(b.length+1){it}
        for(i in a.indices) {
            val next=IntArray(b.length+1);next[0]=i+1
            for(j in b.indices)next[j+1]=minOf(next[j]+1,row[j+1]+1,row[j]+if(a[i]==b[j])0 else 1)
            row=next
        }
        return row[b.length]
    }
}

