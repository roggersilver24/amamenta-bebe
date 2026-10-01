package br.com.amamentabebe

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.amamentabebe.alarm.AlarmScheduler
import br.com.amamentabebe.data.*
import br.com.amamentabebe.domain.FeedingCalculator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UiState(val feedings: List<Feeding> = emptyList(), val settings: AppSettings = AppSettings())

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AmamentaApplication
    private val scheduler = AlarmScheduler(application, app.settings)
    val state: StateFlow<UiState> = combine(app.repository.feedings, app.settings.values, ::UiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun feedNow() = viewModelScope.launch {
        app.repository.quickAdd()?.let { feeding ->
            scheduler.schedule(FeedingCalculator.nextTime(feeding.timeMillis, app.repository.intervalMinutes()))
        }
    }

    fun update(feeding: Feeding) = viewModelScope.launch { app.repository.update(feeding); recalculateFromLatest() }
    fun delete(feeding: Feeding) = viewModelScope.launch { app.repository.delete(feeding); recalculateFromLatest() }
    fun setInterval(minutes: Int) = viewModelScope.launch { app.settings.setInterval(minutes); recalculateFromLatest() }
    fun setTheme(theme: ThemeMode) = viewModelScope.launch { app.settings.setTheme(theme) }
    fun setSound(value: Boolean) = viewModelScope.launch { app.settings.setSound(value) }
    fun setVibration(value: Boolean) = viewModelScope.launch { app.settings.setVibration(value) }

    private suspend fun recalculateFromLatest() {
        val latest = app.repository.latest()
        if (latest == null) scheduler.cancel()
        else scheduler.schedule(FeedingCalculator.nextTime(latest.timeMillis, app.repository.intervalMinutes()))
    }
}
