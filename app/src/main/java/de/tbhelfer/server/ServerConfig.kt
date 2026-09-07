package de.tbhelfer.server

/**
 * Zentrale Serveradresse für die aktuelle Entwicklungsphase.
 *
 * Der Zugriff läuft über das private Tailscale-Netz. Sobald der öffentliche
 * HTTPS-Endpunkt steht, wird diese Adresse durch die HTTPS-URL ersetzt und die
 * Cleartext-Freigabe im Manifest wieder entfernt.
 */
object ServerConfig {
    const val BASE_URL = "http://100.64.132.52:8089"
}
