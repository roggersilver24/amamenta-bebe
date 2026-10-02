package br.com.amamentabebe.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import br.com.amamentabebe.family.SyncRecord
import br.com.amamentabebe.family.SyncDao

class Converters {
    @TypeConverter fun typeToString(value: FeedingType) = value.name
    @TypeConverter fun stringToType(value: String) = FeedingType.valueOf(value)
    @TypeConverter fun sideToString(value: BreastSide) = value.name
    @TypeConverter fun stringToSide(value: String) = BreastSide.valueOf(value)
    @TypeConverter fun diaperToString(value: DiaperType) = value.name
    @TypeConverter fun stringToDiaper(value: String) = DiaperType.valueOf(value)
}

@Database(entities = [Feeding::class, Diaper::class, SyncRecord::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedingDao(): FeedingDao
    abstract fun diaperDao(): DiaperDao
    abstract fun syncDao(): SyncDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE feedings ADD COLUMN leftDurationMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE feedings ADD COLUMN rightDurationMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS diapers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timeMillis INTEGER NOT NULL, type TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_records (id TEXT NOT NULL PRIMARY KEY, familyId TEXT NOT NULL, babyId TEXT NOT NULL, kind TEXT NOT NULL, localId INTEGER NOT NULL, payload TEXT NOT NULL, authorUid TEXT NOT NULL, authorName TEXT NOT NULL, revision INTEGER NOT NULL, deleted INTEGER NOT NULL, pending INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_records_kind_localId_familyId_babyId ON sync_records(kind, localId, familyId, babyId)")
            }
        }
        fun create(context: Context) = Room.databaseBuilder(
            context.applicationContext, AppDatabase::class.java, "amamenta-bebe.db"
        ).addMigrations(MIGRATION_1_2).build()
    }
}
