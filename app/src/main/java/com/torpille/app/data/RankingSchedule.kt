package com.torpille.app.data

import java.time.Instant
import java.time.ZoneId

data class WeeklyRankingWindow(val day: Int = 1, val startMinute: Int = 540, val endMinute: Int = 1320)
data class DatedRankingWindow(val startMs: Long = System.currentTimeMillis(), val endMs: Long = System.currentTimeMillis() + 3600000)
data class RankingSchedule(val fullTime: Boolean = true, val timeZone: String = ZoneId.systemDefault().id,
    val weekly: List<WeeklyRankingWindow> = emptyList(), val dates: List<DatedRankingWindow> = emptyList()) {
    fun isActive(now: Instant = Instant.now()): Boolean {
        if (fullTime) return true
        if (dates.any { now.toEpochMilli() >= it.startMs && now.toEpochMilli() < it.endMs }) return true
        return runCatching {
            val local = now.atZone(ZoneId.of(timeZone)); val day = local.dayOfWeek.value; val minute = local.hour * 60 + local.minute
            weekly.any { w -> if (w.startMinute < w.endMinute) day == w.day && minute >= w.startMinute && minute < w.endMinute
                else (day == w.day && minute >= w.startMinute) || (day == w.day % 7 + 1 && minute < w.endMinute) }
        }.getOrDefault(false)
    }
    fun payload(): Map<String, Any> = mapOf("fullTime" to fullTime, "timeZone" to timeZone,
        "weekly" to weekly.map { mapOf("day" to it.day, "startMinute" to it.startMinute, "endMinute" to it.endMinute) },
        "dates" to dates.map { mapOf("startMs" to it.startMs, "endMs" to it.endMs) })
}
