package br.com.amamentabebe.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.amamentabebe.AmamentaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FeedingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_ALARM) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val app = context.applicationContext as AmamentaApplication
            app.settings.setAlarm(null)
            NotificationHelper.show(context)
            result.finish()
        }
    }
}
