package de.tbhelfer.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Foreground-service and Activity share bounded, main-thread recognition. Audio is never retained. */
class SpeechSession(
    private val context:Context, private val local:Boolean,
    private val allowed:()->Boolean,
    private val onState:(String,Boolean)->Unit,
    private val onText:(String)->Unit,
    private val onFinished:(String,Int?)->Unit
) {
    companion object { private var active:SpeechSession?=null }
    private val handler=Handler(Looper.getMainLooper())
    private val state=SpeechSessionState(SystemClock.elapsedRealtime())
    private var recognizer:SpeechRecognizer?=null
    private var epoch=0
    private var stopping=false
    private var closed=false
    private var emptySessions=0
    private val tick=object:Runnable {
        override fun run() {
            if(closed)return
            if(!allowed()){finish(1001);return}
            if(state.due(SystemClock.elapsedRealtime()))stop()
            if(!closed)handler.postDelayed(this,250)
        }
    }
    fun start() {
        check(Looper.myLooper()==Looper.getMainLooper())
        if(active!=null){finish(SpeechRecognizer.ERROR_RECOGNIZER_BUSY);return}
        active=this
        handler.post(tick)
        listen()
    }
    private fun listen() {
        if(closed || stopping)return
        if(!allowed()){finish(1001);return}
        val ticket=++epoch
        recognizer?.destroy()
        onState("Mikrofon wird gestartet …",false)
        try {
            recognizer=if(local && Build.VERSION.SDK_INT>=31)SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                else SpeechRecognizer.createSpeechRecognizer(context)
            recognizer!!.setRecognitionListener(object:RecognitionListener {
                private var speechEnded=false
                fun valid()=!closed && ticket==epoch
                fun value(b:Bundle?)=b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                override fun onReadyForSpeech(params:Bundle?) { if(valid() && !stopping)onState("Aufnahme läuft – bitte sprechen",true) }
                override fun onBeginningOfSpeech(){if(valid() && !stopping){speechEnded=false;state.speech()}}
                override fun onEndOfSpeech(){if(valid() && !stopping){speechEnded=true;state.endSpeech(SystemClock.elapsedRealtime());onState("Kurze Pause möglich – Aufnahme läuft",true)}}
                override fun onPartialResults(partialResults:Bundle?) {
                    if(valid()){val text=value(partialResults);if(!speechEnded && text.isNotBlank())state.speech();state.partial(text);onText(state.text())}
                }
                override fun onSegmentResults(segmentResults:Bundle) {
                    if(valid()){state.segment(value(segmentResults));onText(state.text());emptySessions=0}
                }
                override fun onEndOfSegmentedSession(){if(valid())ended(null)}
                override fun onResults(results:Bundle?) {
                    if(valid()){if(value(results).isNotBlank())emptySessions=0;state.segment(value(results));onText(state.text());ended(null)}
                }
                override fun onError(error:Int) { if(valid()) {
                    if(stopping){finish(if(error==SpeechRecognizer.ERROR_NO_MATCH || error==SpeechRecognizer.ERROR_SPEECH_TIMEOUT)null else error);return}
                    if(error==SpeechRecognizer.ERROR_NO_MATCH || error==SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        state.segment("");onText(state.text());ended(error)
                    } else finish(error)
                } }
                override fun onRmsChanged(rmsdB:Float){} // Machine noise must not extend the speech timer.
                override fun onBufferReceived(buffer:ByteArray?){}
                override fun onEvent(eventType:Int,params:Bundle?){}
            })
            val request=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE,"de-DE").putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
            if(Build.VERSION.SDK_INT>=33) request
                .putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION,RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,SpeechSessionState.SILENCE_MS)
            if(androidx.core.content.ContextCompat.checkSelfPermission(context,android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED){finish(1001);return}
            recognizer!!.startListening(request)
        } catch(_:Exception){finish(1002)}
    }
    private fun ended(error:Int?) {
        if(closed)return
        ++epoch // Ignore late callbacks from the completed recognizer request.
        if(stopping){finish(null);return}
        state.endSpeech(SystemClock.elapsedRealtime())
        if(state.due(SystemClock.elapsedRealtime())){finish(null);return}
        if(error!=null && ++emptySessions>=4){finish(error);return}
        onState("Sprechpause – weiter sprechen oder beenden",false)
        handler.postDelayed({listen()},300)
    }
    fun stop() {
        if(closed || stopping)return
        stopping=true;onState("Aufnahme wird beendet …",false)
        runCatching { recognizer?.stopListening() }
        handler.postDelayed({finish(null)},1500)
    }
    fun cancel(){finish(1003)}
    private fun finish(error:Int?) {
        if(closed)return
        closed=true;state.stop();++epoch
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.cancel();recognizer?.destroy() };recognizer=null
        if(active===this)active=null
        onFinished(state.text(),error)
    }
}
