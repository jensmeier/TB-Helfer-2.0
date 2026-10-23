package de.tbhelfer.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAuthorizationTest {
    @Test fun missingOrInvalidCredentialsRequireActivation() {
        assertTrue(DeviceAuthorization.sessionInvalid(401, "DEVICE_TOKEN_INVALID"))
        assertTrue(DeviceAuthorization.sessionInvalid(401, "DEVICE_TOKEN_MISSING"))
    }

    @Test fun revokedDeviceAndDisabledUserRemainBlocked() {
        listOf("DEVICE_REVOKED", "USER_DISABLED", "USER_EXPIRED").forEach {
            assertTrue(DeviceAuthorization.sessionInvalid(403, it))
        }
    }

    @Test fun deniedResourcesMustNotLogOutAValidDevice() {
        listOf("WORKSITE_ACCESS_DENIED", "WORKSITE_INVITATION_COMPANY_MISMATCH",
            "BTB_PARTICIPANT_NOT_ACTIVE", "CATALOG_ACCESS_DENIED", "", "<html>Forbidden</html>").forEach {
            assertFalse(DeviceAuthorization.sessionInvalid(403, it))
        }
    }

    @Test fun nonAuthenticationErrorsMustNotClearCredentials() {
        listOf(200, 404, 409, 429, 500, 503).forEach {
            assertFalse(DeviceAuthorization.sessionInvalid(it, "DEVICE_REVOKED"))
        }
    }
}

