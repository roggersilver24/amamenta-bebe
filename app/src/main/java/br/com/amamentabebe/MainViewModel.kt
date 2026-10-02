package br.com.amamentabebe

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import br.com.amamentabebe.alarm.AlarmScheduler
import br.com.amamentabebe.data.*
import br.com.amamentabebe.domain.FeedingCalculator
import br.com.amamentabebe.domain.TimerSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class UiState(val feedings: List<Feeding> = emptyList(), val settings: AppSettings = AppSettings(), val diapers: List<Diaper> = emptyList(), val timer: TimerSnapshot = TimerSnapshot(), val authors: Map<String, String> = emptyMap())
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AmamentaApplication
    private val scheduler = AlarmScheduler(application, app.settings)
    private val timerMutex = Mutex()
    private val diaperMutex = Mutex()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    val state: StateFlow<UiState> = combine(app.repository.feedings, app.settings.values, app.database.diaperDao().observeAll(), app.settings.timer, app.database.syncDao().observeAll()) { feedings, settings, diapers, timer, records -> UiState(feedings, settings, diapers, timer, records.filter { !it.deleted && it.authorName.isNotBlank() }.associate { "${it.kind}:${it.localId}" to it.authorName }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())
    fun clearMessage() { _message.value = null }
    private fun perform(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { _message.value = "Não foi possível concluir a operação. Confira o registro antes de tentar novamente. ${error.message.orEmpty().take(160)}" }
    }
    fun feedNow() = perform { app.repository.quickAdd()?.let { recalculateFromLatest() } }
    fun add(feeding: Feeding) = perform { app.repository.add(feeding)?.let { recalculateFromLatest() } }
    fun update(feeding: Feeding) = perform { app.repository.update(feeding); recalculateFromLatest() }
    fun delete(feeding: Feeding) = perform { app.repository.delete(feeding); recalculateFromLatest() }
    fun addDiaper(type: DiaperType) = perform { diaperMutex.withLock {
        val now = System.currentTimeMillis()
        val latest = app.database.diaperDao().observeAll().first().firstOrNull()
        if (latest == null || now - latest.timeMillis !in 0..2000) {
            app.database.withTransaction {
                val diaper = Diaper(timeMillis = now, type = type)
                val saved = diaper.copy(id = app.database.diaperDao().insert(diaper))
                app.familySync.diaper(saved, isNew = true)
            }
            app.requestSync()
        }
    } }
    fun updateDiaper(value: Diaper) = perform { app.database.withTransaction { app.database.diaperDao().update(value); app.familySync.diaper(value) }; app.requestSync() }
    fun deleteDiaper(value: Diaper) = perform { app.database.withTransaction { app.database.diaperDao().delete(value); app.familySync.diaper(value, deleted = true) }; app.requestSync() }
    fun setInterval(minutes: Int) = perform { app.settings.setInterval(minutes); recalculateFromLatest() }
    fun setTheme(theme: ThemeMode) = perform { app.settings.setTheme(theme) }
    fun setNightMode(mode: NightMode) = perform { app.settings.setNightMode(mode) }
    fun setSound(value: Boolean) = perform { app.settings.setSound(value) }
    fun setVibration(value: Boolean) = perform { app.settings.setVibration(value) }
    fun startTimer(side: BreastSide) = changeTimer { if (it.active) it else TimerSnapshot.start(System.currentTimeMillis(), side) }
    fun pauseTimer() = changeTimer { it.pause(System.currentTimeMillis()) }
    fun resumeTimer() = changeTimer { it.resume(System.currentTimeMillis()) }
    fun switchTimer() = changeTimer { it.switch(System.currentTimeMillis()) }
    private fun changeTimer(change: (TimerSnapshot) -> TimerSnapshot) = perform { timerMutex.withLock { app.settings.setTimer(change(app.settings.timer.first())) } }
    fun finishTimer() = perform { timerMutex.withLock {
        val timer = app.settings.timer.first()
        if (!timer.active) return@withLock
        val (left, right) = timer.elapsed(System.currentTimeMillis())
        val side = if (left > 0 && right > 0) BreastSide.BOTH else if (right > 0) BreastSide.RIGHT else timer.activeSide
        // Recover a completed save if the process stopped before DataStore cleared the timer.
        val alreadySaved = app.repository.feedings.first().any { it.type == FeedingType.BREAST && it.timeMillis == timer.startedAt }
        if (!alreadySaved) app.repository.add(Feeding(timeMillis = timer.startedAt, type = FeedingType.BREAST, side = side, leftDurationMillis = left, rightDurationMillis = right))
        app.settings.setTimer(TimerSnapshot())
        recalculateFromLatest()
    } }
    fun exportBackup(uri: Uri) = viewModelScope.launch { runCatching {
        withContext(Dispatchers.IO) {
            val json = BackupManager(app.database, app.settings).export()
            requireNotNull(app.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(json) }
        }
    }.onSuccess { _message.value = "Backup exportado." }.onFailure { if (it is CancellationException) throw it; _message.value = "Não foi possível exportar: ${it.message}" } }
    fun importBackup(uri: Uri) = viewModelScope.launch { runCatching {
        val result = withContext(Dispatchers.IO) {
            val json = requireNotNull(app.contentResolver.openInputStream(uri)).bufferedReader().use { reader ->
                val buffer = CharArray(8192); val text = StringBuilder()
                while (true) { val read = reader.read(buffer); if (read < 0) break; require(text.length + read <= 10_000_000) { "Arquivo muito grande" }; text.append(buffer, 0, read) }; text.toString()
            }
            BackupManager(app.database, app.settings).import(json)
        }
        if (app.settings.current().nextAlarmMillis == null && result.feedingsAdded > 0) recalculateFromLatest()
        br.com.amamentabebe.widget.FeedingWidget.refresh(app)
        "Backup restaurado: ${result.feedingsAdded} alimentações e ${result.diapersAdded} fraldas adicionadas."
    }.onSuccess { _message.value = it }.onFailure { if (it is CancellationException) throw it; _message.value = "Backup não importado: ${it.message}" } }
    private suspend fun recalculateFromLatest() {
        val latest = app.repository.latest()
        if (latest == null) scheduler.cancel()
        else scheduler.schedule(FeedingCalculator.nextTime(latest.timeMillis, app.repository.intervalMinutes()))
    }
}
