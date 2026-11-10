package com.strengthlogz.mobile.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.strengthlogz.mobile.model.ActiveWorkoutDraft
import com.strengthlogz.mobile.model.AppData
import com.strengthlogz.mobile.model.BodyWeightEntry
import com.strengthlogz.mobile.model.Exercise
import com.strengthlogz.mobile.model.ExerciseGoal
import com.strengthlogz.mobile.model.ProgramDay
import com.strengthlogz.mobile.model.ProgramExerciseItem
import com.strengthlogz.mobile.model.Workout
import com.strengthlogz.mobile.model.WorkoutProgram
import com.strengthlogz.mobile.model.WorkoutSet
import com.strengthlogz.mobile.util.BodyWeightUtils
import com.strengthlogz.mobile.util.DateUtils
import com.strengthlogz.mobile.util.GoalEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.Executors

private class RepositoryIndexes(
    val workoutsSorted: List<Workout> = emptyList(),
    val workoutById: Map<String, Workout> = emptyMap(),
    val setsByWorkoutId: Map<String, List<WorkoutSet>> = emptyMap(),
    val setsByExerciseLower: Map<String, List<WorkoutSet>> = emptyMap(),
    val previousSetsByExerciseLower: Map<String, List<WorkoutSet>> = emptyMap(),
    val distinctExercisesByFrequency: List<String> = emptyList(),
    val recentExercises: List<String> = emptyList()
)

class WorkoutRepository(private val context: Context) {
    private val gson = Gson()
    private val file = File(context.filesDir, "strengthlogz_data.json")
    private val prefs = context.getSharedPreferences("strengthlogz_prefs", Context.MODE_PRIVATE)
    private val ioExecutor = Executors.newSingleThreadExecutor()

    val sheetsClient = GoogleSheetsClient {
        getServiceAccountJson()
    }

    var appData: AppData = AppData()
        private set

    @Volatile
    private var indexes: RepositoryIndexes = RepositoryIndexes()

    @Volatile
    private var draftLoaded: Boolean = false
    @Volatile
    private var cachedDraft: ActiveWorkoutDraft? = null

    init {
        loadLocalData()
        ensureDefaultExercisesPresent()
        publishBodyWeights()
        rebuildIndexes()
    }

    // ---------------- In-memory Indexing ----------------

    @Synchronized
    private fun rebuildIndexes() {
        val workoutsSnap = appData.workouts.toList()
        val setsSnap = appData.sets.toList()

        val sortedWorkouts = workoutsSnap.sortedWith(
            compareByDescending<Workout> { DateUtils.toSortableIso(it.date) }
                .thenByDescending { it.id }
        )
        val wMap = workoutsSnap.associateBy { it.id }
        val byWorkout = setsSnap.groupBy { it.workoutId }
            .mapValues { (_, list) -> list.sortedBy { it.setNo } }

        val byExerciseLower = LinkedHashMap<String, MutableList<WorkoutSet>>()
        val canonicalNameByLower = LinkedHashMap<String, String>()
        for (s in setsSnap) {
            val trimmed = s.exercise.trim()
            if (trimmed.isEmpty()) continue
            val lower = trimmed.lowercase()
            canonicalNameByLower.putIfAbsent(lower, trimmed)
            byExerciseLower.getOrPut(lower) { ArrayList() }.add(s)
        }

        val prevByExerciseLower = HashMap<String, List<WorkoutSet>>(byExerciseLower.size)
        for ((lower, exSets) in byExerciseLower) {
            var latestIso = ""
            var latestWorkoutId: String? = null
            for (s in exSets) {
                val wDate = wMap[s.workoutId]?.date?.ifBlank { s.date } ?: s.date
                val iso = DateUtils.toSortableIso(wDate)
                if (latestWorkoutId == null || iso > latestIso || (iso == latestIso && s.workoutId > latestWorkoutId)) {
                    latestIso = iso
                    latestWorkoutId = s.workoutId
                }
            }
            if (latestWorkoutId != null) {
                prevByExerciseLower[lower] = exSets.filter { it.workoutId == latestWorkoutId }.sortedBy { it.setNo }
            }
        }

        val distinctByFreq = byExerciseLower.entries
            .sortedByDescending { it.value.size }
            .mapNotNull { canonicalNameByLower[it.key] }

        // Compute top 8 recent exercises using sorted workouts first, falling back to set dates
        val recentSet = LinkedHashSet<String>()
        for (w in sortedWorkouts) {
            val wSets = byWorkout[w.id] ?: continue
            for (s in wSets) {
                val exName = s.exercise.trim()
                if (exName.isNotEmpty()) {
                    recentSet.add(exName)
                    if (recentSet.size >= 12) break
                }
            }
            if (recentSet.size >= 12) break
        }
        if (recentSet.size < 8 && setsSnap.isNotEmpty()) {
            setsSnap.sortedByDescending { DateUtils.toSortableIso(it.date) }
                .forEach { s ->
                    if (s.exercise.isNotBlank()) recentSet.add(s.exercise.trim())
                }
        }

        indexes = RepositoryIndexes(
            workoutsSorted = sortedWorkouts,
            workoutById = wMap,
            setsByWorkoutId = byWorkout,
            setsByExerciseLower = byExerciseLower,
            previousSetsByExerciseLower = prevByExerciseLower,
            distinctExercisesByFrequency = distinctByFreq,
            recentExercises = recentSet.take(8)
        )
    }

    // ---------------- Body weight ----------------

    fun getBodyWeights(): List<BodyWeightEntry> = appData.bodyWeights ?: emptyList()

    private fun publishBodyWeights() {
        BodyWeightUtils.publish(getBodyWeights())
        BodyWeightUtils.publishBodyweightExercises(appData.exercises.filter { it.bodyweight }.map { it.name })
    }

    /** Call after the exercise catalog changes so bodyweight flags are current. */
    fun refreshBodyWeightExercises() = publishBodyWeights()

    /** Insert/replace the entry for a date. */
    @Synchronized
    fun upsertBodyWeights(newEntries: List<BodyWeightEntry>) {
        val map = linkedMapOf<String, BodyWeightEntry>()
        getBodyWeights().forEach { map[DateUtils.toSortableIso(it.date)] = it }
        newEntries.forEach { e ->
            val key = DateUtils.toSortableIso(e.date)
            val existing = map[key]
            // Manual entries are never overwritten by automatic sources
            if (existing == null || e.source == "manual" || existing.source != "manual") {
                map[key] = e.copy(date = DateUtils.toDdMonYyyy(e.date))
            }
        }
        appData.bodyWeights = map.toSortedMap().values.toMutableList()
        saveLocalDataAsync()
        publishBodyWeights()
    }

    fun addManualBodyWeight(date: String, kg: Double) =
        upsertBodyWeights(listOf(BodyWeightEntry(DateUtils.toDdMonYyyy(date), kg, "manual")))

    private fun normalizeDates(data: AppData): AppData {
        @Suppress("UNCHECKED_CAST")
        val rawWorkouts = (data.workouts as? List<Workout>) ?: emptyList()
        val workouts = rawWorkouts.map { w ->
            w.copy(date = DateUtils.toDdMonYyyy(w.date))
        }.toMutableList()

        @Suppress("UNCHECKED_CAST")
        val rawSets = (data.sets as? List<WorkoutSet>) ?: emptyList()
        val sets = rawSets.map { s ->
            s.copy(date = DateUtils.toDdMonYyyy(s.date))
        }.toMutableList()

        @Suppress("UNCHECKED_CAST")
        val rawExercises = (data.exercises as? List<Exercise>) ?: emptyList()
        val exercises = rawExercises.map { e ->
            e.copy(
                name = e.name.trim(),
                category = if (e.category.isBlank()) "Other" else e.category,
                bodyweight = e.bodyweight,
                description = e.safeDescription
            )
        }.toMutableList()

        @Suppress("UNCHECKED_CAST")
        val rawGoals = (data.goals as? List<ExerciseGoal>) ?: emptyList()
        val goals = rawGoals.map { g ->
            g.copy(
                exerciseName = g.exerciseName.trim(),
                goalType = g.safeGoalType,
                createdDate = DateUtils.toDdMonYyyy(g.safeCreatedDate),
                targetDate = g.safeTargetDate,
                notes = g.safeNotes,
                sessionReports = g.safeReports
            )
        }.toMutableList()

        return AppData(
            workouts = workouts,
            sets = sets,
            exercises = exercises,
            bodyWeights = data.bodyWeights ?: mutableListOf(),
            goals = goals,
            program = data.program
        )
    }

    fun loadDefaultExercises(): List<Exercise> {
        return try {
            context.assets.open("exercises.json").use { stream ->
                InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                    val listType = object : TypeToken<List<Exercise>>() {}.type
                    val raw: List<Exercise> = gson.fromJson(reader, listType) ?: emptyList()
                    raw.map { e ->
                        e.copy(
                            name = e.name.trim(),
                            category = e.category.ifBlank { "Other" },
                            bodyweight = e.bodyweight,
                            description = e.safeDescription
                        )
                    }.sortedBy { it.name.lowercase() }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    @Synchronized
    private fun ensureDefaultExercisesPresent() {
        val defaults = loadDefaultExercises()
        if (defaults.isEmpty()) return
        val existingLower = appData.exercises.map { it.name.trim().lowercase() }.toHashSet()
        var addedAny = false
        for (defEx in defaults) {
            val key = defEx.name.trim().lowercase()
            if (key.isNotEmpty() && key !in existingLower) {
                appData.exercises.add(defEx)
                existingLower.add(key)
                addedAny = true
            }
        }
        if (addedAny) {
            appData.exercises.sortBy { it.name.lowercase() }
            saveLocalDataAsync()
        }
    }

    private fun loadLocalData() {
        if (file.exists()) {
            try {
                val json = file.readText(Charsets.UTF_8)
                val loaded = gson.fromJson(json, AppData::class.java)
                if (loaded != null && (loaded.workouts.isNotEmpty() || loaded.exercises.isNotEmpty() || loaded.program != null)) {
                    appData = normalizeDates(loaded)
                    return
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // First launch: load built-in default exercises catalog cleanly (no personal workouts)
        appData = AppData(
            workouts = mutableListOf(),
            sets = mutableListOf(),
            exercises = loadDefaultExercises().toMutableList(),
            bodyWeights = mutableListOf(),
            goals = mutableListOf(),
            program = null,
            programs = mutableListOf(),
            activeProgramId = null
        )
        saveLocalDataAsync()
    }

    @Synchronized
    fun saveLocalData() {
        try {
            file.writeText(gson.toJson(appData), Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    private fun saveLocalDataAsync() {
        val snapshot = AppData(
            workouts = appData.workouts.toMutableList(),
            sets = appData.sets.toMutableList(),
            exercises = appData.exercises.toMutableList(),
            bodyWeights = appData.bodyWeights?.toMutableList() ?: mutableListOf(),
            goals = appData.goals?.toMutableList() ?: mutableListOf(),
            program = appData.program
        )
        ioExecutor.execute {
            try {
                file.writeText(gson.toJson(snapshot), Charsets.UTF_8)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getSpreadsheetId(): String {
        return prefs.getString("spreadsheet_id", "")?.trim() ?: ""
    }

    fun setSpreadsheetId(idOrUrl: String) {
        val extracted = GoogleSheetsClient.extractSheetId(idOrUrl)
        prefs.edit().putString("spreadsheet_id", extracted).apply()
    }

    fun getServiceAccountJson(): String {
        val saved = prefs.getString("service_account_json", "")?.trim() ?: ""
        if (saved.isNotEmpty()) return saved
        return try {
            context.assets.open("service_account.json").bufferedReader(Charsets.UTF_8).use { it.readText().trim() }
        } catch (_: Exception) {
            ""
        }
    }

    fun setServiceAccountJson(json: String) {
        prefs.edit().putString("service_account_json", json.trim()).apply()
        sheetsClient.invalidateToken()
    }

    fun getServiceAccountEmail(): String? {
        return GoogleSheetsClient.extractServiceAccountEmail(getServiceAccountJson())
    }

    fun isCloudConfigured(): Boolean {
        return getSpreadsheetId().isNotBlank() && getServiceAccountJson().isNotBlank()
    }

    fun clearCloudCredentials() {
        prefs.edit()
            .remove("spreadsheet_id")
            .remove("service_account_json")
            .apply()
        sheetsClient.invalidateToken()
    }

    fun getLastSyncTime(): String {
        return prefs.getString("last_sync_time", "Never") ?: "Never"
    }

    private fun setLastSyncTime(timeStr: String) {
        prefs.edit().putString("last_sync_time", timeStr).apply()
    }

    private fun getPendingDeletedIds(): MutableSet<String> {
        val set = prefs.getStringSet("pending_deleted_ids", emptySet()) ?: emptySet()
        return set.toMutableSet()
    }

    private fun savePendingDeletedIds(ids: Set<String>) {
        prefs.edit().putStringSet("pending_deleted_ids", ids).apply()
    }

    @Synchronized
    fun getActiveDraft(): ActiveWorkoutDraft? {
        if (draftLoaded) return cachedDraft
        draftLoaded = true
        val json = prefs.getString("active_workout_draft", null) ?: return null
        cachedDraft = try {
            gson.fromJson(json, ActiveWorkoutDraft::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        return cachedDraft
    }

    @Synchronized
    fun saveActiveDraft(draft: ActiveWorkoutDraft) {
        cachedDraft = draft
        draftLoaded = true
        ioExecutor.execute {
            try {
                val json = gson.toJson(draft)
                prefs.edit().putString("active_workout_draft", json).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Synchronized
    fun clearActiveDraft() {
        cachedDraft = null
        draftLoaded = true
        prefs.edit().remove("active_workout_draft").apply()
    }

    fun hasActiveDraft(): Boolean {
        val draft = getActiveDraft() ?: return false
        return draft.title.isNotBlank() || draft.notes.isNotBlank() || draft.exercises.isNotEmpty()
    }

    fun getWorkoutsSorted(): List<Workout> = indexes.workoutsSorted

    fun getSetsForWorkout(workoutId: String): List<WorkoutSet> =
        indexes.setsByWorkoutId[workoutId] ?: emptyList()

    fun getSetsForExercise(exerciseName: String): List<WorkoutSet> =
        indexes.setsByExerciseLower[exerciseName.trim().lowercase()] ?: emptyList()

    fun getPreviousSetsForExercise(exerciseName: String): List<WorkoutSet> =
        indexes.previousSetsByExerciseLower[exerciseName.trim().lowercase()] ?: emptyList()

    fun getDistinctExercisesByFrequency(): List<String> =
        indexes.distinctExercisesByFrequency

    fun getRecentExercises(): List<String> =
        indexes.recentExercises

    // ---------------- Exercise Goals ----------------

    fun getGoals(): List<ExerciseGoal> = appData.goals ?: emptyList()

    fun getGoalForExercise(exerciseName: String): ExerciseGoal? {
        val clean = exerciseName.trim()
        return getGoals().firstOrNull { it.exerciseName.equals(clean, ignoreCase = true) }
    }

    fun getActiveGoalForExercise(exerciseName: String): ExerciseGoal? {
        val clean = exerciseName.trim()
        return getGoals().firstOrNull { it.enabled && it.exerciseName.equals(clean, ignoreCase = true) }
    }

    @Synchronized
    fun saveGoal(goal: ExerciseGoal) {
        val list = (appData.goals ?: mutableListOf()).toMutableList()
        val existingIdx = list.indexOfFirst {
            it.id == goal.id || it.exerciseName.equals(goal.exerciseName.trim(), ignoreCase = true)
        }
        val cleanGoal = goal.copy(
            exerciseName = goal.exerciseName.trim(),
            goalType = goal.safeGoalType,
            createdDate = goal.safeCreatedDate.ifBlank { DateUtils.todayDdMonYyyy() }
        )
        if (existingIdx >= 0) {
            list[existingIdx] = cleanGoal
        } else {
            list.add(0, cleanGoal)
        }
        appData.goals = list
        saveLocalDataAsync()
    }

    @Synchronized
    fun deleteGoal(goalId: String) {
        val list = (appData.goals ?: mutableListOf()).toMutableList()
        list.removeAll { it.id == goalId }
        appData.goals = list
        saveLocalDataAsync()
    }

    @Synchronized
    fun toggleGoalEnabled(goalId: String) {
        val list = (appData.goals ?: mutableListOf()).toMutableList()
        val idx = list.indexOfFirst { it.id == goalId }
        if (idx >= 0) {
            val g = list[idx]
            list[idx] = g.copy(enabled = !g.enabled)
            appData.goals = list
            saveLocalDataAsync()
        }
    }

    @Synchronized
    fun saveWorkout(workout: Workout, sets: List<WorkoutSet>): List<ExerciseGoal> {
        val formattedDate = DateUtils.toDdMonYyyy(workout.date)
        val normWorkout = workout.copy(date = formattedDate)
        val normSets = sets.map { it.copy(date = formattedDate) }

        appData.workouts.removeAll { it.id == normWorkout.id }
        appData.sets.removeAll { it.workoutId == normWorkout.id }

        appData.workouts.add(0, normWorkout)
        appData.sets.addAll(normSets)

        // Ensure exercise exists in catalog
        normSets.map { it.exercise.trim() }.filter { it.isNotEmpty() }.distinct().forEach { name ->
            if (appData.exercises.none { it.name.equals(name, ignoreCase = true) }) {
                appData.exercises.add(Exercise(name = name, category = "Other"))
            }
        }
        appData.exercises.sortBy { it.name.lowercase() }
        rebuildIndexes()

        // Automatically update any goals matching exercises logged in this workout
        val updatedGoalsInSession = mutableListOf<ExerciseGoal>()
        val goalsList = (appData.goals ?: mutableListOf()).toMutableList()
        if (goalsList.isNotEmpty()) {
            val setsByExLower = normSets.groupBy { it.exercise.trim().lowercase() }
            for (i in goalsList.indices) {
                val g = goalsList[i]
                val loggedForEx = setsByExLower[g.exerciseName.trim().lowercase()]
                if (!loggedForEx.isNullOrEmpty()) {
                    val allExSets = getSetsForExercise(g.exerciseName)
                    val updatedGoal = GoalEngine.updateGoalWithLoggedSession(
                        goal = g,
                        workout = normWorkout,
                        workoutExerciseSets = loggedForEx,
                        allExerciseSetsAfterSave = allExSets,
                        allWorkoutsAfterSave = appData.workouts,
                        exercisesCatalog = appData.exercises,
                        bodyWeights = getBodyWeights()
                    )
                    goalsList[i] = updatedGoal
                    updatedGoalsInSession.add(updatedGoal)
                }
            }
            appData.goals = goalsList
        }

        saveLocalDataAsync()
        return updatedGoalsInSession
    }

    @Synchronized
    fun deleteWorkout(workoutId: String) {
        appData.workouts.removeAll { it.id == workoutId }
        appData.sets.removeAll { it.workoutId == workoutId }
        val pending = getPendingDeletedIds()
        pending.add(workoutId)
        savePendingDeletedIds(pending)
        rebuildIndexes()
        saveLocalDataAsync()
    }

    @Synchronized
    fun addExercise(exercise: Exercise) {
        val cleanName = exercise.name.trim()
        if (cleanName.isNotEmpty() && appData.exercises.none { it.name.equals(cleanName, ignoreCase = true) }) {
            appData.exercises.add(exercise.copy(name = cleanName))
            appData.exercises.sortBy { it.name.lowercase() }
            rebuildIndexes()
            saveLocalDataAsync()
            publishBodyWeights()
        }
    }

    @Synchronized
    fun updateExercise(oldName: String, updated: Exercise): Result<Unit> {
        val cleanNewName = updated.name.trim()
        if (cleanNewName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Exercise name cannot be empty"))
        }

        val isNameChanged = !oldName.equals(cleanNewName, ignoreCase = true)
        if (isNameChanged && appData.exercises.any { it.name.equals(cleanNewName, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("An exercise named \"$cleanNewName\" already exists"))
        }

        val idx = appData.exercises.indexOfFirst { it.name.equals(oldName, ignoreCase = true) }
        val updatedExercise = updated.copy(name = cleanNewName)
        if (idx >= 0) {
            appData.exercises[idx] = updatedExercise
        } else {
            appData.exercises.add(updatedExercise)
        }
        appData.exercises.sortBy { it.name.lowercase() }

        // Cascade rename across historical sets and goals to preserve analytics & PRs
        if (isNameChanged) {
            for (i in 0 until appData.sets.size) {
                val s = appData.sets[i]
                if (s.exercise.equals(oldName, ignoreCase = true)) {
                    appData.sets[i] = s.copy(exercise = cleanNewName)
                }
            }
            val goalsList = appData.goals
            if (goalsList != null) {
                for (i in 0 until goalsList.size) {
                    val g = goalsList[i]
                    if (g.exerciseName.equals(oldName, ignoreCase = true)) {
                        goalsList[i] = g.copy(exerciseName = cleanNewName)
                    }
                }
            }
            // Update exercise name across all stored programs
            val progs = appData.programs
            if (!progs.isNullOrEmpty()) {
                val updatedProgs = progs.map { prog ->
                    val updatedDays = prog.safeDays.map { d ->
                        val updatedExs = d.safeExercises.map { exItem ->
                            if (exItem.exerciseName.equals(oldName, ignoreCase = true)) {
                                exItem.copy(exerciseName = cleanNewName, category = updated.category.ifBlank { exItem.category })
                            } else exItem
                        }
                        d.copy(exercises = updatedExs)
                    }
                    prog.copy(days = updatedDays)
                }.toMutableList()
                appData.programs = updatedProgs
                appData.program = updatedProgs.firstOrNull { it.id == appData.activeProgramId } ?: updatedProgs.firstOrNull()
            } else if (appData.program != null) {
                val prog = appData.program!!
                val updatedDays = prog.safeDays.map { d ->
                    val updatedExs = d.safeExercises.map { exItem ->
                        if (exItem.exerciseName.equals(oldName, ignoreCase = true)) {
                            exItem.copy(exerciseName = cleanNewName, category = updated.category.ifBlank { exItem.category })
                        } else exItem
                    }
                    d.copy(exercises = updatedExs)
                }
                appData.program = prog.copy(days = updatedDays)
            }
        }

        rebuildIndexes()
        saveLocalDataAsync()
        publishBodyWeights()
        return Result.success(Unit)
    }

    // ---------------- Workout Programs ----------------

    fun getPrograms(): List<WorkoutProgram> {
        val list = appData.programs
        if (list != null && list.isNotEmpty()) {
            return list
        }
        val legacy = appData.program
        if (legacy != null && legacy.safeDays.isNotEmpty()) {
            val legacyId = if (legacy.id.isNotBlank()) legacy.id else java.util.UUID.randomUUID().toString()
            val migrated = mutableListOf(legacy.copy(id = legacyId))
            appData.programs = migrated
            if (appData.activeProgramId == null) {
                appData.activeProgramId = legacyId
            }
            saveLocalDataAsync()
            return migrated
        }
        // No defaults forced: user creates their programs from scratch
        return emptyList()
    }

    fun getActiveProgram(): WorkoutProgram? {
        val progs = getPrograms()
        if (progs.isEmpty()) return null
        val activeId = appData.activeProgramId
        return progs.firstOrNull { it.id == activeId } ?: progs.first()
    }

    fun getProgram(): WorkoutProgram? = getActiveProgram()

    fun setActiveProgramId(programId: String) {
        appData.activeProgramId = programId
        appData.program = appData.programs?.firstOrNull { it.id == programId }
        saveLocalDataAsync()
    }

    @Synchronized
    fun saveProgram(program: WorkoutProgram) {
        val progs = (appData.programs ?: mutableListOf()).toMutableList()
        val idx = progs.indexOfFirst { it.id == program.id }
        if (idx >= 0) {
            progs[idx] = program
        } else {
            progs.add(program)
        }
        appData.programs = progs
        if (appData.activeProgramId == null || appData.activeProgramId == program.id) {
            appData.activeProgramId = program.id
        }
        appData.program = program
        saveLocalDataAsync()
    }

    @Synchronized
    fun createProgram(name: String): WorkoutProgram {
        val cleanName = name.trim().ifBlank { "Custom Program" }
        val newProg = WorkoutProgram(
            id = java.util.UUID.randomUUID().toString(),
            programName = cleanName,
            days = emptyList(),
            createdAt = DateUtils.todayDdMonYyyy()
        )
        val progs = (appData.programs ?: mutableListOf()).toMutableList()
        progs.add(newProg)
        appData.programs = progs
        appData.activeProgramId = newProg.id
        appData.program = newProg
        saveLocalDataAsync()
        return newProg
    }

    @Synchronized
    fun duplicateProgram(programId: String): WorkoutProgram? {
        val progs = (appData.programs ?: mutableListOf()).toMutableList()
        val orig = progs.firstOrNull { it.id == programId } ?: return null
        val copy = orig.copy(
            id = java.util.UUID.randomUUID().toString(),
            programName = "${orig.safeProgramName} (Copy)",
            createdAt = DateUtils.todayDdMonYyyy()
        )
        progs.add(copy)
        appData.programs = progs
        appData.activeProgramId = copy.id
        appData.program = copy
        saveLocalDataAsync()
        return copy
    }

    @Synchronized
    fun deleteProgram(programId: String) {
        val progs = (appData.programs ?: mutableListOf()).toMutableList()
        progs.removeAll { it.id == programId }
        appData.programs = progs
        if (appData.activeProgramId == programId) {
            appData.activeProgramId = progs.firstOrNull()?.id
        }
        appData.program = progs.firstOrNull()
        saveLocalDataAsync()
    }

    companion object {
        fun buildDefaultProgram(daysPerWeek: Int): WorkoutProgram {
            val coerced = daysPerWeek.coerceIn(1, 7)
            val days = when (coerced) {
                1 -> listOf(
                    ProgramDay(
                        dayIndex = 0,
                        dayName = "Day 1: Full Body Power",
                        focus = "Compound Squat, Press, Pull & Posterior Chain",
                        exercises = listOf(
                            ProgramExerciseItem("Squat", "Legs", 3, 5, "Heavy compound lower"),
                            ProgramExerciseItem("Bench Press", "Chest", 3, 5, "Primary horizontal press"),
                            ProgramExerciseItem("Pull Ups", "Back", 3, 8, "Vertical pull stretch"),
                            ProgramExerciseItem("Overhead Press", "Shoulders", 3, 8, "Vertical shoulder press"),
                            ProgramExerciseItem("Romanian Deadlift", "Legs", 3, 8, "Posterior chain hinge")
                        )
                    )
                )
                2 -> listOf(
                    ProgramDay(
                        dayIndex = 0,
                        dayName = "Day 1: Upper Body",
                        focus = "Chest, Back, Shoulders & Arms",
                        exercises = listOf(
                            ProgramExerciseItem("Bench Press", "Chest", 3, 6),
                            ProgramExerciseItem("Pendlay Row", "Back", 3, 6),
                            ProgramExerciseItem("Overhead Press", "Shoulders", 3, 8),
                            ProgramExerciseItem("Pull Ups", "Back", 3, 8),
                            ProgramExerciseItem("Dips", "Chest", 3, 10)
                        )
                    ),
                    ProgramDay(
                        dayIndex = 1,
                        dayName = "Day 2: Lower Body & Core",
                        focus = "Quads, Hamstrings, Glutes & Hinge",
                        exercises = listOf(
                            ProgramExerciseItem("Squat", "Legs", 3, 5),
                            ProgramExerciseItem("Romanian Deadlift", "Legs", 3, 8),
                            ProgramExerciseItem("Bulgarian Split Squat", "Legs", 3, 8),
                            ProgramExerciseItem("Leg Curl", "Legs", 3, 10),
                            ProgramExerciseItem("Hanging Leg Raise", "Core", 3, 12)
                        )
                    )
                )
                3 -> listOf(
                    ProgramDay(
                        dayIndex = 0,
                        dayName = "Day 1: Push",
                        focus = "Chest, Shoulders & Triceps",
                        exercises = listOf(
                            ProgramExerciseItem("Bench Press", "Chest", 3, 5),
                            ProgramExerciseItem("Overhead Press", "Shoulders", 3, 8),
                            ProgramExerciseItem("Incline Dumbbell Press", "Chest", 3, 8),
                            ProgramExerciseItem("Lateral Raise", "Shoulders", 3, 12),
                            ProgramExerciseItem("Overhead Tricep Extension", "Arms", 3, 10)
                        )
                    ),
                    ProgramDay(
                        dayIndex = 1,
                        dayName = "Day 2: Pull",
                        focus = "Back, Rear Delts & Biceps",
                        exercises = listOf(
                            ProgramExerciseItem("Pull Ups", "Back", 3, 6),
                            ProgramExerciseItem("Pendlay Row", "Back", 3, 6),
                            ProgramExerciseItem("Band Face Pull", "Back", 3, 12),
                            ProgramExerciseItem("Fat Gripz Hammer Curl", "Arms", 3, 10),
                            ProgramExerciseItem("Barbell Curl", "Arms", 3, 10)
                        )
                    ),
                    ProgramDay(
                        dayIndex = 2,
                        dayName = "Day 3: Legs & Posterior Chain",
                        focus = "Quads, Hamstrings, Glutes & Calves",
                        exercises = listOf(
                            ProgramExerciseItem("Squat", "Legs", 3, 5),
                            ProgramExerciseItem("Romanian Deadlift", "Legs", 3, 8),
                            ProgramExerciseItem("Leg Press", "Legs", 3, 10),
                            ProgramExerciseItem("Leg Curl", "Legs", 3, 10),
                            ProgramExerciseItem("Hyperextension", "Legs", 3, 12)
                        )
                    )
                )
                4 -> listOf(
                    ProgramDay(
                        dayIndex = 0,
                        dayName = "Day 1: Upper Strength",
                        focus = "Heavy Chest, Back & Overhead Press",
                        exercises = listOf(
                            ProgramExerciseItem("Bench Press", "Chest", 4, 5),
                            ProgramExerciseItem("Pendlay Row", "Back", 4, 6),
                            ProgramExerciseItem("Overhead Press", "Shoulders", 3, 6),
                            ProgramExerciseItem("Chin Ups", "Back", 3, 8),
                            ProgramExerciseItem("Overhead Tricep Extension", "Arms", 3, 10)
                        )
                    ),
                    ProgramDay(
                        dayIndex = 1,
                        dayName = "Day 2: Lower Strength",
                        focus = "Heavy Squat, Hinge & Unilateral Quad",
                        exercises = listOf(
                            ProgramExerciseItem("Squat", "Legs", 4, 5),
                            ProgramExerciseItem("Romanian Deadlift", "Legs", 3, 8),
                            ProgramExerciseItem("Bulgarian Split Squat", "Legs", 3, 8),
                            ProgramExerciseItem("Leg Curl", "Legs", 3, 10),
                            ProgramExerciseItem("Ab Wheel Rollout", "Core", 3, 10)
                        )
                    ),
                    ProgramDay(
                        dayIndex = 2,
                        dayName = "Day 3: Upper Hypertrophy",
                        focus = "Incline Press, Vertical Pull, Delts & Arms",
                        exercises = listOf(
                            ProgramExerciseItem("Incline Bench Press", "Chest", 3, 8),
                            ProgramExerciseItem("Pull Ups", "Back", 3, 8),
                            ProgramExerciseItem("Dips", "Chest", 3, 10),
                            ProgramExerciseItem("Lateral Raise", "Shoulders", 3, 12),
                            ProgramExerciseItem("Fat Gripz Hammer Curl", "Arms", 3, 10)
                        )
                    ),
                    ProgramDay(
                        dayIndex = 3,
                        dayName = "Day 4: Lower Hypertrophy",
                        focus = "Deadlift / Hinge, Quad Volume & Posterior Chain",
                        exercises = listOf(
                            ProgramExerciseItem("Deadlift", "Legs", 3, 5),
                            ProgramExerciseItem("Leg Press", "Legs", 3, 10),
                            ProgramExerciseItem("Good Morning", "Legs", 3, 8),
                            ProgramExerciseItem("Leg Extension", "Legs", 3, 12),
                            ProgramExerciseItem("Hyperextension", "Legs", 3, 12)
                        )
                    )
                )
                5 -> listOf(
                    ProgramDay(
                        dayIndex = 0,
                        dayName = "Day 1: Upper Power",
                        focus = "Heavy Compound Upper Body",
                        exercises = listOf(
                            ProgramExerciseItem("Bench Press", "Chest", 4, 5),
                            ProgramExerciseItem("Pendlay Row", "Back", 4, 5),
                            ProgramExerciseItem("Overhead Press", "Shoulders", 3, 6),
                            ProgramExerciseItem("Chin Ups", "Back", 3, 6)
                        )
                    ),
                    ProgramDay(
                        dayIndex = 1,
                        dayName = "Day 2: Lower Power",
                        focus = "Heavy Squat & Posterior Chain",
                        exercises = listOf(
                            ProgramExerciseItem("Squat", "Legs", 4, 5),
                            ProgramExerciseItem("Romanian Deadlift", "Legs", 3, 8),
                            ProgramExerciseItem("Leg Press", "Legs", 3, 8),
                            ProgramExerciseItem("Leg Curl", "Legs", 3, 10)
                        )
                    ),
                    ProgramDay(
                        dayIndex = 2,
                        dayName = "Day 3: Push Hypertrophy",
                        focus = "Chest, Shoulders & Triceps Volume",
                        exercises = listOf(
                            ProgramExerciseItem("Incline Dumbbell Press", "Chest", 3, 8),
                            ProgramExerciseItem("Dips", "Chest", 3, 10),
                            ProgramExerciseItem("Seated Dumbbell Press", "Shoulders", 3, 10),
                            ProgramExerciseItem("Lateral Raise", "Shoulders", 3, 12),
                            ProgramExerciseItem("Cable Tricep Pushdown", "Arms", 3, 12)
                        )
                    ),
                    ProgramDay(
                        dayIndex = 3,
                        dayName = "Day 4: Pull Hypertrophy",
                        focus = "Back Width, Thickness, Rear Delts & Biceps",
                        exercises = listOf(
                            ProgramExerciseItem("Pull Ups", "Back", 3, 8),
                            ProgramExerciseItem("Chest Supported Dumbbell Row", "Back", 3, 10),
                            ProgramExerciseItem("Band Face Pull", "Back", 3, 15),
                            ProgramExerciseItem("Barbell Curl", "Arms", 3, 10),
                            ProgramExerciseItem("Fat Gripz Hammer Curl", "Arms", 3, 10)
                        )
                    ),
                    ProgramDay(
                        dayIndex = 4,
                        dayName = "Day 5: Legs Hypertrophy",
                        focus = "Unilateral Quads, Hinge & Core",
                        exercises = listOf(
                            ProgramExerciseItem("Bulgarian Split Squat", "Legs", 3, 8),
                            ProgramExerciseItem("Good Morning", "Legs", 3, 8),
                            ProgramExerciseItem("Leg Extension", "Legs", 3, 12),
                            ProgramExerciseItem("Hyperextension", "Legs", 3, 12),
                            ProgramExerciseItem("Hanging Leg Raise", "Core", 3, 12)
                        )
                    )
                )
                else -> (0 until coerced).map { idx ->
                    val splitCycle = idx % 3
                    when (splitCycle) {
                        0 -> ProgramDay(
                            dayIndex = idx,
                            dayName = "Day ${idx + 1}: Push ${if (idx >= 3) "B" else "A"}",
                            focus = "Chest, Shoulders & Triceps",
                            exercises = listOf(
                                ProgramExerciseItem(if (idx < 3) "Bench Press" else "Incline Bench Press", "Chest", 3, 6),
                                ProgramExerciseItem(if (idx < 3) "Overhead Press" else "Seated Dumbbell Press", "Shoulders", 3, 8),
                                ProgramExerciseItem("Dips", "Chest", 3, 8),
                                ProgramExerciseItem("Lateral Raise", "Shoulders", 3, 12),
                                ProgramExerciseItem("Overhead Tricep Extension", "Arms", 3, 10)
                            )
                        )
                        1 -> ProgramDay(
                            dayIndex = idx,
                            dayName = "Day ${idx + 1}: Pull ${if (idx >= 3) "B" else "A"}",
                            focus = "Back, Rear Delts & Biceps",
                            exercises = listOf(
                                ProgramExerciseItem(if (idx < 3) "Pull Ups" else "Chin Ups", "Back", 3, 6),
                                ProgramExerciseItem(if (idx < 3) "Pendlay Row" else "Barbell Row", "Back", 3, 8),
                                ProgramExerciseItem("Band Face Pull", "Back", 3, 12),
                                ProgramExerciseItem("Barbell Curl", "Arms", 3, 10),
                                ProgramExerciseItem("Fat Gripz Hammer Curl", "Arms", 3, 10)
                            )
                        )
                        else -> ProgramDay(
                            dayIndex = idx,
                            dayName = "Day ${idx + 1}: Legs ${if (idx >= 3) "B" else "A"}",
                            focus = "Quads, Hamstrings & Posterior Chain",
                            exercises = listOf(
                                ProgramExerciseItem(if (idx < 3) "Squat" else "Deadlift", "Legs", 3, 5),
                                ProgramExerciseItem(if (idx < 3) "Romanian Deadlift" else "Leg Press", "Legs", 3, 8),
                                ProgramExerciseItem("Bulgarian Split Squat", "Legs", 3, 8),
                                ProgramExerciseItem("Leg Curl", "Legs", 3, 10),
                                ProgramExerciseItem("Hyperextension", "Legs", 3, 12)
                            )
                        )
                    }
                }
            }
            return WorkoutProgram(
                daysPerWeek = coerced,
                programName = "$coerced-Day Training Split",
                days = days,
                lastLoadedDayIndex = -1
            )
        }
    }

    suspend fun backupExercisesToGoogleSheets(
        renamedFrom: String? = null,
        renamedTo: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val spreadsheetId = getSpreadsheetId()
        if (spreadsheetId.isBlank()) {
            return@withContext Result.failure(Exception("Spreadsheet ID is not configured in Settings"))
        }
        if (getServiceAccountJson().isBlank()) {
            return@withContext Result.failure(Exception("Service Account JSON key is not configured in Settings"))
        }

        try {
            // If an exercise was renamed, update occurrences in Google Sheets Sets
            if (!renamedFrom.isNullOrBlank() && !renamedTo.isNullOrBlank()) {
                sheetsClient.renameExerciseInSets(spreadsheetId, renamedFrom, renamedTo)
            }

            // Sync current exercise catalog to Google Sheets
            val listCopy = synchronized(this@WorkoutRepository) {
                appData.exercises.toList()
            }
            sheetsClient.overwriteExercises(spreadsheetId, listCopy)

            val stamp = DateUtils.nowTimestampDdMonYyyy()
            setLastSyncTime(stamp)
            return@withContext Result.success("Backed up ${listCopy.size} exercises to Google Sheets")
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Result.failure(e)
        }
    }

    suspend fun syncWithGoogleSheets(): Result<String> = withContext(Dispatchers.IO) {
        val spreadsheetId = getSpreadsheetId()
        if (spreadsheetId.isBlank()) {
            return@withContext Result.failure(Exception("Spreadsheet ID is empty. Add your Google Sheet in Settings."))
        }
        if (getServiceAccountJson().isBlank()) {
            return@withContext Result.failure(Exception("Service Account JSON key is missing. Add your JSON key in Settings."))
        }

        try {
            // 1. Push any pending deletions first
            val pendingDeletes = getPendingDeletedIds()
            if (pendingDeletes.isNotEmpty()) {
                sheetsClient.deleteWorkouts(spreadsheetId, pendingDeletes)
                savePendingDeletedIds(emptySet())
            }

            // 2. Fetch current state from Google Sheets
            val remoteData = normalizeDates(sheetsClient.loadAll(spreadsheetId))
            val remoteWorkoutIds = remoteData.workouts.map { it.id }.toSet()

            // 3. Push any local workouts that aren't in Google Sheets yet
            val localOnlyWorkouts = appData.workouts.filter { it.id !in remoteWorkoutIds && it.id !in pendingDeletes }
            val localOnlyWorkoutIds = localOnlyWorkouts.map { it.id }.toSet()
            val localOnlySets = appData.sets.filter { it.workoutId in localOnlyWorkoutIds }

            if (localOnlyWorkouts.isNotEmpty()) {
                sheetsClient.addWorkouts(spreadsheetId, localOnlyWorkouts, localOnlySets)
            }

            // 4. Merge exercises (built-in defaults + remote + local)
            val exerciseMap = linkedMapOf<String, Exercise>()
            loadDefaultExercises().forEach { exerciseMap[it.name.lowercase()] = it }
            remoteData.exercises.forEach { exerciseMap[it.name.lowercase()] = it }
            appData.exercises.forEach { localEx ->
                val existing = exerciseMap[localEx.name.lowercase()]
                if (existing != null) {
                    val desc = if (!localEx.description.isNullOrBlank()) localEx.safeDescription else existing.safeDescription
                    exerciseMap[localEx.name.lowercase()] = localEx.copy(description = desc)
                } else {
                    exerciseMap[localEx.name.lowercase()] = localEx
                }
            }

            val mergedWorkouts = (remoteData.workouts + localOnlyWorkouts)
                .distinctBy { it.id }
                .sortedWith(
                    compareByDescending<Workout> { DateUtils.toSortableIso(it.date) }
                        .thenByDescending { it.id }
                )
                .toMutableList()

            val mergedSets = (remoteData.sets + localOnlySets)
                .distinctBy { it.id }
                .toMutableList()

            // Ensure any exercise referenced in sets is in the catalog
            mergedSets.forEach { s ->
                val key = s.exercise.trim().lowercase()
                if (key.isNotEmpty() && !exerciseMap.containsKey(key)) {
                    exerciseMap[key] = Exercise(name = s.exercise.trim(), category = "Other")
                }
            }

            val mergedExercises = exerciseMap.values.sortedBy { it.name.lowercase() }.toMutableList()

            // Backup merged exercises to Google Sheets
            try {
                sheetsClient.overwriteExercises(spreadsheetId, mergedExercises)
            } catch (exErr: Exception) {
                exErr.printStackTrace()
            }

            synchronized(this@WorkoutRepository) {
                appData = AppData(
                    workouts = mergedWorkouts,
                    sets = mergedSets,
                    exercises = mergedExercises,
                    bodyWeights = appData.bodyWeights,
                    goals = appData.goals,
                    program = appData.program
                )
                rebuildIndexes()
                saveLocalDataAsync()
                publishBodyWeights()
            }

            val stamp = DateUtils.nowTimestampDdMonYyyy()
            setLastSyncTime(stamp)

            val pushedNote = if (localOnlyWorkouts.isNotEmpty()) " (pushed ${localOnlyWorkouts.size} new)" else ""
            return@withContext Result.success(
                "Synced ${mergedWorkouts.size} workouts & ${mergedSets.size} sets$pushedNote"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Result.failure(e)
        }
    }
}
