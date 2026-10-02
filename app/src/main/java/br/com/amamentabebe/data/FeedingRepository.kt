package br.com.amamentabebe.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.room.withTransaction
import br.com.amamentabebe.family.FamilySync

class FeedingRepository(private val dao: FeedingDao, private val settings: SettingsStore,
    private val database: AppDatabase, private val sync: FamilySync, private val changed: () -> Unit) {
    private val insertMutex = Mutex()
    val feedings: Flow<List<Feeding>> = dao.observeAll()

    suspend fun quickAdd(now: Long = System.currentTimeMillis()): Feeding? = insertMutex.withLock {
        require(now > 0)
        if (dao.countInWindow(now - 2_000, now) > 0) return null
        val feeding = Feeding(timeMillis = now)
        val saved = database.withTransaction { feeding.copy(id = dao.insert(feeding)).also { sync.feeding(it, isNew = true) } }
        changed(); saved
    }

    suspend fun update(feeding: Feeding) { database.withTransaction { dao.update(feeding); sync.feeding(feeding) }; changed() }
    suspend fun add(feeding: Feeding): Feeding? = insertMutex.withLock {
        require(feeding.timeMillis > 0 && feeding.leftDurationMillis >= 0 && feeding.rightDurationMillis >= 0)
        if (dao.atTime(feeding.timeMillis).any { it.copy(id = 0) == feeding.copy(id = 0) }) return null
        val latest = dao.latest()
        if (latest != null && feeding.timeMillis - latest.timeMillis in 0..2_000 &&
            latest.copy(id = 0, timeMillis = feeding.timeMillis) == feeding.copy(id = 0)) return null
        val saved = database.withTransaction { feeding.copy(id = dao.insert(feeding.copy(id = 0))).also { sync.feeding(it, isNew = true) } }
        changed(); saved
    }
    suspend fun delete(feeding: Feeding) { database.withTransaction { dao.delete(feeding); sync.feeding(feeding, deleted = true) }; changed() }
    suspend fun latest() = dao.latest()
    suspend fun intervalMinutes() = settings.current().intervalMinutes
}
