package de.tbhelfer.server.recovery

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class RecoveryHttpTest {
    private class Fake : HttpURLConnection(URL("https://example.test/api/v1/catalog")) {
        var responseReads = 0
        var outputRequests = 0
        var inputRequests = 0
        var disconnected = false
        var status = 200
        val sent = ByteArrayOutputStream()
        override fun connect() { }
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode(): Int { responseReads++; return status }
        override fun getOutputStream() = sent.also { outputRequests++ }
        override fun getInputStream() = ByteArrayInputStream("payload".toByteArray()).also { inputRequests++ }
    }
    @Test fun blockedUploadCannotOpenStreamOrSendBytes() {
        val fake = Fake()
        val connection = GuardedConnection(fake, { throw IOException("rebase required") }, { _, _ -> })
        connection.requestMethod = "POST"
        connection.doOutput = true
        try { connection.outputStream.write(byteArrayOf(42)); fail("Upload bypassed gate") } catch (_: IOException) { }
        assertEquals(0, fake.outputRequests)
        assertEquals(0, fake.sent.size())
        assertEquals(0, fake.responseReads)
    }
    @Test fun blockedReadCannotConsumeServerOrLocalSnapshot() {
        val fake = Fake()
        val connection = GuardedConnection(fake, { throw IOException("blocked") }, { _, _ -> })
        try { connection.inputStream; fail("Read bypassed gate") } catch (_: IOException) { }
        assertEquals(0, fake.inputRequests)
        assertEquals(0, fake.responseReads)
    }
    @Test fun settingsAndAuthorizationReachActualConnectionBeforeGate() {
        val fake = Fake()
        var calls = 0
        val connection = GuardedConnection(fake, {
            calls++
            assertEquals("PATCH", it.requestMethod)
            assertEquals("Bearer private", it.getRequestProperty("Authorization"))
            assertEquals(81, it.connectTimeout)
            assertEquals(93, it.readTimeout)
            assertTrue(it.doOutput)
            assertFalse(it.useCaches)
            it.setRequestProperty("X-TB-Recovery-ID", "approved")
        }, { _, _ -> })
        connection.requestMethod = "PATCH"
        connection.connectTimeout = 81
        connection.readTimeout = 93
        connection.doOutput = true
        connection.useCaches = false
        connection.setRequestProperty("Authorization", "Bearer private")
        connection.outputStream.use { it.write("body".toByteArray()) }
        assertEquals(200, connection.responseCode)
        assertEquals(1, calls)
        assertEquals("approved", fake.getRequestProperty("X-TB-Recovery-ID"))
        assertEquals("body", fake.sent.toString("UTF-8"))
    }
    @Test fun conflictIsObservedWithoutRetryOrBodyConsumption() {
        val fake = Fake().apply { status = 409 }
        var observed = 0
        val connection = GuardedConnection(fake, {}, { _, code -> assertEquals(409, code); observed++ })
        assertEquals(409, connection.responseCode)
        assertEquals(1, observed)
        assertEquals(1, fake.responseReads)
        assertEquals(0, fake.outputRequests)
        assertEquals(0, fake.inputRequests)
        connection.disconnect()
        assertTrue(fake.disconnected)
    }
    @Test fun permittedBodyRemainsAvailableToCaller() {
        val fake = Fake()
        val connection = GuardedConnection(fake, {}, { _, _ -> })
        assertEquals("payload", connection.inputStream.bufferedReader().use { it.readText() })
        assertEquals(1, fake.inputRequests)
    }
}
