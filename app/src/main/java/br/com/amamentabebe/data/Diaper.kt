package br.com.amamentabebe.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class DiaperType { WET, DIRTY, BOTH }

@Entity(tableName = "diapers")
data class Diaper(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timeMillis: Long,
    val type: DiaperType
)

@Dao
interface DiaperDao {
    @Query("SELECT * FROM diapers ORDER BY timeMillis DESC") fun observeAll(): Flow<List<Diaper>>
    @Query("SELECT * FROM diapers ORDER BY timeMillis DESC") suspend fun all(): List<Diaper>
    @Query("SELECT * FROM diapers WHERE id = :id") suspend fun byId(id: Long): Diaper?
    @Insert suspend fun insert(diaper: Diaper): Long
    @Update suspend fun update(diaper: Diaper)
    @Delete suspend fun delete(diaper: Diaper)
}
