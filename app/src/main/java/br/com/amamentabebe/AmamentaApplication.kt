package br.com.amamentabebe

import android.app.Application
import br.com.amamentabebe.alarm.NotificationHelper
import br.com.amamentabebe.data.AppDatabase
import br.com.amamentabebe.data.FeedingRepository
import br.com.amamentabebe.data.SettingsStore

class AmamentaApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val settings by lazy { SettingsStore(this) }
    val repository by lazy { FeedingRepository(database.feedingDao(), settings) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this, settings)
    }
}
