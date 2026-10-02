package br.com.amamentabebe.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.amamentabebe.AmamentaApplication
import br.com.amamentabebe.widget.FeedingWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as AmamentaApplication
                val alarm = app.settings.current().nextAlarmMillis
                if (alarm != null) AlarmScheduler(context, app.settings).schedule(maxOf(alarm, System.currentTimeMillis() + 1_000))
                FeedingWidget.refresh(context)
            } finally { result?.finish() }
        }
    }
}
