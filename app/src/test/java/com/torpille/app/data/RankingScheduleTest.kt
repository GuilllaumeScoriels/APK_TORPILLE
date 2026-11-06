package com.torpille.app.data
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
class RankingScheduleTest {
    @Test fun weeklyBoundariesUseCommunityZone() {
        val s = RankingSchedule(false, "Europe/Brussels", listOf(WeeklyRankingWindow(1, 540, 1080)))
        assertFalse(s.isActive(Instant.parse("2026-10-12T06:59:59Z")))
        assertTrue(s.isActive(Instant.parse("2026-10-12T07:00:00Z")))
        assertFalse(s.isActive(Instant.parse("2026-10-12T16:00:00Z")))
    }
    @Test fun overnightWrapsWeekAndDateWindowsAreUnion() {
        val start = Instant.parse("2026-10-14T10:00:00Z").toEpochMilli()
        val s = RankingSchedule(false, "UTC", listOf(WeeklyRankingWindow(7, 1320, 120)), listOf(DatedRankingWindow(start, start + 3600000)))
        assertTrue(s.isActive(Instant.parse("2026-10-12T01:59:59Z")))
        assertFalse(s.isActive(Instant.parse("2026-10-12T02:00:00Z")))
        assertTrue(s.isActive(Instant.ofEpochMilli(start)))
        assertFalse(s.isActive(Instant.ofEpochMilli(start + 3600000)))
    }
    @Test fun repeatedAutumnHourMatchesTwice() {
        val s = RankingSchedule(false, "Europe/Brussels", listOf(WeeklyRankingWindow(7, 120, 180)))
        assertTrue(s.isActive(Instant.parse("2026-10-25T00:30:00Z")))
        assertTrue(s.isActive(Instant.parse("2026-10-25T01:30:00Z")))
    }
}
