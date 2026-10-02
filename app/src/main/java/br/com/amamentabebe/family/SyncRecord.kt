package br.com.amamentabebe.family

import androidx.room.*

@Entity(tableName = "sync_records", indices = [Index(value = ["kind", "localId", "familyId", "babyId"], unique = true)])
data class SyncRecord(
    @PrimaryKey val id: String,
    val familyId: String,
    val babyId: String,
    val kind: String,
    val localId: Long,
    val payload: String,
    val authorUid: String,
    val authorName: String,
    val revision: Long,
    val deleted: Boolean,
    val pending: Boolean
)

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_records ORDER BY revision ASC")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<SyncRecord>>
    @Query("SELECT * FROM sync_records WHERE familyId = :familyId AND babyId = :babyId")
    suspend fun all(familyId: String, babyId: String): List<SyncRecord>
    @Query("SELECT * FROM sync_records WHERE kind = :kind AND localId = :localId AND familyId = :familyId AND babyId = :babyId LIMIT 1")
    suspend fun find(kind: String, localId: Long, familyId: String, babyId: String): SyncRecord?
    @Query("SELECT * FROM sync_records WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): SyncRecord?
    @Upsert suspend fun save(record: SyncRecord)
}
