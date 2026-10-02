package br.com.amamentabebe.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import br.com.amamentabebe.AmamentaApplication
import br.com.amamentabebe.MainActivity
import br.com.amamentabebe.data.AlarmStatus
import br.com.amamentabebe.R
import br.com.amamentabebe.alarm.NotificationActionReceiver
import br.com.amamentabebe.domain.FeedingCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FeedingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { refresh(context) } finally { pending?.finish() }
        }
    }

    companion object {
        // Updates only after changes, boot or launcher requests; no periodic polling.
        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FeedingWidget::class.java))
            if (ids.isEmpty()) return
            val app = context.applicationContext as AmamentaApplication
            val latest = app.repository.latest()
            val settings = app.settings.current()
            val formatter = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            fun time(value: Long?) = value?.let { formatter.format(Date(it)) } ?: "—"
            val next = latest?.let { FeedingCalculator.nextTime(it.timeMillis, settings.intervalMinutes) }
            val views = RemoteViews(context.packageName, R.layout.feeding_widget)
            views.setTextViewText(R.id.widget_latest, "Última alimentação: ${time(latest?.timeMillis)}")
            views.setTextViewText(R.id.widget_next, "Próxima alimentação: ${time(next)}")
            val status = when (settings.alarmStatus) {
                AlarmStatus.APPROXIMATE -> " (aproximado)"
                AlarmStatus.NOTIFICATIONS_BLOCKED -> " (notificações bloqueadas)"
                AlarmStatus.FAILED -> " (falha ao programar)"
                else -> ""
            }
            views.setTextViewText(R.id.widget_alarm, "Programado: ${time(settings.nextAlarmMillis)}$status")
            views.setOnClickPendingIntent(R.id.widget_register, PendingIntent.getBroadcast(context, 4301,
                Intent(context, NotificationActionReceiver::class.java).setAction(NotificationActionReceiver.ACTION_FEED),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 4302,
                Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            manager.updateAppWidget(ids, views)
        }
    }
}


