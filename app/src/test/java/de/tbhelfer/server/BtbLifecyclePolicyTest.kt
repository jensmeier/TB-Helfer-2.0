package de.tbhelfer.server

import de.tbhelfer.server.BtbLifecyclePolicy.Target.*
import org.junit.Assert.*
import org.junit.Test

class BtbLifecyclePolicyTest {
    @Test fun helperFinishNeedsServerFinished() {
        assertFalse(BtbLifecyclePolicy.confirmed(SHIFT_FINISH, false, "WORKING"))
        assertFalse(BtbLifecyclePolicy.confirmed(SHIFT_FINISH, false, null))
        assertTrue(BtbLifecyclePolicy.confirmed(SHIFT_FINISH, false, "FINISHED"))
    }
    @Test fun retryOfFinishedTargetNeverBecomesResume() {
        val originalClick = SHIFT_FINISH
        assertTrue(BtbLifecyclePolicy.confirmed(originalClick, false, "FINISHED"))
        assertEquals(SHIFT_FINISH, originalClick)
        assertFalse(BtbLifecyclePolicy.confirmed(originalClick, false, "WORKING"))
    }
    @Test fun wholeBtbRequiresFinalServerStatus() {
        assertFalse(BtbLifecyclePolicy.confirmed(FINALIZE, false, "FINISHED"))
        assertTrue(BtbLifecyclePolicy.confirmed(FINALIZE, true, "FINISHED"))
    }
    @Test fun portalClosureAlsoSatisfiesPendingHelperFinish() {
        assertTrue(BtbLifecyclePolicy.confirmed(SHIFT_FINISH, true, "WORKING"))
    }
    @Test fun portalClosureCannotResumeHelper() {
        assertFalse(BtbLifecyclePolicy.confirmed(SHIFT_RESUME, true, "WORKING"))
        assertFalse(BtbLifecyclePolicy.maySend(SHIFT_RESUME, false, false, true))
    }
    @Test fun resumeNeedsExplicitWorkingConfirmation() {
        assertFalse(BtbLifecyclePolicy.confirmed(SHIFT_RESUME, false, "FINISHED"))
        assertTrue(BtbLifecyclePolicy.confirmed(SHIFT_RESUME, false, "WORKING"))
    }
    @Test fun helperNeverFinalizesWholeBtb() {
        assertFalse(BtbLifecyclePolicy.maySend(FINALIZE, false, true, false))
        assertTrue(BtbLifecyclePolicy.maySend(SHIFT_FINISH, false, true, false))
    }
    @Test fun leadNeverUsesHelperLifecycle() {
        assertFalse(BtbLifecyclePolicy.maySend(SHIFT_FINISH, true, true, false))
        assertFalse(BtbLifecyclePolicy.maySend(SHIFT_RESUME, true, true, false))
        assertTrue(BtbLifecyclePolicy.maySend(FINALIZE, true, true, false))
    }
    @Test fun inactiveOrHistoricalBtbNeverReceivesLifecycleMutation() {
        for (target in BtbLifecyclePolicy.Target.entries) {
            assertFalse(BtbLifecyclePolicy.maySend(target, true, false, false))
            assertFalse(BtbLifecyclePolicy.maySend(target, false, false, false))
            assertFalse(BtbLifecyclePolicy.maySend(target, true, true, true))
            assertFalse(BtbLifecyclePolicy.maySend(target, false, true, true))
        }
    }
    @Test fun notParticipatingDoesNotMasqueradeAsFinished() {
        assertFalse(BtbLifecyclePolicy.confirmed(SHIFT_FINISH, false, "NOT_PARTICIPATING"))
        assertFalse(BtbLifecyclePolicy.confirmed(SHIFT_RESUME, false, "NOT_PARTICIPATING"))
    }
}
