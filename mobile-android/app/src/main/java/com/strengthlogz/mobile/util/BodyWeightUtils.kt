package com.strengthlogz.mobile.util

import com.strengthlogz.mobile.model.BodyWeightEntry
import java.util.concurrent.ConcurrentHashMap

/**
 * Body weight lookups and total-load helpers.
 * Bodyweight-flagged exercises log ADDED load (BW = 0); total load = bodyweight on that date + added.
 */
object BodyWeightUtils {
    const val LB_PER_KG = 2.20462
    /** Fallback when no record exists after the latest entry: midpoint of 155-160 lb. */
    const val FALLBACK_KG = 157.5 / LB_PER_KG

    private data class SortedBwEntry(val isoDate: String, val weightKg: Double)

    fun lbsToKg(lb: Double): Double = Math.round(lb / LB_PER_KG * 10.0) / 10.0

    /** Weight on [date] using carry-forward of the latest earlier entry; earliest entry if date precedes all. */
    fun weightOn(date: String?, entries: List<BodyWeightEntry>, fallbackKg: Double = FALLBACK_KG): Double {
        if (entries.isEmpty()) return fallbackKg
        // Fast path when querying the process-wide published entries
        if (entries === publishedEntries) {
            return weightOnPublished(date, fallbackKg)
        }
        val iso = DateUtils.toSortableIso(date).ifBlank { DateUtils.toSortableIso(DateUtils.todayDdMonYyyy()) }
        var bestPriorIso: String? = null
        var bestPriorKg = 0.0
        var earliestIso: String? = null
        var earliestKg = fallbackKg

        for (e in entries) {
            val eIso = DateUtils.toSortableIso(e.date)
            if (earliestIso == null || eIso < earliestIso) {
                earliestIso = eIso
                earliestKg = e.weightKg
            }
            if (eIso <= iso && (bestPriorIso == null || eIso >= bestPriorIso)) {
                bestPriorIso = eIso
                bestPriorKg = e.weightKg
            }
        }
        return if (bestPriorIso != null) bestPriorKg else earliestKg
    }

    private fun weightOnPublished(date: String?, fallbackKg: Double = FALLBACK_KG): Double {
        val sorted = sortedPublishedEntries
        if (sorted.isEmpty()) return fallbackKg
        val iso = DateUtils.toSortableIso(date).ifBlank { DateUtils.toSortableIso(DateUtils.todayDdMonYyyy()) }
        publishedLookupCache[iso]?.let { return it }

        // Binary search for latest entry with isoDate <= iso
        var low = 0
        var high = sorted.size - 1
        var bestIdx = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val cmp = sorted[mid].isoDate.compareTo(iso)
            if (cmp <= 0) {
                bestIdx = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        val resolved = if (bestIdx >= 0) sorted[bestIdx].weightKg else sorted.first().weightKg
        if (publishedLookupCache.size < 2048) {
            publishedLookupCache[iso] = resolved
        }
        return resolved
    }

    fun totalLoad(isBodyweight: Boolean, addedKg: Double?, bodyWeightKg: Double): Double =
        if (isBodyweight) bodyWeightKg + (addedKg ?: 0.0) else (addedKg ?: 0.0)

    fun relativeStrength(e1rm: Double, bodyWeightKg: Double): Double =
        if (bodyWeightKg > 0.0 && e1rm > 0.0) e1rm / bodyWeightKg else 0.0

    // ---- Process-wide snapshot so pure UI helpers can resolve body weight by date in O(1) ----
    @Volatile private var publishedEntries: List<BodyWeightEntry> = emptyList()
    @Volatile private var sortedPublishedEntries: List<SortedBwEntry> = emptyList()
    @Volatile private var bodyweightExercises: Set<String> = emptySet()
    private val publishedLookupCache = ConcurrentHashMap<String, Double>(256)

    fun publish(newEntries: List<BodyWeightEntry>) {
        val copy = newEntries.toList()
        publishedEntries = copy
        sortedPublishedEntries = copy
            .map { SortedBwEntry(DateUtils.toSortableIso(it.date), it.weightKg) }
            .sortedBy { it.isoDate }
        publishedLookupCache.clear()
    }

    fun publishBodyweightExercises(names: Collection<String>) {
        bodyweightExercises = names.map { it.trim().lowercase() }.toSet()
    }

    fun isBodyweightExercise(name: String?): Boolean =
        name != null && name.trim().lowercase() in bodyweightExercises

    fun weightOn(date: String?): Double = weightOnPublished(date)
    fun latestKg(): Double = weightOnPublished(DateUtils.todayDdMonYyyy())
}
