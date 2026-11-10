package com.strengthlogz.mobile

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.strengthlogz.mobile.data.GoogleSheetsClient
import com.strengthlogz.mobile.data.WorkoutRepository
import com.strengthlogz.mobile.model.AppData
import com.strengthlogz.mobile.model.Exercise
import com.strengthlogz.mobile.model.Workout
import com.strengthlogz.mobile.model.WorkoutSet
import com.strengthlogz.mobile.ui.buildSessionPoints
import com.strengthlogz.mobile.ui.computeEpley
import com.strengthlogz.mobile.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GoogleSheetsSyncTest {

    @Test
    fun verifyDdMonYyyyFormattingAndChronologicalSorting() {
        assertEquals("14-APR-2026", DateUtils.toDdMonYyyy("2026-04-14"))
        assertEquals("05-JAN-2025", DateUtils.toDdMonYyyy("2025-1-5"))
        assertEquals("16-OCT-2026", DateUtils.toDdMonYyyy("16-oct-2026"))
        assertEquals("2026-04-14", DateUtils.toSortableIso("14-APR-2026"))
        assertEquals("2025-01-05", DateUtils.toSortableIso("05-JAN-2025"))
    }

    @Test
    fun verifyFailedZeroRepSetsNeverCountAsPrOrTopWeight() {
        assertEquals(0.0, computeEpley(115.0, 0), 0.001)

        val workouts = listOf(
            Workout("w-1", "2026-01-12", "Push"),
            Workout("w-2", "2025-11-26", "Pull 1"),
            Workout("w-3", "2025-12-01", "Pull 2"),
            Workout("w-4", "2026-01-23", "Pull 3")
        )
        val workoutMap = workouts.associateBy { it.id }

        // 1. Bench Press: 12-JAN-2026 had 115x0 (FAILED), 112.5x1, 100x8, 100x8
        val benchSets = listOf(
            WorkoutSet("s1", "w-1", "2026-01-12", "Bench Press", 1, 115.0, 0),
            WorkoutSet("s2", "w-1", "2026-01-12", "Bench Press", 2, 112.5, 1),
            WorkoutSet("s3", "w-1", "2026-01-12", "Bench Press", 3, 100.0, 8),
            WorkoutSet("s4", "w-1", "2026-01-12", "Bench Press", 4, 100.0, 8)
        )
        val benchSessions = buildSessionPoints(benchSets, workoutMap)
        val jan12Bench = benchSessions.first { it.isoDate == "2026-01-12" }

        // Heaviest completed set must be 112.5 x 1 (NOT 115 x 0!)
        assertEquals(112.5, jan12Bench.topWeight, 0.001)
        assertEquals(1, jan12Bench.topWeightReps)

        // Best E1RM must be 126.5kg sourced from 100kg x 8 (NOT 115 x 0!)
        assertEquals(126.5, jan12Bench.e1rm, 0.001)
        assertEquals(100.0, jan12Bench.e1rmWeight, 0.001)
        assertEquals(8, jan12Bench.e1rmReps)

        // All-time Bench Press heaviest weight must be 112.5kg (never 115kg)
        val allTimeBenchMaxWeight = benchSessions.maxOf { it.topWeight }
        assertEquals(112.5, allTimeBenchMaxWeight, 0.001)

        // 2. Deadlift: 01-DEC-2025 had 190x0 (FAILED) and 23-JAN-2026 had 180x0 (FAILED)
        val deadliftSets = listOf(
            WorkoutSet("d1", "w-2", "2025-11-26", "Deadlift", 1, 180.0, 1),
            WorkoutSet("d2", "w-3", "2025-12-01", "Deadlift", 1, 190.0, 0),
            WorkoutSet("d3", "w-3", "2025-12-01", "Deadlift", 2, 150.0, 2),
            WorkoutSet("d4", "w-4", "2026-01-23", "Deadlift", 1, 180.0, 0),
            WorkoutSet("d5", "w-4", "2026-01-23", "Deadlift", 2, 160.0, 3)
        )
        val deadliftSessions = buildSessionPoints(deadliftSets, workoutMap)
        val dec01Dl = deadliftSessions.first { it.isoDate == "2025-12-01" }
        assertEquals(150.0, dec01Dl.topWeight, 0.001)
        assertEquals(2, dec01Dl.topWeightReps)

        val jan23Dl = deadliftSessions.first { it.isoDate == "2026-01-23" }
        assertEquals(160.0, jan23Dl.topWeight, 0.001)
        assertEquals(3, jan23Dl.topWeightReps)

        // All-time Deadlift heaviest completed weight must be 180x1 on 26-NOV-2025 (never 190x0)
        val allTimeDlMaxWeight = deadliftSessions.maxOf { it.topWeight }
        assertEquals(180.0, allTimeDlMaxWeight, 0.001)
    }

    @Test
    fun verifyLegacyExerciseJsonWithoutDescriptionDoesNotCrash() {
        val legacyJson = """{"workouts":[],"sets":[],"exercises":[{"name":"Bench Press","category":"Chest","bodyweight":false}]}"""
        val raw = Gson().fromJson(legacyJson, AppData::class.java)
        val ex = raw.exercises.first()
        assertEquals("", ex.safeDescription)
        assertEquals("Bench Press", ex.name)
        assertEquals("Chest", ex.category)

        val descCheck = ex.safeDescription.isNotBlank()
        val containsCheck = ex.safeDescription.contains("cue", ignoreCase = true)
        val pillCategory = ex.category.ifBlank { "Other" }
        assertEquals(false, descCheck)
        assertEquals(false, containsCheck)
        assertEquals("Chest", pillCategory)
    }

    @Test
    fun verifyBuiltInExercisesCatalogAndNoHardcodedCredentials() {
        // 1. Verify no hardcoded Spreadsheet ID in GoogleSheetsClient
        assertEquals("", GoogleSheetsClient.DEFAULT_SPREADSHEET_ID)
        assertEquals("", GoogleSheetsClient.extractSheetId(""))
        assertEquals(
            "1abcDEF_123",
            GoogleSheetsClient.extractSheetId("https://docs.google.com/spreadsheets/d/1abcDEF_123/edit#gid=0")
        )

        // 2. Verify Service Account email extraction helper
        val sampleSa = """{"type":"service_account","client_email":"my-bot@my-project.iam.gserviceaccount.com"}"""
        assertEquals("my-bot@my-project.iam.gserviceaccount.com", GoogleSheetsClient.extractServiceAccountEmail(sampleSa))
        assertNull(GoogleSheetsClient.extractServiceAccountEmail(""))
        assertNull(GoogleSheetsClient.extractServiceAccountEmail("not-json"))

        // 3. Verify bundled exercises.json parses cleanly and has the full catalog
        val exercisesFile = File("src/main/assets/exercises.json")
        assertTrue("exercises.json must exist in assets", exercisesFile.exists())
        val listType = object : TypeToken<List<Exercise>>() {}.type
        val exercises: List<Exercise> = Gson().fromJson(exercisesFile.readText(Charsets.UTF_8), listType)
        assertNotNull(exercises)
        assertTrue("Expected at least 80 built-in exercises", exercises.size >= 80)
        assertTrue(exercises.any { it.name == "Bench Press" && it.category == "Chest" })
        assertTrue(exercises.any { it.name == "Pull Ups" && it.bodyweight })

        // 4. Verify personal seed files are NOT bundled in APK assets
        assertTrue(!File("src/main/assets/service_account.json").exists())
        assertTrue(!File("src/main/assets/initial_data.json").exists())
        assertTrue(!File("src/main/assets/bodyweight_seed_lbs.csv").exists())
    }

    @Test
    fun verifyWorkoutProgramTemplatesAndSerialization() {
        for (days in 1..7) {
            val program = WorkoutRepository.buildDefaultProgram(days)
            assertEquals(days, program.daysPerWeek)
            assertEquals(days, program.safeDays.size)
            program.safeDays.forEachIndexed { idx, day ->
                assertEquals(idx, day.dayIndex)
                assertTrue(day.dayName.isNotBlank())
                assertTrue(day.safeExercises.isNotEmpty())
            }
        }
    }
}
