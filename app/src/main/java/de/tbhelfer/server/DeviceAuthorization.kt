package de.tbhelfer.server

/** A denied resource must not invalidate the device's credentials. */
internal object DeviceAuthorization {
    fun sessionInvalid(httpCode: Int, detail: String): Boolean =
        httpCode == 401 || (httpCode == 403 &&
            detail in setOf("DEVICE_REVOKED", "USER_DISABLED", "USER_EXPIRED"))
}

