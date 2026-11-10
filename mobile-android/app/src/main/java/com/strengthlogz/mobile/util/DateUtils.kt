package com.strengthlogz.mobile.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object DateUtils {
    private val MONTHS = arrayOf(
        "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
        "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
    )

    private val isoRegex = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""")
    private val ddMonYyyyRegex = Regex("""^(\d{1,2})-([A-Za-z]{3})-(\d{4})$""")

    private val ddMonYyyyCache = ConcurrentHashMap<String, String>(512)
    private val sortableIsoCache = ConcurrentHashMap<String, String>(512)
    private val dayOfWeekCache = ConcurrentHashMap<String, String>(256)
    private val displayWithDayFullCache = ConcurrentHashMap<String, String>(256)
    private val displayWithDayShortCache = ConcurrentHashMap<String, String>(256)
    private val epochMsCache = ConcurrentHashMap<String, Long>(256)

    /**
     * Converts any date string ("2026-04-14" or "14-APR-2026") into "DD-MON-YYYY" (e.g. "14-APR-2026").
     */
    fun toDdMonYyyy(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim()
        ddMonYyyyCache[trimmed]?.let { return it }

        val computed = computeDdMonYyyy(trimmed)
        if (ddMonYyyyCache.size < 4096) {
            ddMonYyyyCache[trimmed] = computed
        }
        return computed
    }

    private fun computeDdMonYyyy(trimmed: String): String {
        // Fast path for already-canonical "DD-MON-YYYY" (length 11, e.g. "14-APR-2026")
        if (trimmed.length == 11 && trimmed[2] == '-' && trimmed[6] == '-') {
            val mon = trimmed.substring(3, 6).uppercase(Locale.US)
            if (mon in MONTHS && trimmed[0].isDigit() && trimmed[1].isDigit()) {
                return "${trimmed.substring(0, 2)}-$mon-${trimmed.substring(7, 11)}"
            }
        }

        isoRegex.matchEntire(trimmed)?.let { m ->
            val year = m.groupValues[1].toIntOrNull() ?: return trimmed
            val month = m.groupValues[2].toIntOrNull() ?: return trimmed
            val day = m.groupValues[3].toIntOrNull() ?: return trimmed
            if (month in 1..12 && day in 1..31) {
                val dayStr = day.toString().padStart(2, '0')
                val monStr = MONTHS[month - 1]
                return "$dayStr-$monStr-$year"
            }
        }

        ddMonYyyyRegex.matchEntire(trimmed)?.let { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return trimmed.uppercase(Locale.US)
            val mon = m.groupValues[2].uppercase(Locale.US)
            val year = m.groupValues[3]
            if (mon in MONTHS) {
                return "${day.toString().padStart(2, '0')}-$mon-$year"
            }
        }

        return trimmed.uppercase(Locale.US)
    }

    /**
     * Converts either "14-APR-2026" or "2026-04-14" into sortable ISO "2026-04-14"
     * so chronological sorting and comparisons always work accurately.
     */
    fun toSortableIso(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim()
        sortableIsoCache[trimmed]?.let { return it }

        val computed = computeSortableIso(trimmed)
        if (sortableIsoCache.size < 4096) {
            sortableIsoCache[trimmed] = computed
        }
        return computed
    }

    private fun computeSortableIso(trimmed: String): String {
        // Fast path for canonical ISO "YYYY-MM-DD" (length 10)
        if (trimmed.length == 10 && trimmed[4] == '-' && trimmed[7] == '-') {
            if (trimmed[0].isDigit() && trimmed[5].isDigit() && trimmed[8].isDigit()) {
                return trimmed
            }
        }
        // Fast path for canonical "DD-MON-YYYY" (length 11)
        if (trimmed.length == 11 && trimmed[2] == '-' && trimmed[6] == '-') {
            val monStr = trimmed.substring(3, 6).uppercase(Locale.US)
            val monthIndex = MONTHS.indexOf(monStr)
            if (monthIndex >= 0 && trimmed[0].isDigit() && trimmed[1].isDigit()) {
                val month = (monthIndex + 1).toString().padStart(2, '0')
                val day = trimmed.substring(0, 2)
                val year = trimmed.substring(7, 11)
                return "$year-$month-$day"
            }
        }

        isoRegex.matchEntire(trimmed)?.let { m ->
            val year = m.groupValues[1]
            val month = m.groupValues[2].padStart(2, '0')
            val day = m.groupValues[3].padStart(2, '0')
            return "$year-$month-$day"
        }

        ddMonYyyyRegex.matchEntire(trimmed)?.let { m ->
            val day = m.groupValues[1].padStart(2, '0')
            val monStr = m.groupValues[2].uppercase(Locale.US)
            val year = m.groupValues[3]
            val monthIndex = MONTHS.indexOf(monStr)
            if (monthIndex >= 0) {
                val month = (monthIndex + 1).toString().padStart(2, '0')
                return "$year-$month-$day"
            }
        }

        return trimmed
    }

    /**
     * Converts any date string ("2026-04-14" or "14-APR-2026") to epoch milliseconds, cached.
     */
    fun toEpochMs(raw: String?): Long {
        val iso = toSortableIso(raw)
        if (iso.isBlank()) return 0L
        epochMsCache[iso]?.let { return it }

        val ms = try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
        if (epochMsCache.size < 4096) {
            epochMsCache[iso] = ms
        }
        return ms
    }

    /**
     * Returns today's date formatted as "DD-MON-YYYY", e.g., "16-OCT-2026".
     */
    fun todayDdMonYyyy(): String {
        val cal = Calendar.getInstance()
        val day = cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
        val mon = MONTHS[cal.get(Calendar.MONTH)]
        val year = cal.get(Calendar.YEAR)
        return "$day-$mon-$year"
    }

    /**
     * Returns current date & time formatted as "DD-MON-YYYY HH:mm", e.g., "16-OCT-2026 17:05".
     */
    fun nowTimestampDdMonYyyy(): String {
        val timePart = SimpleDateFormat("HH:mm", Locale.US).format(Date())
        return "${todayDdMonYyyy()} $timePart"
    }

    /**
     * Returns full day of the week (e.g. "Monday", "Tuesday") for any ISO or DD-MON-YYYY date.
     */
    fun getDayOfWeek(raw: String?): String {
        val iso = toSortableIso(raw)
        if (iso.isBlank()) return ""
        dayOfWeekCache[iso]?.let { return it }

        val dayStr = try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val d = sdf.parse(iso) ?: return ""
            SimpleDateFormat("EEEE", Locale.US).format(d)
        } catch (_: Exception) {
            ""
        }
        if (dayOfWeekCache.size < 2048) {
            dayOfWeekCache[iso] = dayStr
        }
        return dayStr
    }

    /**
     * Formats a date with day-of-week prefix, e.g. "Monday • 20-OCT-2026".
     */
    fun formatDisplayWithDay(raw: String?, shortDay: Boolean = false): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim()
        val cache = if (shortDay) displayWithDayShortCache else displayWithDayFullCache
        cache[trimmed]?.let { return it }

        val iso = toSortableIso(trimmed)
        if (iso.isBlank()) return ""
        val dayStr = if (!shortDay) {
            getDayOfWeek(iso)
        } else {
            try {
                val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso)
                if (d != null) SimpleDateFormat("EEE", Locale.US).format(d) else ""
            } catch (_: Exception) {
                ""
            }
        }
        val formattedDate = toDdMonYyyy(trimmed)
        val result = if (dayStr.isNotBlank()) "$dayStr • $formattedDate" else formattedDate
        if (cache.size < 2048) {
            cache[trimmed] = result
        }
        return result
    }
}
