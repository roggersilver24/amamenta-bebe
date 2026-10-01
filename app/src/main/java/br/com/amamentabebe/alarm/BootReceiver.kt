package br.com.amamentabebe.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.amamentabebe.AmamentaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val app = context.applicationContext as AmamentaApplication
            val alarm = app.settings.current().nextAlarmMillis
            if (alarm != null && alarm > System.currentTimeMillis()) AlarmScheduler(context, app.settings).schedule(alarm)
            else app.settings.setAlarm(null)
            result.finish()
        }
    }
}
