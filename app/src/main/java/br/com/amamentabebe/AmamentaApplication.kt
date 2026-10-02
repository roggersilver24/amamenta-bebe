package br.com.amamentabebe

import android.app.Application
import br.com.amamentabebe.alarm.NotificationHelper
import br.com.amamentabebe.data.AppDatabase
import br.com.amamentabebe.data.FeedingRepository
import br.com.amamentabebe.data.SettingsStore
import br.com.amamentabebe.family.*

class AmamentaApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val settings by lazy { SettingsStore(this) }
    val family by lazy { FamilyService(this) }
    val familySync by lazy { FamilySync(database, family) }
    val repository by lazy { FeedingRepository(database.feedingDao(), settings, database, familySync, ::requestSync) }
    fun requestSync() { if (family.selection() != null) FamilySyncWorker.enqueue(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this, settings)
        if (family.configured) FamilySyncWorker.schedule(this)
    }
}
