package de.tbhelfer.speech

import android.content.Context
import de.tbhelfer.server.recovery.RecoveryWorkspace
import org.json.JSONArray
import org.json.JSONObject

/** Learned only through an explicit confirmation; isolated per owner, site and firm ID. */
object SpeechAliasStore {
    private fun prefs(c:Context)=RecoveryWorkspace.wrap(c.applicationContext).getSharedPreferences("speech_confirmed_aliases_v1",Context.MODE_PRIVATE)
    private fun read(c:Context)=runCatching { JSONArray(prefs(c).getString("aliases","[]")) }.getOrElse { JSONArray() }
    fun list(c:Context,site:Long):List<Pair<String,String>> {
        val owner=SpeechDraftStore.owner(c) ?: return emptyList()
        val a=read(c)
        return (0 until a.length()).mapNotNull { i -> runCatching {
            val j=a.getJSONObject(i)
            if(j.getString("owner")==owner && j.getLong("site")==site) j.getString("firm") to j.getString("alias") else null
        }.getOrNull() }
    }
    @Synchronized fun remember(c:Context,owner:String,site:Long,firm:String,alias:String, firms:List<Pair<String,String>>) {
        check(!RecoveryWorkspace.blocked() && owner==SpeechDraftStore.owner(c))
        require(site>0 && firm.isNotBlank() && firms.any { it.first==firm })
        require(SpeechMatching.validAlias(alias))
        val normalized=SpeechMatching.normalize(alias)
        require(firms.none { it.first!=firm && SpeechMatching.normalize(it.second)==normalized })
        require(list(c,site).none { it.first!=firm && SpeechMatching.normalize(it.second)==normalized })
        val old=read(c);val next=JSONArray()
        for(i in maxOf(0,old.length()-199) until old.length()) {
            val j=old.optJSONObject(i) ?: continue
            if(!(j.optString("owner")==owner && j.optLong("site")==site && j.optString("alias")==normalized)) next.put(j)
        }
        next.put(JSONObject().put("owner",owner).put("site",site).put("firm",firm).put("alias",normalized))
        check(prefs(c).edit().putString("aliases",next.toString()).commit())
    }
    @Synchronized fun clearOwner(c:Context,owner:String) {
        check(!RecoveryWorkspace.blocked() && owner==SpeechDraftStore.owner(c))
        val old=read(c);val next=JSONArray()
        for(i in 0 until old.length())old.optJSONObject(i)?.takeIf { it.optString("owner")!=owner }?.let { next.put(it) }
        check(prefs(c).edit().putString("aliases",next.toString()).commit())
    }
}
