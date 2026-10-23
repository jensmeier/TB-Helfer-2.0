package de.tbhelfer.server.recovery

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class RecoveryPolicyTest {
    private val first = "f1b83f27-3f89-46ca-9620-9c38212d0491"
    private val next = "e197b955-460e-4c21-8d94-ed0492f03ba2"
    @Test fun legacyServerWithoutRestorePermitsLegacyWorkspace() {
        assertTrue(RecoveryPolicy.permits(null, null, true))
    }
    @Test fun restoredServerNeverPermitsOldOutbox() {
        assertFalse(RecoveryPolicy.permits(first, null, true))
        assertFalse(RecoveryPolicy.permits(next, first, true))
    }
    @Test fun acknowledgedGenerationRequiresSameDeviceBinding() {
        assertTrue(RecoveryPolicy.permits(first, first, true))
        assertFalse(RecoveryPolicy.permits(first, first, false))
    }
    @Test fun missingRecoveryIdNeverDowngradesAcknowledgedWorkspace() {
        assertFalse(RecoveryPolicy.permits(null, first, true))
    }
    @Test fun canonicalUuidIsRequired() {
        assertEquals(first, RecoveryPolicy.epoch(first.uppercase()))
        for (bad in listOf("", "1-1-1-1-1", "null", "../../data", first + "x")) {
            try { RecoveryPolicy.epoch(bad); fail("Accepted malformed epoch: $bad") } catch (_: IOException) { }
        }
        assertNull(RecoveryPolicy.epoch(null))
    }
}
