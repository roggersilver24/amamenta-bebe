package br.com.amamentabebe.domain

import br.com.amamentabebe.data.BreastSide

/** Persisted timestamps, rather than screen ticks, keep elapsed time across process death. */
data class TimerSnapshot(
    val startedAt: Long = 0,
    val activeSide: BreastSide = BreastSide.LEFT,
    val runningSince: Long = 0,
    val leftMillis: Long = 0,
    val rightMillis: Long = 0
) {
    val active get() = startedAt > 0
    val running get() = runningSince > 0
    fun elapsed(now: Long): Pair<Long, Long> {
        val extra = if (running) (now - runningSince).coerceAtLeast(0) else 0
        return (leftMillis + if (activeSide == BreastSide.LEFT) extra else 0) to
            (rightMillis + if (activeSide == BreastSide.RIGHT) extra else 0)
    }
    fun pause(now: Long): TimerSnapshot {
        val (left, right) = elapsed(now)
        return copy(leftMillis = left, rightMillis = right, runningSince = 0)
    }
    fun resume(now: Long) = if (active && !running) copy(runningSince = now) else this
    fun switch(now: Long): TimerSnapshot = pause(now).copy(
        activeSide = if (activeSide == BreastSide.LEFT) BreastSide.RIGHT else BreastSide.LEFT,
        runningSince = if (running) now else 0
    )
    companion object { fun start(now: Long, side: BreastSide) = TimerSnapshot(now, side, now) }
}
