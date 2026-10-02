package br.com.amamentabebe.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DatabaseUpgradeTest {
    @Test fun upgradePreservesOldRecordsAndAddsZeroDurations() = runTest {
        val context: Context = RuntimeEnvironment.getApplication()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val old = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE feedings (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timeMillis INTEGER NOT NULL, type TEXT NOT NULL, side TEXT NOT NULL, amountMl INTEGER, note TEXT NOT NULL)")
                    db.execSQL("INSERT INTO feedings VALUES (42, 1700000000000, 'BOTTLE', 'NONE', 90, 'Registro existente')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        old.writableDatabase
        old.close()
        val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2).build()
        try {
            val record = upgraded.feedingDao().byId(42)!!
            assertEquals("Registro existente", record.note)
            assertEquals(90, record.amountMl)
            assertEquals(1700000000000L, record.timeMillis)
            assertEquals(0L, record.leftDurationMillis)
            assertEquals(0L, record.rightDurationMillis)
            assertTrue(upgraded.diaperDao().all().isEmpty())
            val newId = upgraded.feedingDao().insert(Feeding(timeMillis = 1700000001000))
            assertTrue(newId > 42)
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }

    @Test fun backupMergeIsIdempotentAndRejectsInvalidFilesWithoutChanges() = runTest {
        val context: Context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            database.feedingDao().insert(Feeding(timeMillis = 1700000000000, note = "Preservado"))
            database.diaperDao().insert(Diaper(timeMillis = 1700000000001, type = DiaperType.BOTH))
            val manager = BackupManager(database, SettingsStore(context))
            val exported = manager.export()
            assertEquals(ImportResult(0, 0), manager.import(exported))
            val restored = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
            try {
                val restoredManager = BackupManager(restored, SettingsStore(context))
                assertEquals(ImportResult(1, 1), restoredManager.import(exported))
                assertEquals("Preservado", restored.feedingDao().all().single().note)
                assertEquals(DiaperType.BOTH, restored.diaperDao().all().single().type)
                assertEquals(ImportResult(0, 0), restoredManager.import(exported))
            } finally { restored.close() }
            val bad = exported.replace("\"BOTH\"", "\"INVALID\"")
            assertTrue(runCatching { manager.import(bad) }.isFailure)
            assertEquals(1, database.feedingDao().all().size)
            assertEquals(1, database.diaperDao().all().size)
        } finally { database.close() }
    }

    @Test fun invalidNumericBackupFieldsRejectTheEntireMerge() = runTest {
        val context: Context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            database.feedingDao().insert(Feeding(timeMillis = 1700000000000, note = "Original"))
            val manager = BackupManager(database, SettingsStore(context))
            val exported = manager.export()
            for ((key, badValue) in listOf(
                "leftDurationMillis" to "inválido", "rightDurationMillis" to "1000",
                "timeMillis" to 1700000000000.5, "timeMillis" to Long.MAX_VALUE,
                "leftDurationMillis" to Long.MAX_VALUE
            )) {
                val document = JSONObject(exported)
                val records = document.getJSONArray("feedings")
                // A valid new row precedes the invalid one: validation must finish before inserts.
                val validNew = JSONObject(records.getJSONObject(0).toString()).put("timeMillis", 1700000001000)
                val invalid = JSONObject(records.getJSONObject(0).toString()).put(key, badValue)
                records.put(0, validNew)
                records.put(invalid)
                assertTrue("Must reject $key=$badValue", runCatching { manager.import(document.toString()) }.isFailure)
                assertEquals(listOf("Original"), database.feedingDao().all().map { it.note })
            }
            val badSettings = JSONObject(exported)
            badSettings.getJSONArray("feedings").getJSONObject(0).put("timeMillis", 1700000002000)
            badSettings.getJSONObject("settings").put("intervalMinutes", "120")
            assertTrue(runCatching { manager.import(badSettings.toString()) }.isFailure)
            assertEquals(1, database.feedingDao().all().size)
        } finally { database.close() }
    }

    @Test fun legacyLargeNotesSurviveExportAndMergeWithoutTruncation() = runTest {
        val context: Context = RuntimeEnvironment.getApplication()
        val source = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val restored = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val note = "a".repeat(10001)
            source.feedingDao().insert(Feeding(timeMillis = 1700000000000, note = note))
            val json = BackupManager(source, SettingsStore(context)).export()
            val manager = BackupManager(restored, SettingsStore(context))
            assertEquals(ImportResult(1, 0), manager.import(json))
            assertEquals(note, restored.feedingDao().all().single().note)
            assertEquals(ImportResult(0, 0), manager.import(json))
        } finally { source.close(); restored.close() }
    }
}
