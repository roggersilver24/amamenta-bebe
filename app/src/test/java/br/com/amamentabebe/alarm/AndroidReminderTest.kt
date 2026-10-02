package br.com.amamentabebe.alarm

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.widget.TextView
import br.com.amamentabebe.AmamentaApplication
import br.com.amamentabebe.R
import br.com.amamentabebe.MainViewModel
import br.com.amamentabebe.data.FeedingType
import br.com.amamentabebe.data.BreastSide
import br.com.amamentabebe.domain.TimerSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain

import br.com.amamentabebe.data.AlarmStatus
import br.com.amamentabebe.data.Feeding
import br.com.amamentabebe.widget.FeedingWidget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = AmamentaApplication::class)
class AndroidReminderTest {
    private val app get() = RuntimeEnvironment.getApplication() as AmamentaApplication
    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java))

    @Test fun replacementKeepsOnlyOneAlarmAndCancelClearsState() = runBlocking {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val scheduler = AlarmScheduler(app, app.settings)
        scheduler.schedule(100_000)
        val first = alarms.scheduledAlarms.single().operation
        scheduler.schedule(200_000)
        assertEquals(1, alarms.scheduledAlarms.size)
        assertEquals(first, alarms.scheduledAlarms.single().operation)
        assertEquals(200_000L, alarms.scheduledAlarms.single().triggerAtTime)
        assertEquals(200_000L, app.settings.current().nextAlarmMillis)
        scheduler.cancel()
        assertTrue(alarms.scheduledAlarms.isEmpty())
        assertNull(app.settings.current().nextAlarmMillis)
        assertEquals(AlarmStatus.NONE, app.settings.current().alarmStatus)
    }

    @Test fun exactPermissionDenialSchedulesApproximateAndReportsIt() = runBlocking {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val scheduler = AlarmScheduler(app, app.settings)
        assertFalse(scheduler.schedule(300_000))
        assertEquals(1, alarms.scheduledAlarms.size)
        assertEquals(AlarmStatus.APPROXIMATE, app.settings.current().alarmStatus)
    }

    @Test fun rebootRestoresSavedSnoozeWithoutCreatingFeeding() = runBlocking {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val future = System.currentTimeMillis() + 600_000
        app.settings.setAlarm(future)
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        withTimeout(5_000) { while (alarms.scheduledAlarms.isEmpty()) delay(10) }
        assertEquals(future, alarms.scheduledAlarms.single().triggerAtTime)
        assertNull(app.repository.latest())
    }

    @Test fun notificationSnoozeDoesNotInsertPhantomFeeding() = runBlocking {
        NotificationActionReceiver().onReceive(app, Intent(NotificationActionReceiver.ACTION_SNOOZE_20))
        withTimeout(5_000) { while (app.settings.current().nextAlarmMillis == null) delay(10) }
        assertNull(app.repository.latest())
        val remaining = app.settings.current().nextAlarmMillis!! - System.currentTimeMillis()
        assertTrue(remaining in 1_190_000L..1_200_000L)
        assertEquals(1, alarms.scheduledAlarms.size)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun timerFinishRecoversPreviouslySavedRecordAfterProcessDeath() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val started = System.currentTimeMillis() - 600_000
            app.database.feedingDao().insert(Feeding(timeMillis = started, type = FeedingType.BREAST, side = BreastSide.LEFT, leftDurationMillis = 300_000))
            app.settings.setTimer(TimerSnapshot(started, BreastSide.LEFT, 0, 300_000, 0))
            MainViewModel(app).finishTimer().join()
            assertEquals(1, app.repository.feedings.first().size)
            assertFalse(app.settings.timer.first().active)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun widgetRemoteViewsRenderDataAndRefreshProgrammedTime() = runBlocking {
        val manager = shadowOf(AppWidgetManager.getInstance(app))
        val info = AppWidgetProviderInfo().apply { provider = ComponentName(app, FeedingWidget::class.java) }
        manager.addBoundWidget(99, info)
        FeedingWidget.refresh(app)
        var view = manager.getViewFor(99)
        assertEquals("Última alimentação: —", view.findViewById<TextView>(R.id.widget_latest).text.toString())
        app.database.feedingDao().insert(Feeding(timeMillis = 1_700_000_000_000))
        app.settings.setAlarm(1_700_000_600_000)
        app.settings.setAlarmStatus(AlarmStatus.APPROXIMATE)
        FeedingWidget.refresh(app)
        view = manager.getViewFor(99)
        assertFalse(view.findViewById<TextView>(R.id.widget_latest).text.toString().endsWith("—"))
        assertFalse(view.findViewById<TextView>(R.id.widget_next).text.toString().endsWith("—"))
        assertTrue(view.findViewById<TextView>(R.id.widget_alarm).text.toString().contains("aproximado"))
        assertTrue(view.findViewById<TextView>(R.id.widget_register).hasOnClickListeners())
    }
}

