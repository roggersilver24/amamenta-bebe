package br.com.amamentabebe.alarm

import br.com.amamentabebe.data.AlarmStatus

object ReminderPolicy {
    fun status(scheduled: AlarmStatus, notificationsEnabled: Boolean): AlarmStatus =
        if (scheduled != AlarmStatus.FAILED && !notificationsEnabled) AlarmStatus.NOTIFICATIONS_BLOCKED else scheduled

    fun snoozeAt(now: Long, minutes: Int): Long {
        require(minutes == 10 || minutes == 20)
        return now + minutes * 60_000L
    }
}
