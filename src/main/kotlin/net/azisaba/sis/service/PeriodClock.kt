package net.azisaba.sis.service

import net.azisaba.sis.model.ResetPeriod
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/**
 * Names the current limit period ("all", "2026-11-22", "2026-W47", "2026-11"). A count is stored per period, so a
 * limit "resets" simply because a new period has a new key; old rows are left alone.
 */
class PeriodClock(private val zone: ZoneId, private val resetHour: Int, private val now: () -> Instant = Instant::now) {

    /** The day the reset hour belongs to: 04:59 on the 22nd still counts as the 21st when resets are at 05:00. */
    private fun shifted(at: Instant): ZonedDateTime = at.atZone(zone).minusHours(resetHour.toLong())

    fun key(period: ResetPeriod, at: Instant = now()): String {
        val t = shifted(at)
        return when (period) {
            ResetPeriod.NEVER -> "all"
            ResetPeriod.DAILY -> "%04d-%02d-%02d".format(t.year, t.monthValue, t.dayOfMonth)
            ResetPeriod.WEEKLY -> "%04d-W%02d".format(t.get(IsoFields.WEEK_BASED_YEAR), t.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
            ResetPeriod.MONTHLY -> "%04d-%02d".format(t.year, t.monthValue)
        }
    }

    /** When the current period ends, or null for [ResetPeriod.NEVER]. */
    fun nextReset(period: ResetPeriod, at: Instant = now()): ZonedDateTime? {
        val day = shifted(at).toLocalDate()
        val nextDay = when (period) {
            ResetPeriod.NEVER -> return null
            ResetPeriod.DAILY -> day.plusDays(1)
            ResetPeriod.WEEKLY -> day.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            ResetPeriod.MONTHLY -> day.with(TemporalAdjusters.firstDayOfNextMonth())
        }
        return nextDay.atStartOfDay(zone).plusHours(resetHour.toLong())
    }
}
