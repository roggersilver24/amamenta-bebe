package br.com.amamentabebe.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.amamentabebe.AmamentaApplication
import br.com.amamentabebe.data.AlarmStatus
import br.com.amamentabebe.widget.FeedingWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FeedingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_ALARM) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as AmamentaApplication
                app.settings.setAlarm(null)
                app.settings.setAlarmStatus(if (NotificationHelper.show(context)) AlarmStatus.NONE else AlarmStatus.NOTIFICATIONS_BLOCKED)
                FeedingWidget.refresh(context)
            } finally { result?.finish() }
        }
    }
}
