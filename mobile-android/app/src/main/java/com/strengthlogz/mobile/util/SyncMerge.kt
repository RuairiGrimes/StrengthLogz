package com.strengthlogz.mobile.util

import com.strengthlogz.mobile.model.BodyWeightEntry
import com.strengthlogz.mobile.model.ExerciseGoal
import com.strengthlogz.mobile.model.WorkoutProgram

/**
 * Pure merge helpers used when syncing BodyWeights / Goals / Programs with Google Sheets.
 *
 * Rules (per key):
 *  - key deleted locally while offline ([deletedLocally]) -> dropped everywhere
 *  - present on both sides -> [resolve] picks the winner
 *  - local only: if the key was present at the last successful sync ([lastSyncedKeys]) another device
 *    deleted it -> dropped; otherwise it is a new local item -> kept (and pushed)
 *  - remote only -> kept (new from another device)
 */
object SyncMerge {

    fun <T> mergeByKey(
        local: List<T>,
        remote: List<T>,
        key: (T) -> String,
        deletedLocally: Set<String>,
        lastSyncedKeys: Set<String>,
        resolve: (local: T, remote: T) -> T
    ): List<T> {
        val localMap = LinkedHashMap<String, T>()
        local.forEach { localMap[key(it)] = it }
        val remoteMap = LinkedHashMap<String, T>()
        remote.forEach { remoteMap[key(it)] = it }

        val out = ArrayList<T>()
        val allKeys = LinkedHashSet<String>().apply { addAll(localMap.keys); addAll(remoteMap.keys) }
        for (k in allKeys) {
            if (k in deletedLocally) continue
            val l = localMap[k]
            val r = remoteMap[k]
            when {
                l != null && r != null -> out.add(resolve(l, r))
                l != null -> if (k !in lastSyncedKeys) out.add(l)
                r != null -> out.add(r)
            }
        }
        return out
    }

    fun bodyWeightKey(e: BodyWeightEntry): String = DateUtils.toSortableIso(e.date)

    /** Manual readings beat automatic ones on the same date; otherwise the newer edit wins. */
    fun resolveBodyWeight(l: BodyWeightEntry, r: BodyWeightEntry): BodyWeightEntry = when {
        l.source == "manual" && r.source != "manual" -> l
        r.source == "manual" && l.source != "manual" -> r
        l.updatedAt >= r.updatedAt -> l
        else -> r
    }

    fun resolveGoal(l: ExerciseGoal, r: ExerciseGoal): ExerciseGoal = when {
        l.updatedAt > r.updatedAt -> l
        r.updatedAt > l.updatedAt -> r
        l.safeReports.size >= r.safeReports.size -> l
        else -> r
    }

    fun resolveProgram(l: WorkoutProgram, r: WorkoutProgram): WorkoutProgram =
        if (l.updatedAt >= r.updatedAt) l else r

    fun mergeBodyWeights(
        local: List<BodyWeightEntry>,
        remote: List<BodyWeightEntry>,
        deletedLocally: Set<String>,
        lastSyncedKeys: Set<String>
    ): List<BodyWeightEntry> =
        mergeByKey(local, remote, ::bodyWeightKey, deletedLocally, lastSyncedKeys, ::resolveBodyWeight)
            .sortedBy { bodyWeightKey(it) }

    fun mergeGoals(
        local: List<ExerciseGoal>,
        remote: List<ExerciseGoal>,
        deletedLocally: Set<String>,
        lastSyncedKeys: Set<String>
    ): List<ExerciseGoal> =
        mergeByKey(local, remote, { it.id }, deletedLocally, lastSyncedKeys, ::resolveGoal)

    fun mergePrograms(
        local: List<WorkoutProgram>,
        remote: List<WorkoutProgram>,
        deletedLocally: Set<String>,
        lastSyncedKeys: Set<String>
    ): List<WorkoutProgram> =
        mergeByKey(local, remote, { it.id }, deletedLocally, lastSyncedKeys, ::resolveProgram)
}
