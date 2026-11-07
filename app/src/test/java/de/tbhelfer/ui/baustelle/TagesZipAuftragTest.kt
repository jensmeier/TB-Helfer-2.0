package de.tbhelfer.ui.baustelle

import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class TagesZipAuftragTest {
    @Test fun duplicateClicksDoNotStartAnotherExport() = runBlocking {
        val job = SupervisorJob()
        val task = TagesZipAuftrag(CoroutineScope(job + Dispatchers.Unconfined))
        val gate = CompletableDeferred<File>()
        var starts = 0
        assertTrue(task.starten { starts++; gate.await() })
        assertFalse(task.starten { starts++; File("wrong") })
        assertEquals(1, starts)
        gate.complete(File("ready.zip"))
        assertTrue(task.zustand.value is TagesZipAuftrag.Zustand.Fertig)
        assertFalse(task.starten { File("wrong") })
        job.cancel()
    }

    @Test fun resultRemainsAvailableWithoutUiAndIsConsumedOnlyOnce() = runBlocking {
        val job = SupervisorJob()
        val task = TagesZipAuftrag(CoroutineScope(job + Dispatchers.Unconfined))
        val gate = CompletableDeferred<File>()
        task.starten { gate.await() }
        // No collector while an old view is destroyed and a new view is created.
        gate.complete(File("ready.zip"))
        val result = task.zustand.value
        assertEquals(File("ready.zip"), (result as TagesZipAuftrag.Zustand.Fertig).datei)
        assertSame(result, task.zustand.value)
        assertTrue(task.uebernehmen(result))
        assertFalse(task.uebernehmen(result))
        assertSame(TagesZipAuftrag.Zustand.Bereit, task.zustand.value)
        job.cancel()
    }

    @Test fun cancellationIsNotReportedAsExportFailure() = runBlocking {
        val job = SupervisorJob()
        val task = TagesZipAuftrag(CoroutineScope(job + Dispatchers.Unconfined))
        task.starten { awaitCancellation() }
        job.cancel()
        assertSame(TagesZipAuftrag.Zustand.Bereit, task.zustand.value)
    }

    @Test fun originalExceptionSurvivesAndRetryCanSucceed() = runBlocking {
        val job = SupervisorJob()
        val task = TagesZipAuftrag(CoroutineScope(job + Dispatchers.Unconfined))
        val failure = IOException("test")
        task.starten { throw failure }
        val previous = task.zustand.value as TagesZipAuftrag.Zustand.Fehler
        assertSame(failure, previous.ursache)
        assertTrue(task.starten { File("retry.zip") })
        assertFalse(task.uebernehmen(previous))
        assertEquals(File("retry.zip"), (task.zustand.value as TagesZipAuftrag.Zustand.Fertig).datei)
        job.cancel()
    }

    @Test fun swallowedCancellationCannotPublishAResult() = runBlocking {
        val job = SupervisorJob()
        val task = TagesZipAuftrag(CoroutineScope(job + Dispatchers.Unconfined))
        task.starten {
            try { awaitCancellation() } catch (_: CancellationException) { File("cancelled.zip") }
        }
        job.cancel()
        assertSame(TagesZipAuftrag.Zustand.Bereit, task.zustand.value)
    }

    @Test fun consumedResultAllowsNextExport() = runBlocking {
        val job = SupervisorJob()
        val task = TagesZipAuftrag(CoroutineScope(job + Dispatchers.Unconfined))
        task.starten { File("one.zip") }
        task.uebernehmen(task.zustand.value)
        assertTrue(task.starten { File("two.zip") })
        assertEquals(File("two.zip"), (task.zustand.value as TagesZipAuftrag.Zustand.Fertig).datei)
        job.cancel()
    }
}
