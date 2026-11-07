package de.tbhelfer.ui.baustelle

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Owned by the ViewModel; no Fragment, View or Activity references. Main-thread confined. */
class TagesZipAuftrag(private val scope: CoroutineScope) {
    sealed interface Zustand {
        data object Bereit : Zustand
        data object Laeuft : Zustand
        data class Fertig(val datei: File) : Zustand
        data class Fehler(val ursache: Exception) : Zustand
    }
    private val mutable = MutableStateFlow<Zustand>(Zustand.Bereit)
    val zustand = mutable.asStateFlow()

    fun starten(export: suspend () -> File): Boolean {
        if (mutable.value is Zustand.Laeuft || mutable.value is Zustand.Fertig) return false
        mutable.value = Zustand.Laeuft
        scope.launch {
            try {
                val file = export()
                currentCoroutineContext().ensureActive()
                mutable.value = Zustand.Fertig(file)
            } catch (cancelled: CancellationException) {
                mutable.value = Zustand.Bereit
                throw cancelled
            } catch (error: Exception) {
                mutable.value = Zustand.Fehler(error)
            }
        }
        return true
    }

    /** Consume synchronously before launching the chooser: no repeat after resume/rotation. */
    fun uebernehmen(erwartet: Zustand): Boolean {
        if (mutable.value !== erwartet) return false
        mutable.value = Zustand.Bereit
        return true
    }
}
