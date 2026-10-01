package br.com.amamentabebe.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FeedingRepository(private val dao: FeedingDao, private val settings: SettingsStore) {
    private val insertMutex = Mutex()
    val feedings: Flow<List<Feeding>> = dao.observeAll()

    suspend fun quickAdd(now: Long = System.currentTimeMillis()): Feeding? = insertMutex.withLock {
        val latest = dao.latest()
        if (latest != null && now - latest.timeMillis in 0..2_000) return null
        val feeding = Feeding(timeMillis = now)
        val id = dao.insert(feeding)
        feeding.copy(id = id)
    }

    suspend fun update(feeding: Feeding) = dao.update(feeding)
    suspend fun delete(feeding: Feeding) = dao.delete(feeding)
    suspend fun latest() = dao.latest()
    suspend fun intervalMinutes() = settings.current().intervalMinutes
}
