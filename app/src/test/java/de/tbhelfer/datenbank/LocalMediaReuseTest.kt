package de.tbhelfer.datenbank

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalMediaReuseTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun file(name: String, text: String = "image bytes"): File =
        temporary.newFile(name).apply { writeText(text) }

    private fun hash(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private fun path(mapped: File? = null, cached: File? = null, ready: Boolean = true,
                     image: Boolean = true, remote: String? = null, old: String? = remote): String? =
        LocalMediaReuse.path(mapped?.absolutePath, cached?.absolutePath, ready, image, remote, old)

    @Test fun `own mapped original wins over downloaded preview`() {
        val original = file("camera.jpg", "full original")
        val preview = file("id_mobile_preview.jpg", "small preview")
        assertEquals(original.absolutePath, path(original, preview))
        assertEquals("full original", original.readText())
        assertTrue(preview.exists())
    }

    @Test fun `cleared upload cache does not redownload mapped original`() {
        val original = file("camera.jpg")
        assertEquals(original.absolutePath, path(mapped = original, ready = false))
    }

    @Test fun `own upload is reused by verified server hash despite different suffix`() {
        val upload = file("btb_42.jpg")
        assertEquals(upload.absolutePath, path(cached = upload, remote = hash(upload)))
    }

    @Test fun `hash comparison ignores letter case`() {
        val upload = file("btb_42.jpg")
        assertEquals(upload.absolutePath, path(cached = upload, remote = hash(upload).uppercase(), old = hash(upload)))
    }

    @Test fun `cached hash alone cannot hide corrupt upload bytes`() {
        val upload = file("btb_42.jpg")
        val sha = hash(upload)
        upload.writeText("changed")
        assertNull(path(cached = upload, remote = sha))
    }

    @Test fun `portal and helper images missing locally still require download`() {
        assertNull(path(remote = "a".repeat(64)))
    }

    @Test fun `unproven local image cannot suppress a download`() {
        val sameName = file("camera.jpg")
        assertNull(path(cached = sameName))
        assertNull(path(cached = sameName, remote = "not-a-sha"))
        assertNull(path(cached = sameName, remote = "b".repeat(64)))
    }

    @Test fun `existing mobile preview remains usable`() {
        val preview = file("id_mobile_preview.jpg")
        assertEquals(preview.absolutePath, path(cached = preview, remote = "a".repeat(64)))
    }

    @Test fun `changed server payload invalidates original and preview cache`() {
        val original = file("camera.jpg")
        val preview = file("id_mobile_preview.jpg")
        assertNull(path(original, preview, remote = "a".repeat(64), old = "b".repeat(64)))
    }

    @Test fun `missing mapped original falls back to existing preview`() {
        val original = file("camera.jpg").apply { delete() }
        val preview = file("id_mobile_preview.jpg")
        assertEquals(preview.absolutePath, path(original, preview))
    }

    @Test fun `missing or empty files are downloaded again`() {
        val empty = file("id_mobile_preview.jpg", "")
        assertNull(path(cached = empty))
        assertNull(path(mapped = empty))
        empty.delete()
        assertNull(path(cached = empty))
    }

    @Test fun `non image attachments retain ordinary local file reuse`() {
        val pdf = file("document.pdf")
        assertEquals(pdf.absolutePath, path(cached = pdf, image = false))
        assertNull(path(cached = pdf, ready = false, image = false))
    }

    @Test fun `old snapshot cannot unlink a just uploaded photo`() {
        assertFalse(LocalMediaReuse.confirmedUnlink(false, true, false, true))
        assertFalse(LocalMediaReuse.confirmedUnlink(false, false, false, false))
    }

    @Test fun `confirmed portal unlink and parent move detach old binding`() {
        assertTrue(LocalMediaReuse.confirmedUnlink(false, true, true, true))
        assertTrue(LocalMediaReuse.confirmedUnlink(false, true, false, false))
        assertFalse(LocalMediaReuse.confirmedUnlink(true, true, true, false))
    }

    @Test fun `upload old snapshot refresh preserves identity and does not download`() {
        val original = file("camera.jpg", "original")
        val upload = file("btb_42.jpg", "compressed")
        var mapped: File? = original
        if (LocalMediaReuse.confirmedUnlink(false, true, false, true)) mapped = null
        assertEquals(original.absolutePath, path(mapped, upload, remote = hash(upload)))
        upload.delete() // Android may evict the temporary upload cache.
        assertEquals(original.absolutePath, path(mapped, upload, remote = hash(file("server.jpg", "compressed"))))
    }

    @Test fun `download and second import wait until upload identity is published`() = runBlocking {
        withTimeout(5000) {
            val entered = CompletableDeferred<Unit>()
            val releaseUpload = CompletableDeferred<Unit>()
            var mapped = false
            var imports = 0
            val upload = launch {
                MediaSyncGate.run {
                    entered.complete(Unit)
                    releaseUpload.await()
                    mapped = true
                }
            }
            entered.await()
            val refresh = async(start = CoroutineStart.UNDISPATCHED) {
                MediaSyncGate.run { if (!mapped) imports++; mapped }
            }
            assertFalse(refresh.isCompleted)
            releaseUpload.complete(Unit)
            upload.join()
            assertTrue(refresh.await())
            assertEquals(0, imports)
        }
    }

    @Test fun `cancelling a media operation releases the gate`() = runBlocking {
        withTimeout(5000) {
            val entered = CompletableDeferred<Unit>()
            val never = CompletableDeferred<Unit>()
            val operation = launch { MediaSyncGate.run { entered.complete(Unit); never.await() } }
            entered.await()
            operation.cancelAndJoin()
            assertEquals("next sync", MediaSyncGate.run { "next sync" })
        }
    }
}
