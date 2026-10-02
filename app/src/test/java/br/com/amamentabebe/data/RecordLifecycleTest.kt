package br.com.amamentabebe.data

import android.content.Context
import androidx.room.Room
import br.com.amamentabebe.family.FamilyService
import br.com.amamentabebe.family.FamilySync
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RecordLifecycleTest {
    @Test fun futureManualRecordDoesNotBypassQuickTapDeduplication() = runTest {
        val context: Context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = FeedingRepository(db.feedingDao(), SettingsStore(context), db, FamilySync(db, FamilyService(context))) {}
            db.feedingDao().insert(Feeding(timeMillis = 1800000000000))
            assertNotNull(repo.quickAdd(1700000000000))
            assertNull(repo.quickAdd(1700000000100))
            assertEquals(2, db.feedingDao().all().size)
        } finally { db.close() }
    }
    @Test fun repeatedTapRejectedAndInterruptedTimerSaveIsIdempotent() = runTest {
        val context: Context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = FeedingRepository(db.feedingDao(), SettingsStore(context), db, FamilySync(db, FamilyService(context))) {}
            assertNotNull(repo.quickAdd(1700000000000))
            assertNull(repo.quickAdd(1700000000100))
            val timer = Feeding(timeMillis = 1700000010000, type = FeedingType.BREAST, side = BreastSide.BOTH, leftDurationMillis = 5000, rightDurationMillis = 7000)
            assertNotNull(repo.add(timer))
            assertNotNull(repo.add(Feeding(timeMillis = 1700000020000)))
            assertNull(repo.add(timer))
            assertEquals(3, repo.feedings.first().size)
            val saved = repo.feedings.first().first { it.timeMillis == timer.timeMillis }
            repo.update(saved.copy(note = "Corrigido", leftDurationMillis = 9000))
            assertEquals("Corrigido", db.feedingDao().byId(saved.id)!!.note)
            assertEquals(9000L, db.feedingDao().byId(saved.id)!!.leftDurationMillis)
            repo.delete(saved)
            assertNull(db.feedingDao().byId(saved.id))
            assertEquals(2, repo.feedings.first().size)
        } finally { db.close() }
    }
    @Test fun diaperEditDeleteAndCombinedTypeRemainSingleChangeWithoutAlarmMutation() = runTest {
        val context: Context = RuntimeEnvironment.getApplication()
        val settings = SettingsStore(context)
        settings.setAlarm(1700001000000)
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.diaperDao()
            val both = Diaper(timeMillis = 1700000000000, type = DiaperType.BOTH)
            val saved = both.copy(id = dao.insert(both))
            assertEquals(1, dao.observeAll().first().size)
            assertEquals(1, dao.all().count { it.type != DiaperType.DIRTY })
            assertEquals(1, dao.all().count { it.type != DiaperType.WET })
            dao.update(saved.copy(type = DiaperType.WET, timeMillis = 1700000002000))
            assertEquals(DiaperType.WET, dao.all().single().type)
            assertEquals(1700000002000L, dao.all().single().timeMillis)
            dao.delete(saved)
            assertTrue(dao.observeAll().first().isEmpty())
            assertEquals(1700001000000L, settings.current().nextAlarmMillis)
        } finally { db.close() }
    }
}
