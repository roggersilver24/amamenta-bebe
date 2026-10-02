package br.com.amamentabebe.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedingDao {
    @Query("SELECT COUNT(*) FROM feedings WHERE timeMillis BETWEEN :start AND :end")
    suspend fun countInWindow(start: Long, end: Long): Int
    @Query("SELECT * FROM feedings ORDER BY timeMillis DESC")
    fun observeAll(): Flow<List<Feeding>>

    @Query("SELECT * FROM feedings ORDER BY timeMillis DESC LIMIT 1")
    suspend fun latest(): Feeding?

    @Query("SELECT * FROM feedings ORDER BY timeMillis DESC")
    suspend fun all(): List<Feeding>
    @Query("SELECT * FROM feedings WHERE id = :id") suspend fun byId(id: Long): Feeding?
    @Query("SELECT * FROM feedings WHERE timeMillis = :time") suspend fun atTime(time: Long): List<Feeding>

    @Insert suspend fun insert(feeding: Feeding): Long
    @Update suspend fun update(feeding: Feeding)
    @Delete suspend fun delete(feeding: Feeding)
}
