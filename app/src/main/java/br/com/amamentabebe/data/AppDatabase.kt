package br.com.amamentabebe.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun typeToString(value: FeedingType) = value.name
    @TypeConverter fun stringToType(value: String) = FeedingType.valueOf(value)
    @TypeConverter fun sideToString(value: BreastSide) = value.name
    @TypeConverter fun stringToSide(value: String) = BreastSide.valueOf(value)
}

@Database(entities = [Feeding::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedingDao(): FeedingDao
    companion object {
        fun create(context: Context) = Room.databaseBuilder(
            context.applicationContext, AppDatabase::class.java, "amamenta-bebe.db"
        ).build()
    }
}
