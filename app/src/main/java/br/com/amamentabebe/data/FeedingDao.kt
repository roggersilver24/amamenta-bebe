package br.com.amamentabebe.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedingDao {
    @Query("SELECT * FROM feedings ORDER BY timeMillis DESC")
    fun observeAll(): Flow<List<Feeding>>

    @Query("SELECT * FROM feedings ORDER BY timeMillis DESC LIMIT 1")
    suspend fun latest(): Feeding?

    @Insert suspend fun insert(feeding: Feeding): Long
    @Update suspend fun update(feeding: Feeding)
    @Delete suspend fun delete(feeding: Feeding)
}
