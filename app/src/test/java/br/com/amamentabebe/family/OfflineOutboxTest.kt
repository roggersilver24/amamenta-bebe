package br.com.amamentabebe.family

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import br.com.amamentabebe.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class OfflineOutboxTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val family = FamilySelection("caregiver-1", "family-1", "baby-1")
    private fun engine(db: AppDatabase) = FamilySync(db, FamilyService(context)) { family to "Ana" }
    private fun database(name: String) = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(AppDatabase.MIGRATION_1_2).build()

    @Test fun privateHistoryEditDoesNotOptInToSharing() = runBlocking {
        val db = database("private-history.db")
        try {
            val feeding = Feeding(timeMillis = 1_700_000_000_000)
            val saved = feeding.copy(id = db.feedingDao().insert(feeding))
            val edited = saved.copy(note = "registro antigo editado")
            db.withTransaction { db.feedingDao().update(edited); engine(db).feeding(edited) }
            assertTrue(db.syncDao().all(family.familyId, family.babyId).isEmpty())
            assertEquals(edited, db.feedingDao().byId(saved.id))
        } finally { db.close() }
    }

    @Test fun feedingEditsAndDeleteKeepStableUuidAndDurableTombstoneAcrossRestart() = runBlocking {
        val name = "durable-outbox.db"
        var db = database(name)
        var stableId = ""
        var localId = 0L
        try {
            val feeding = Feeding(timeMillis = 1_700_000_000_000)
            localId = db.feedingDao().insert(feeding)
            val saved = feeding.copy(id = localId)
            val sync = engine(db)
            sync.feeding(saved, isNew = true)
            val first = db.syncDao().all(family.familyId, family.babyId).single()
            stableId = first.id
            assertEquals(stableId, UUID.fromString(stableId).toString())
            assertEquals("caregiver-1", first.authorUid)
            assertEquals("Ana", first.authorName)
            assertTrue(first.pending)
            sync.feeding(saved.copy(note = "Atualizado offline"))
            val updated = db.syncDao().all(family.familyId, family.babyId).single()
            assertEquals(stableId, updated.id)
            assertTrue(updated.payload.contains("Atualizado offline"))
            db.withTransaction {
                db.feedingDao().delete(saved)
                sync.feeding(saved.copy(note = "Atualizado offline"), deleted = true)
            }
            assertNull(db.feedingDao().byId(localId))
            val deleted = db.syncDao().byId(stableId)!!
            assertTrue(deleted.deleted)
            assertTrue(deleted.pending)
        } finally { db.close() }
        db = database(name)
        try {
            val restarted = engine(db)
            val tombstone = db.syncDao().byId(stableId)!!
            assertEquals(localId, tombstone.localId)
            assertTrue(tombstone.deleted && tombstone.pending)
            restarted.feeding(Feeding(id = localId, timeMillis = 1_700_000_000_000), deleted = true)
            assertEquals(stableId, db.syncDao().all(family.familyId, family.babyId).single().id)
            assertNull(db.feedingDao().byId(localId))
        } finally { db.close() }
    }

    @Test fun oldHistoryRequiresConfirmationAndRetainsUnknownAuthorAndStableIds() = runBlocking {
        val db = database("confirmed-history.db")
        try {
            val feeding = Feeding(timeMillis = 1_700_000_000_000, note = "Histórico privado original")
            val saved = feeding.copy(id = db.feedingDao().insert(feeding))
            val diaper = Diaper(timeMillis = 1_700_000_100_000, type = DiaperType.BOTH)
            val savedDiaper = diaper.copy(id = db.diaperDao().insert(diaper))
            val sync = engine(db)
            var rejected = false
            try { sync.shareExisting(confirmed = false) } catch (_: IllegalStateException) { rejected = true }
            assertTrue(rejected)
            assertTrue(db.syncDao().all(family.familyId, family.babyId).isEmpty())
            assertEquals(saved, db.feedingDao().byId(saved.id))
            sync.shareExisting(confirmed = true)
            val shared = db.syncDao().all(family.familyId, family.babyId)
            assertEquals(2, shared.size)
            assertTrue(shared.all { it.authorName == "Não informado (registro anterior)" && it.pending })
            assertEquals(saved, db.feedingDao().byId(saved.id))
            assertEquals(savedDiaper, db.diaperDao().byId(savedDiaper.id))
            val originalIds = shared.map { it.id }.toSet()
            sync.shareExisting(confirmed = true)
            val repeated = db.syncDao().all(family.familyId, family.babyId)
            assertEquals(originalIds, repeated.map { it.id }.toSet())
            assertEquals(2, repeated.size)
            assertTrue(repeated.all { it.authorName == "Não informado (registro anterior)" })
        } finally { db.close() }
    }

    @Test fun localMutationAndOutboxRollbackTogetherOnFailure() = runBlocking {
        val db = database("atomic-outbox.db")
        try {
            var rolledBack = false
            try {
                db.withTransaction {
                    val feeding = Feeding(timeMillis = 1_700_000_000_000)
                    val saved = feeding.copy(id = db.feedingDao().insert(feeding))
                    engine(db).feeding(saved, isNew = true)
                    error("Simulated failed local mutation")
                }
            } catch (_: IllegalStateException) { rolledBack = true }
            assertTrue(rolledBack)
            assertTrue(db.feedingDao().all().isEmpty())
            assertTrue(db.syncDao().all(family.familyId, family.babyId).isEmpty())
        } finally { db.close() }
    }

    @Test fun diaperUsesOwnStableMappingAndUnselectedUserQueuesNothing() = runBlocking {
        val db = database("diaper-outbox.db")
        try {
            val diaper = Diaper(timeMillis = 1_700_000_000_000, type = DiaperType.BOTH)
            val saved = diaper.copy(id = db.diaperDao().insert(diaper))
            val unselected = FamilySync(db, FamilyService(context)) { null }
            unselected.diaper(saved, isNew = true)
            assertTrue(db.syncDao().all(family.familyId, family.babyId).isEmpty())
            val sync = engine(db)
            sync.diaper(saved, isNew = true)
            val first = db.syncDao().all(family.familyId, family.babyId).single()
            sync.diaper(saved.copy(type = DiaperType.WET))
            val edited = db.syncDao().all(family.familyId, family.babyId).single()
            assertEquals(first.id, edited.id)
            assertEquals("DIAPER", edited.kind)
            assertTrue(edited.pending)
            assertNotNull(db.diaperDao().byId(saved.id))
        } finally { db.close() }
    }
}


