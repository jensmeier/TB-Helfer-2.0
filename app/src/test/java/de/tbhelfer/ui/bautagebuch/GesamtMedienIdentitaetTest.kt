package de.tbhelfer.ui.bautagebuch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GesamtMedienIdentitaetTest {
    @Test
    fun `fehlender hash verwendet dieselbe stabile server identitaet`() {
        val catalogKey = GesamtMedienIdentitaet.serverKey("server-photo-1", null)
        val linkedOriginalKey = GesamtMedienIdentitaet.serverKey("server-photo-1", "")
        assertEquals(catalogKey, linkedOriginalKey)
        assertEquals(64, catalogKey.length)
        assertNotEquals(catalogKey, GesamtMedienIdentitaet.serverKey("server-photo-2", null))
    }

    @Test
    fun `vorhandener dateihash behaelt bisherige katalog identitaet`() {
        assertEquals("abc123", GesamtMedienIdentitaet.serverKey("server-photo-1", "ABC123"))
        assertEquals("abc123", GesamtMedienIdentitaet.serverKey("server-photo-2", "abc123"))
    }
}
