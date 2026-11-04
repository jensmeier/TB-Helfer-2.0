package de.tbhelfer.speech

/** One user recording may contain several recognizer sessions. No Android dependencies. */
class SpeechSessionState(private val started: Long) {
    companion object { const val SILENCE_MS=5_000L; const val INITIAL_WAIT_MS=10_000L; const val MAX_MS=300_000L }
    private val completed=mutableListOf<String>()
    private var partial=""
    private var heard=false
    private var silenceSince:Long?=null
    var stopped=false; private set
    fun speech() { if(!stopped){heard=true;silenceSince=null} }
    fun endSpeech(now:Long) { if(!stopped && silenceSince==null)silenceSince=now }
    fun partial(value:String) { if(!stopped && value.isNotBlank()){heard=true;partial=value.trim()} }
    fun segment(value:String) {
        if(stopped)return
        val accepted=value.trim().ifBlank { partial }
        if(accepted.isNotBlank()){completed.add(accepted);heard=true}
        partial=""
    }
    fun text()=(completed+partial.takeIf { it.isNotBlank() }.orEmpty()).filter { it.isNotBlank() }.joinToString(" ").take(12000)
    fun due(now:Long):Boolean = !stopped && (now-started>=MAX_MS ||
        (!heard && now-started>=INITIAL_WAIT_MS) || silenceSince?.let { now-it>=SILENCE_MS }==true)
    fun stop(){stopped=true}
}
