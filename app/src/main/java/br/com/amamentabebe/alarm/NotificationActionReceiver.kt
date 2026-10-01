package br.com.amamentabebe.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import br.com.amamentabebe.AmamentaApplication
import br.com.amamentabebe.domain.FeedingCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val app = context.applicationContext as AmamentaApplication
            val scheduler = AlarmScheduler(context, app.settings)
            when (intent.action) {
                ACTION_FEED -> app.repository.quickAdd()?.let { feeding ->
                    scheduler.schedule(FeedingCalculator.nextTime(feeding.timeMillis, app.repository.intervalMinutes()))
                }
                ACTION_SNOOZE_10 -> scheduler.schedule(System.currentTimeMillis() + 10 * 60_000L)
                ACTION_SNOOZE_20 -> scheduler.schedule(System.currentTimeMillis() + 20 * 60_000L)
            }
            NotificationManagerCompat.from(context).cancel(NotificationHelper.NOTIFICATION_ID)
            result.finish()
        }
    }
    companion object {
        const val ACTION_FEED = "br.com.amamentabebe.FEED_NOW"
        const val ACTION_SNOOZE_10 = "br.com.amamentabebe.SNOOZE_10"
        const val ACTION_SNOOZE_20 = "br.com.amamentabebe.SNOOZE_20"
    }
}
