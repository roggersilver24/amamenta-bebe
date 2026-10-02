package br.com.amamentabebe.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import br.com.amamentabebe.data.AlarmStatus
import br.com.amamentabebe.data.SettingsStore
import br.com.amamentabebe.widget.FeedingWidget

class AlarmScheduler(private val context: Context, private val settings: SettingsStore) {
    private val manager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    suspend fun schedule(atMillis: Long): Boolean {
        manager.cancel(pendingIntent())
        var status = AlarmStatus.FAILED
        try {
            if (canScheduleExact()) {
                try {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent())
                    status = AlarmStatus.EXACT
                } catch (_: SecurityException) {
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent())
                    status = AlarmStatus.APPROXIMATE
                }
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent())
                status = AlarmStatus.APPROXIMATE
            }
        } catch (_: SecurityException) {
            status = AlarmStatus.FAILED
        } catch (_: IllegalStateException) {
            status = AlarmStatus.FAILED
        }
        // Keep the intended time even on failure so permission recovery can retry it.
        settings.setAlarm(atMillis)
        settings.setAlarmStatus(ReminderPolicy.status(status, NotificationHelper.canNotify(context)))
        FeedingWidget.refresh(context)
        return status == AlarmStatus.EXACT && NotificationHelper.canNotify(context)
    }

    suspend fun cancel(clearSaved: Boolean = true) {
        manager.cancel(pendingIntent())
        if (clearSaved) {
            settings.setAlarm(null)
            settings.setAlarmStatus(AlarmStatus.NONE)
            FeedingWidget.refresh(context)
        }
    }

    private fun pendingIntent() = PendingIntent.getBroadcast(
        context, REQUEST_CODE, Intent(context, FeedingAlarmReceiver::class.java).setAction(ACTION_ALARM),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object { const val ACTION_ALARM = "br.com.amamentabebe.ALARM"; const val REQUEST_CODE = 4101 }
}
