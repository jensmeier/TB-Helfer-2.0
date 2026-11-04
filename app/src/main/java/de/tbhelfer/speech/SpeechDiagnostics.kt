package de.tbhelfer.speech

import android.content.Context
import de.tbhelfer.server.ErrorHistory
import de.tbhelfer.server.ErrorLogStore

object SpeechDiagnostics {
    fun error(c:Context,code:Int) {
        val safe=when(code) {
            3 -> "speech_audio"
            9,1001 -> "speech_permission"
            8 -> "speech_busy"
            12,13 -> "speech_language"
            1003 -> "speech_interrupted"
            1004 -> "speech_storage"
            else -> "speech_service"
        }
        ErrorLogStore.record(c,ErrorHistory.Operation.SPEECH,safe)
    }
}
