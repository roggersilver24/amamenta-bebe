package br.com.amamentabebe.alarm

import br.com.amamentabebe.data.AlarmStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderPolicyTest {
    @Test fun deniedNotificationsNeverReportSuccessfulReminder() {
        assertEquals(AlarmStatus.NOTIFICATIONS_BLOCKED, ReminderPolicy.status(AlarmStatus.EXACT, false))
        assertEquals(AlarmStatus.NOTIFICATIONS_BLOCKED, ReminderPolicy.status(AlarmStatus.APPROXIMATE, false))
        assertEquals(AlarmStatus.FAILED, ReminderPolicy.status(AlarmStatus.FAILED, false))
    }
    @Test fun fallbackRemainsVisible() {
        assertEquals(AlarmStatus.APPROXIMATE, ReminderPolicy.status(AlarmStatus.APPROXIMATE, true))
        assertEquals(AlarmStatus.EXACT, ReminderPolicy.status(AlarmStatus.EXACT, true))
    }
    @Test fun snoozeUsesCurrentTimeAndRequestedMinutes() {
        assertEquals(1_600_000L, ReminderPolicy.snoozeAt(1_000_000, 10))
        assertEquals(2_200_000L, ReminderPolicy.snoozeAt(1_000_000, 20))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnsupportedSnoozeDuration() {
        ReminderPolicy.snoozeAt(0, -1)
    }
}
