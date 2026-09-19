package de.tbhelfer.server

import de.tbhelfer.BuildConfig

/**
 * Zentraler Server-Endpunkt.
 *
 * Debug kann lokal per local.properties / Gradle-Property auf Staging zeigen.
 * Release bleibt im Buildskript fest auf https://api.tb-helfer.de gesetzt.
 */
object ServerConfig {
    val BASE_URL: String = BuildConfig.SERVER_BASE_URL.trimEnd('/')
}
