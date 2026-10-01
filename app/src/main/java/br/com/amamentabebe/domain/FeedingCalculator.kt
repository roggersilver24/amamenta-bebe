package br.com.amamentabebe.domain

object FeedingCalculator {
    fun nextTime(lastFeedingMillis: Long, intervalMinutes: Int): Long =
        lastFeedingMillis + intervalMinutes.coerceAtLeast(1) * 60_000L

    fun remaining(targetMillis: Long, nowMillis: Long): Long =
        (targetMillis - nowMillis).coerceAtLeast(0)
}
