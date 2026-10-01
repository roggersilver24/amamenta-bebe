package br.com.amamentabebe.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import br.com.amamentabebe.data.SettingsStore

class AlarmScheduler(private val context: Context, private val settings: SettingsStore) {
    private val manager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    suspend fun schedule(atMillis: Long): Boolean {
        cancel(clearSaved = false)
        val intent = pendingIntent()
        return try {
            if (canScheduleExact()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
            }
            settings.setAlarm(atMillis)
            true
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
            settings.setAlarm(atMillis)
            false
        }
    }

    suspend fun cancel(clearSaved: Boolean = true) {
        manager.cancel(pendingIntent())
        if (clearSaved) settings.setAlarm(null)
    }

    private fun pendingIntent() = PendingIntent.getBroadcast(
        context, REQUEST_CODE, Intent(context, FeedingAlarmReceiver::class.java).setAction(ACTION_ALARM),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object { const val ACTION_ALARM = "br.com.amamentabebe.ALARM"; const val REQUEST_CODE = 4101 }
}
