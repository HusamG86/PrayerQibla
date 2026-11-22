package com.sa.prayerqibla.widget

import com.sa.prayerqibla.TimeMath
import java.time.LocalDate
import java.time.ZoneId

data class NextPrayer(val index: Int, val atMillis: Long, val fromMillis: Long?)

object NextPrayerFinder {
    /** Sunrise (index 1) is shown in calendars but is never a prayer to count down to. */
    private val prayerIndexes = intArrayOf(0, 2, 3, 4, 5)

    fun find(
        nowMillis: Long,
        date: LocalDate,
        zone: ZoneId,
        todayHours: DoubleArray,
        tomorrowHours: DoubleArray
    ): NextPrayer? {
        if (todayHours.size < 6 || tomorrowHours.size < 6) return null
        val candidates = mutableListOf<Pair<Int, Long>>()
        listOf(date to todayHours, date.plusDays(1) to tomorrowHours).forEach { (day, hours) ->
            prayerIndexes.forEach { index ->
                candidates += index to TimeMath.localHoursToMillis(day, hours[index], zone)
            }
        }
        candidates.sortBy { it.second }
        for (position in candidates.indices) {
            val (index, atMillis) = candidates[position]
            if (atMillis > nowMillis) {
                return NextPrayer(index, atMillis, candidates.getOrNull(position - 1)?.second)
            }
        }
        return null
    }

    fun progress(nowMillis: Long, next: NextPrayer): Float {
        val from = next.fromMillis ?: return 0f
        if (next.atMillis <= from) return 0f
        return ((nowMillis - from).toFloat() / (next.atMillis - from)).coerceIn(0f, 1f)
    }

    fun cityOrFallback(name: String?, fallback: String): String =
        name?.takeIf { it.isNotBlank() }?.trim() ?: fallback
}
