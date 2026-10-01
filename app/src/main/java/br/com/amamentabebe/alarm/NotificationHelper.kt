package br.com.amamentabebe.alarm

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import br.com.amamentabebe.MainActivity
import br.com.amamentabebe.R
import br.com.amamentabebe.data.SettingsStore
import kotlinx.coroutines.runBlocking

object NotificationHelper {
    const val CHANNEL_ID = "feeding_reminders"
    const val NOTIFICATION_ID = 4201

    fun createChannel(context: Context, settingsStore: SettingsStore) {
        val values = runBlocking { settingsStore.current() }
        val manager = context.getSystemService(NotificationManager::class.java)
        val sound = if (values.sound) android.provider.Settings.System.DEFAULT_NOTIFICATION_URI else null
        val channel = NotificationChannel(CHANNEL_ID, "Lembretes de mamada", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Avisos no horário programado para a próxima mamada"
            enableVibration(values.vibration)
            if (sound != null) setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()) else setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context) {
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun actionIntent(action: String, request: Int) = PendingIntent.getBroadcast(
            context, request, Intent(context, NotificationActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🍼 Hora da próxima mamada")
            .setContentText("Está no horário programado para conferir a mamada do bebê.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Está no horário programado para conferir a mamada do bebê."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "MAMOU AGORA", actionIntent(NotificationActionReceiver.ACTION_FEED, 1))
            .addAction(0, "ADIAR 10 MIN", actionIntent(NotificationActionReceiver.ACTION_SNOOZE_10, 2))
            .addAction(0, "ADIAR 20 MIN", actionIntent(NotificationActionReceiver.ACTION_SNOOZE_20, 3))
            .build()
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    fun openNotificationSettings(context: Context) = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
    fun openExactAlarmSettings(context: Context) = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        .setData(Uri.parse("package:${context.packageName}"))
}
