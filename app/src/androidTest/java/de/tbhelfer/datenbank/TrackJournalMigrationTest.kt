package de.tbhelfer.datenbank

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackJournalMigrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun open(name: String) = Room.databaseBuilder(context, TBDatenbank::class.java, name)
        .addMigrations(TBDatenbank.MIGRATION_19_22).build()

    private suspend fun withDatabase(name: String, block: suspend (TBDatenbank) -> Unit) {
        val db = open(name)
        try { block(db) } finally { db.close() }
    }

    @Test fun migrationPreservesAxisAndCreatesJournal() = runBlocking {
        val name = "migration-journal-test.db"
        context.deleteDatabase(name)
        val axis = TrassenAchseEntity(id=71, name="Original", quelleDatei="source.TRA", dateiSignatur="a".repeat(24))
        try {
            withDatabase(name) { db -> db.trassenDao().achseEinfuegen(axis) }
            // v22 differs from established v19 ONLY by the new journal table.
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("DROP TABLE track_sync_journal")
                it.version = 19
            }
            withDatabase(name) { db ->
                assertEquals(axis, db.trassenDao().achseMitId(71))
                db.trackSyncJournalDao().save(TrackSyncJournal("owner", "key", "confirmed"))
                assertEquals("confirmed", db.trackSyncJournalDao().load("owner", "key"))
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun geometryAndReceiptRollBackTogether() = runBlocking {
        val name = "atomic-journal-test.db"
        context.deleteDatabase(name)
        try {
            withDatabase(name) { db ->
                val original = TrassenAchseEntity(id=73, name="Original", quelleDatei="source.TRA")
                db.trassenDao().achseEinfuegen(original)
                try {
                    db.withTransaction {
                        db.trassenDao().achseAktualisieren(original.copy(name="Received"))
                        db.trackSyncJournalDao().save(TrackSyncJournal("owner", "key", "received"))
                        error("simulated interrupted receive")
                    }
                    fail("Expected interrupted transaction")
                } catch (expected: IllegalStateException) {
                    assertEquals("simulated interrupted receive", expected.message)
                }
                assertEquals(original, db.trassenDao().achseMitId(73))
                assertNull(db.trackSyncJournalDao().load("owner", "key"))
                db.withTransaction {
                    db.trassenDao().achseAktualisieren(original.copy(name="Received"))
                    db.trackSyncJournalDao().save(TrackSyncJournal("owner", "key", "received"))
                }
                assertEquals("Received", db.trassenDao().achseMitId(73)?.name)
                assertEquals("received", db.trackSyncJournalDao().load("owner", "key"))
                assertNull(db.trackSyncJournalDao().load("different-owner", "key"))
            }
        } finally { context.deleteDatabase(name) }
    }
}
