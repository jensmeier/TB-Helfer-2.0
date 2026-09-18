package de.tbhelfer.ui.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorksiteInvitationLinkTest {

    @Test
    fun parsesServerAppUriWithoutChangingTokenCase() {
        val token = "AbCdEfghijklmnopqrstuvwxyz_1234567890"
        val parsed = WorksiteInvitationLink.parse(
            "tbhelfer://worksite-invite?token=$token"
        )
        assertTrue(parsed is WorksiteInvitationLink.Credential.ServerToken)
        assertEquals(token, parsed?.value)
    }

    @Test
    fun parsesServerBrowserFragment() {
        val token = "A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6"
        val parsed = WorksiteInvitationLink.parse(
            "https://admin.tb-helfer.de/portal/v1/invitations/redeem#token=$token"
        )
        assertTrue(parsed is WorksiteInvitationLink.Credential.ServerToken)
        assertEquals(token, parsed?.value)
    }

    @Test
    fun acceptsRawServerTokenForManualPaste() {
        val token = "xYz_1234567890abcdefghijklmnopqrst"
        val parsed = WorksiteInvitationLink.parse(token)
        assertTrue(parsed is WorksiteInvitationLink.Credential.ServerToken)
        assertEquals(token, parsed?.value)
    }

    @Test
    fun keepsLegacyInviteCompatibility() {
        val parsed = WorksiteInvitationLink.parse(
            "tbhelfer://invite?code=tbi-abcd-1234"
        )
        assertTrue(parsed is WorksiteInvitationLink.Credential.LegacyCode)
        assertEquals("TBI-ABCD-1234", parsed?.value)
    }

    @Test
    fun rejectsActivationAndWrongInviteHost() {
        assertNull(
            WorksiteInvitationLink.parse(
                "tbhelfer://activate?key=TBH-12345678901234567890"
            )
        )
        assertNull(
            WorksiteInvitationLink.parse(
                "tbhelfer://not-worksite-invite?token=abcdefghijklmnopqrstuvwxyz123456"
            )
        )
    }
}
