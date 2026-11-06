package de.tbhelfer.util

import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ShareStagingTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `private recovery preview is copied unchanged without touching original`() {
        val source = File(temporary.newFolder(), "preview.jpg").apply { writeText("photo bytes") }
        val root = File(temporary.newFolder(), "share-outgoing/generation")
        val copy = ShareStaging.copy(source, root)
        assertEquals("photo bytes", copy.readText())
        assertEquals("photo bytes", source.readText())
        assertTrue(copy.canonicalPath.startsWith(root.canonicalPath + File.separator))
    }

    @Test fun `same filename from different photos never overwrites an earlier share`() {
        val first = File(temporary.newFolder(), "photo.jpg").apply { writeText("first") }
        val second = File(temporary.newFolder(), "photo.jpg").apply { writeText("second") }
        val root = temporary.newFolder()
        val a = ShareStaging.copy(first, root)
        val b = ShareStaging.copy(second, root)
        assertNotEquals(a, b)
        assertEquals("first", a.readText())
        assertEquals("second", b.readText())
    }

    @Test(expected = IOException::class) fun `missing source fails visibly`() {
        ShareStaging.copy(File(temporary.root, "missing.jpg"), temporary.newFolder())
    }

    @Test(expected = IOException::class) fun `empty source fails visibly`() {
        ShareStaging.copy(temporary.newFile(), temporary.newFolder())
    }

    @Test fun `unwritable destination preserves source`() {
        val source = temporary.newFile().apply { writeText("original") }
        val blocked = temporary.newFile()
        try {
            ShareStaging.copy(source, blocked)
            fail("Expected destination failure")
        } catch (_: IOException) {
            assertEquals("original", source.readText())
        }
    }
}
