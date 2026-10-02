package br.com.amamentabebe.domain

import br.com.amamentabebe.data.BreastSide
import org.junit.Assert.*
import org.junit.Test

class FeedingTimerTest {
    @Test fun timestampsIncludeLockedScreenTime() {
        val restored = TimerSnapshot.start(1000, BreastSide.LEFT).copy()
        assertEquals(1_800_000L to 0L, restored.elapsed(1_801_000))
        assertTrue(restored.running)
    }
    @Test fun pauseResumeAndSwitchPreserveSeparateDurations() {
        val paused = TimerSnapshot.start(1000, BreastSide.LEFT).pause(11000)
        assertEquals(10000L to 0L, paused.elapsed(90000))
        val switched = paused.resume(100000).switch(105000)
        assertEquals(15000L to 4000L, switched.elapsed(109000))
    }
    @Test fun repeatedResumeAndBackwardClockDoNotAddTime() {
        val timer = TimerSnapshot.start(1000, BreastSide.RIGHT)
        assertEquals(timer, timer.resume(2000))
        assertEquals(0L to 0L, timer.elapsed(500))
    }
}
