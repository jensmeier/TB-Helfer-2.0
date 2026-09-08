package de.tbhelfer.server

/**
 * Zentraler HTTPS-Endpunkt des TB-Helfer-Servers.
 *
 * Der Zugriff läuft über Cloudflare Tunnel auf api.tb-helfer.de. Die API selbst
 * bleibt zusätzlich durch die TB-Helfer-Geräte-/Aktivierungs-Authentifizierung
 * geschützt.
 */
object ServerConfig {
    const val BASE_URL = "https://api.tb-helfer.de"
}
