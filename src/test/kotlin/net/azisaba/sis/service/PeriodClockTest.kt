package net.azisaba.sis.service

import net.azisaba.sis.model.ResetPeriod
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PeriodClockTest {
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private fun at(text: String) = java.time.ZonedDateTime.parse(text).toInstant()

    @Test
    fun `daily key changes at the reset hour, not at midnight`() {
        val clock = PeriodClock(tokyo, 5)
        assertEquals("2026-11-21", clock.key(ResetPeriod.DAILY, at("2026-11-22T04:59:59+09:00")))
        assertEquals("2026-11-22", clock.key(ResetPeriod.DAILY, at("2026-11-22T05:00:00+09:00")))
    }

    @Test
    fun `weekly key uses ISO weeks starting on Monday at the reset hour`() {
        val clock = PeriodClock(tokyo, 5)
        // 2026-11-23 is a Monday
        assertEquals(clock.key(ResetPeriod.WEEKLY, at("2026-11-17T12:00:00+09:00")), clock.key(ResetPeriod.WEEKLY, at("2026-11-23T04:00:00+09:00")))
        assertEquals("2026-W48", clock.key(ResetPeriod.WEEKLY, at("2026-11-23T05:00:00+09:00")))
    }

    @Test
    fun `monthly and never`() {
        val clock = PeriodClock(tokyo, 0)
        assertEquals("2026-12", clock.key(ResetPeriod.MONTHLY, at("2026-12-01T00:00:00+09:00")))
        assertEquals("all", clock.key(ResetPeriod.NEVER, Instant.EPOCH))
    }

    @Test
    fun `next reset`() {
        val clock = PeriodClock(tokyo, 5)
        val now = at("2026-11-22T10:00:00+09:00")
        assertEquals(at("2026-11-23T05:00:00+09:00"), clock.nextReset(ResetPeriod.DAILY, now)!!.toInstant())
        assertEquals(at("2026-11-23T05:00:00+09:00"), clock.nextReset(ResetPeriod.WEEKLY, now)!!.toInstant())
        assertEquals(at("2026-12-01T05:00:00+09:00"), clock.nextReset(ResetPeriod.MONTHLY, now)!!.toInstant())
        assertNull(clock.nextReset(ResetPeriod.NEVER, now))
    }
}
