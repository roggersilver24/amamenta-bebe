package br.com.amamentabebe.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedingCalculatorTest {
    @Test fun `two hours are added to last feeding`() = assertEquals(7_200_000L, FeedingCalculator.nextTime(0, 120))
    @Test fun `custom interval is supported`() = assertEquals(5_400_000L, FeedingCalculator.nextTime(0, 90))
    @Test fun `negative countdown is clamped`() = assertEquals(0, FeedingCalculator.remaining(1_000, 2_000))
}
