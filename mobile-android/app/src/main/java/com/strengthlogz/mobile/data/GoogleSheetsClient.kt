package com.strengthlogz.mobile.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.strengthlogz.mobile.model.AppData
import com.strengthlogz.mobile.model.BodyWeightEntry
import com.strengthlogz.mobile.model.Exercise
import com.strengthlogz.mobile.model.ExerciseGoal
import com.strengthlogz.mobile.model.WorkoutProgram
import com.strengthlogz.mobile.model.Workout
import com.strengthlogz.mobile.model.WorkoutSet
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

class GoogleSheetsClient(
    private val serviceAccountJsonProvider: () -> String
) {
    companion object {
        const val DEFAULT_SPREADSHEET_ID = ""

        fun extractSheetId(input: String): String {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return ""
            val regex = Regex("/spreadsheets/d/([a-zA-Z0-9-_]+)")
            val match = regex.find(trimmed)
            return match?.groupValues?.get(1) ?: trimmed
        }

        fun extractServiceAccountEmail(jsonStr: String): String? {
            if (jsonStr.isBlank()) return null
            return try {
                val obj = JsonParser.parseString(jsonStr).asJsonObject
                obj.get("client_email")?.asString?.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }
    }

    private val gson = Gson()
    private var cachedToken: String? = null
    private var tokenExpiryEpochSec: Long = 0L

    @Synchronized
    fun invalidateToken() {
        cachedToken = null
        tokenExpiryEpochSec = 0L
    }

    @Synchronized
    fun getAccessToken(): String {
        val nowSec = System.currentTimeMillis() / 1000L
        val existing = cachedToken
        if (existing != null && nowSec < tokenExpiryEpochSec - 60) {
            return existing
        }

        val rawJson = serviceAccountJsonProvider().trim()
        if (rawJson.isBlank()) {
            throw Exception("Service Account JSON key is not configured. Add your JSON key in Settings.")
        }

        val saJson = try {
            JsonParser.parseString(rawJson).asJsonObject
        } catch (e: Exception) {
            throw Exception("Invalid Service Account JSON key format. Paste the complete JSON file contents.")
        }
        val clientEmail = saJson.get("client_email")?.asString
            ?: throw Exception("Service Account JSON is missing 'client_email'.")
        val privateKeyPem = saJson.get("private_key")?.asString
            ?: throw Exception("Service Account JSON is missing 'private_key'.")
        val tokenUri = if (saJson.has("token_uri")) saJson.get("token_uri").asString else "https://oauth2.googleapis.com/token"

        val cleanPem = privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s+".toRegex(), "")

        val keyBytes = Base64.getDecoder().decode(cleanPem)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(keySpec)

        val headerJson = """{"alg":"RS256","typ":"JWT"}"""
        val expSec = nowSec + 3600L
        val claimJson = gson.toJson(
            mapOf(
                "iss" to clientEmail,
                "scope" to "https://www.googleapis.com/auth/spreadsheets",
                "aud" to tokenUri,
                "iat" to nowSec,
                "exp" to expSec
            )
        )

        val encoder = Base64.getUrlEncoder().withoutPadding()
        val encodedHeader = encoder.encodeToString(headerJson.toByteArray(Charsets.UTF_8))
        val encodedClaim = encoder.encodeToString(claimJson.toByteArray(Charsets.UTF_8))
        val signingInput = "$encodedHeader.$encodedClaim"

        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(signingInput.toByteArray(Charsets.UTF_8))
        val encodedSig = encoder.encodeToString(signature.sign())

        val jwt = "$signingInput.$encodedSig"
        val formBody = "grant_type=" +
            URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", "UTF-8") +
            "&assertion=" + URLEncoder.encode(jwt, "UTF-8")

        val conn = (URL(tokenUri).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(formBody) }

        val code = conn.responseCode
        val respText = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

        if (code !in 200..299) {
            throw Exception("OAuth token error ($code): $respText")
        }

        val tokenObj = JsonParser.parseString(respText).asJsonObject
        val token = tokenObj.get("access_token").asString
        cachedToken = token
        tokenExpiryEpochSec = expSec
        return token
    }

    private fun requestJson(
        method: String,
        urlStr: String,
        bodyObj: Any? = null
    ): JsonObject {
        val token = getAccessToken()
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20000
            readTimeout = 20000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (bodyObj != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
        }

        if (bodyObj != null) {
            val jsonBody = if (bodyObj is String) bodyObj else gson.toJson(bodyObj)
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(jsonBody) }
        }

        val code = conn.responseCode
        val respText = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

        if (code !in 200..299) {
            throw Exception("Google Sheets API error ($code): $respText")
        }

        return if (respText.isBlank()) JsonObject() else JsonParser.parseString(respText).asJsonObject
    }

    data class SheetMeta(
        val title: String,
        val sheetIds: Map<String, Int>
    )

    fun ensureTabs(rawSpreadsheetId: String): SheetMeta {
        val spreadsheetId = extractSheetId(rawSpreadsheetId)
        val meta = requestJson("GET", "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId")
        val docTitle = meta.getAsJsonObject("properties")?.get("title")?.asString ?: "StrengthLogz"
        val existing = mutableMapOf<String, Int>()
        val sheetsArr = meta.getAsJsonArray("sheets")
        if (sheetsArr != null) {
            for (el in sheetsArr) {
                val props = el.asJsonObject.getAsJsonObject("properties")
                val t = props.get("title").asString
                val id = props.get("sheetId").asInt
                existing[t] = id
            }
        }

        val requiredTabs = mapOf(
            "Workouts" to listOf("Id", "Date", "Title", "Notes"),
            "Sets" to listOf("Id", "WorkoutId", "Date", "Exercise", "Set", "Weight (kg)", "Reps", "Notes"),
            "Exercises" to listOf("Name", "Category", "Bodyweight", "Description"),
            "BodyWeights" to listOf("Date", "WeightKg", "Source", "UpdatedAt"),
            "Goals" to listOf("Id", "ExerciseName", "PayloadJson", "UpdatedAt"),
            "Programs" to listOf("Id", "ProgramName", "IsActive", "PayloadJson", "UpdatedAt")
        )

        val missing = requiredTabs.keys.filter { it !in existing }
        if (missing.isNotEmpty()) {
            val addSheetRequests = missing.map { title ->
                mapOf(
                    "addSheet" to mapOf(
                        "properties" to mapOf(
                            "title" to title,
                            "gridProperties" to mapOf("frozenRowCount" to 1)
                        )
                    )
                )
            }
            val addRes = requestJson(
                "POST",
                "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId:batchUpdate",
                mapOf("requests" to addSheetRequests)
            )
            val replies = addRes.getAsJsonArray("replies")
            if (replies != null) {
                for (r in replies) {
                    val props = r.asJsonObject.getAsJsonObject("addSheet")?.getAsJsonObject("properties")
                    if (props != null) {
                        existing[props.get("title").asString] = props.get("sheetId").asInt
                    }
                }
            }

            val headerData = missing.map { tab ->
                mapOf(
                    "range" to "$tab!A1",
                    "values" to listOf(requiredTabs[tab])
                )
            }
            requestJson(
                "POST",
                "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values:batchUpdate",
                mapOf(
                    "valueInputOption" to "RAW",
                    "data" to headerData
                )
            )
        }

        return SheetMeta(title = docTitle, sheetIds = existing)
    }

    fun loadAll(rawSpreadsheetId: String): AppData {
        val spreadsheetId = extractSheetId(rawSpreadsheetId)
        ensureTabs(spreadsheetId)

        val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values:batchGet" +
            "?ranges=Workouts!A2:D&ranges=Sets!A2:H&ranges=Exercises!A2:D&ranges=BodyWeights!A2:D&ranges=Goals!A2:D&ranges=Programs!A2:E&valueRenderOption=UNFORMATTED_VALUE"

        val res = requestJson("GET", url)
        val valueRanges = res.getAsJsonArray("valueRanges")

        fun getRows(index: Int): List<List<String>> {
            if (valueRanges == null || index >= valueRanges.size()) return emptyList()
            val vr = valueRanges[index].asJsonObject
            val values = vr.getAsJsonArray("values") ?: return emptyList()
            val result = mutableListOf<List<String>>()
            for (rowEl in values) {
                val rowArr = rowEl.asJsonArray
                val rowList = mutableListOf<String>()
                for (cellEl in rowArr) {
                    if (cellEl == null || cellEl.isJsonNull) {
                        rowList.add("")
                    } else {
                        rowList.add(cellEl.asString)
                    }
                }
                result.add(rowList)
            }
            return result
        }

        val wRows = getRows(0)
        val sRows = getRows(1)
        val eRows = getRows(2)
        val bwRows = getRows(3)
        val gRows = getRows(4)
        val pRows = getRows(5)

        val workouts = wRows
            .filter { it.firstOrNull()?.isNotBlank() == true }
            .map { r ->
                Workout(
                    id = r.getOrElse(0) { "" },
                    date = r.getOrElse(1) { "" },
                    title = r.getOrElse(2) { "" },
                    notes = r.getOrElse(3) { "" }
                )
            }
            .toMutableList()

        val sets = sRows
            .filter { it.firstOrNull()?.isNotBlank() == true }
            .map { r ->
                WorkoutSet(
                    id = r.getOrElse(0) { "" },
                    workoutId = r.getOrElse(1) { "" },
                    date = r.getOrElse(2) { "" },
                    exercise = r.getOrElse(3) { "" },
                    setNo = r.getOrElse(4) { "" }.toDoubleOrNull()?.toInt() ?: 1,
                    weight = r.getOrElse(5) { "" }.toDoubleOrNull(),
                    reps = r.getOrElse(6) { "" }.toDoubleOrNull()?.toInt(),
                    notes = r.getOrElse(7) { "" }
                )
            }
            .toMutableList()

        val exercises = eRows
            .filter { it.firstOrNull()?.isNotBlank() == true }
            .map { r ->
                val bwStr = r.getOrElse(2) { "" }
                val descStr = r.getOrElse(3) { "" }
                Exercise(
                    name = r.getOrElse(0) { "" },
                    category = r.getOrElse(1) { "Other" }.ifBlank { "Other" },
                    bodyweight = bwStr.equals("true", ignoreCase = true),
                    description = descStr
                )
            }
            .toMutableList()

        val bodyWeights = bwRows
            .filter { it.firstOrNull()?.isNotBlank() == true }
            .mapNotNull { r ->
                val kg = r.getOrElse(1) { "" }.toDoubleOrNull() ?: return@mapNotNull null
                BodyWeightEntry(
                    date = r.getOrElse(0) { "" },
                    weightKg = kg,
                    source = r.getOrElse(2) { "" }.ifBlank { "manual" },
                    updatedAt = r.getOrElse(3) { "" }.toDoubleOrNull()?.toLong() ?: 0L
                )
            }
            .toMutableList()

        val goals = gRows
            .filter { it.firstOrNull()?.isNotBlank() == true }
            .mapNotNull { r ->
                try {
                    val g = gson.fromJson(r.getOrElse(2) { "" }, ExerciseGoal::class.java) ?: return@mapNotNull null
                    val stamp = r.getOrElse(3) { "" }.toDoubleOrNull()?.toLong() ?: g.updatedAt
                    g.copy(id = r[0], updatedAt = stamp)
                } catch (_: Exception) {
                    null
                }
            }
            .toMutableList()

        var remoteActiveId: String? = null
        val programs = pRows
            .filter { it.firstOrNull()?.isNotBlank() == true }
            .mapNotNull { r ->
                try {
                    val p = gson.fromJson(r.getOrElse(3) { "" }, WorkoutProgram::class.java) ?: return@mapNotNull null
                    if (r.getOrElse(2) { "" }.equals("true", ignoreCase = true)) remoteActiveId = r[0]
                    val stamp = r.getOrElse(4) { "" }.toDoubleOrNull()?.toLong() ?: p.updatedAt
                    p.copy(id = r[0], updatedAt = stamp)
                } catch (_: Exception) {
                    null
                }
            }
            .toMutableList()

        return AppData(
            workouts = workouts,
            sets = sets,
            exercises = exercises,
            bodyWeights = bodyWeights,
            goals = goals,
            programs = programs,
            activeProgramId = remoteActiveId
        )
    }

    /** Replaces the data rows (row 2 onward) of [tab] with [rows], keeping/creating the header row. */
    private fun overwriteTab(spreadsheetId: String, tab: String, header: List<String>, rows: List<List<Any>>) {
        val lastCol = 'A' + (header.size - 1)
        requestJson(
            "POST",
            "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values:batchUpdate",
            mapOf(
                "valueInputOption" to "RAW",
                "data" to listOf(mapOf("range" to "$tab!A1:${lastCol}1", "values" to listOf(header)))
            )
        )
        requestJson(
            "POST",
            "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$tab!A2:$lastCol:clear",
            emptyMap<String, Any>()
        )
        if (rows.isNotEmpty()) {
            requestJson(
                "POST",
                "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values:batchUpdate",
                mapOf(
                    "valueInputOption" to "RAW",
                    "data" to listOf(mapOf("range" to "$tab!A2:$lastCol", "values" to rows))
                )
            )
        }
    }

    fun overwriteBodyWeights(rawSpreadsheetId: String, entries: List<BodyWeightEntry>) {
        val id = extractSheetId(rawSpreadsheetId)
        val rows = entries.map { listOf<Any>(it.date, it.weightKg, it.source, it.updatedAt) }
        overwriteTab(id, "BodyWeights", listOf("Date", "WeightKg", "Source", "UpdatedAt"), rows)
    }

    fun overwriteGoals(rawSpreadsheetId: String, goals: List<ExerciseGoal>) {
        val id = extractSheetId(rawSpreadsheetId)
        val rows = goals.map { listOf<Any>(it.id, it.exerciseName, gson.toJson(it), it.updatedAt) }
        overwriteTab(id, "Goals", listOf("Id", "ExerciseName", "PayloadJson", "UpdatedAt"), rows)
    }

    fun overwritePrograms(rawSpreadsheetId: String, programs: List<WorkoutProgram>, activeProgramId: String?) {
        val id = extractSheetId(rawSpreadsheetId)
        val rows = programs.map {
            listOf<Any>(it.id, it.safeProgramName, it.id == activeProgramId, gson.toJson(it), it.updatedAt)
        }
        overwriteTab(id, "Programs", listOf("Id", "ProgramName", "IsActive", "PayloadJson", "UpdatedAt"), rows)
    }

    private fun appendRows(spreadsheetId: String, tab: String, rows: List<List<Any>>) {
        if (rows.isEmpty()) return
        val encodedTab = URLEncoder.encode("$tab!A1", "UTF-8")
        val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$encodedTab:append" +
            "?valueInputOption=RAW&insertDataOption=INSERT_ROWS"
        requestJson("POST", url, mapOf("values" to rows))
    }

    fun addWorkouts(rawSpreadsheetId: String, workouts: List<Workout>, sets: List<WorkoutSet>) {
        val spreadsheetId = extractSheetId(rawSpreadsheetId)
        if (workouts.isNotEmpty()) {
            val wRows = workouts.map { w ->
                listOf<Any>(w.id, w.date, w.title, w.notes)
            }
            appendRows(spreadsheetId, "Workouts", wRows)
        }
        if (sets.isNotEmpty()) {
            val sRows = sets.map { s ->
                listOf<Any>(
                    s.id,
                    s.workoutId,
                    s.date,
                    s.exercise,
                    s.setNo,
                    s.weight ?: "",
                    s.reps ?: "",
                    s.notes
                )
            }
            appendRows(spreadsheetId, "Sets", sRows)
        }
    }

    fun addExercises(rawSpreadsheetId: String, exercises: List<Exercise>) {
        if (exercises.isEmpty()) return
        val spreadsheetId = extractSheetId(rawSpreadsheetId)
        val eRows = exercises.map { e ->
            listOf<Any>(e.name, e.category.ifBlank { "Other" }, e.bodyweight, e.safeDescription)
        }
        appendRows(spreadsheetId, "Exercises", eRows)
    }

    fun overwriteExercises(rawSpreadsheetId: String, exercises: List<Exercise>) {
        val spreadsheetId = extractSheetId(rawSpreadsheetId)
        ensureTabs(spreadsheetId)

        // 1. Ensure header at Exercises!A1:D1
        val headerData = mapOf(
            "range" to "Exercises!A1:D1",
            "values" to listOf(listOf("Name", "Category", "Bodyweight", "Description"))
        )
        requestJson(
            "POST",
            "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values:batchUpdate",
            mapOf(
                "valueInputOption" to "RAW",
                "data" to listOf(headerData)
            )
        )

        // 2. Clear previous exercise rows Exercises!A2:D
        val clearUrl = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/Exercises!A2:D:clear"
        requestJson("POST", clearUrl, emptyMap<String, Any>())

        // 3. Write all exercises
        if (exercises.isNotEmpty()) {
            val sorted = exercises.sortedBy { it.name.lowercase() }
            val rows = sorted.map { e ->
                listOf<Any>(e.name, e.category.ifBlank { "Other" }, e.bodyweight, e.safeDescription)
            }
            val updateData = mapOf(
                "range" to "Exercises!A2:D",
                "values" to rows
            )
            requestJson(
                "POST",
                "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values:batchUpdate",
                mapOf(
                    "valueInputOption" to "RAW",
                    "data" to listOf(updateData)
                )
            )
        }
    }

    fun renameExerciseInSets(rawSpreadsheetId: String, oldName: String, newName: String) {
        if (oldName.isBlank() || newName.isBlank() || oldName.equals(newName, ignoreCase = true)) return
        val spreadsheetId = extractSheetId(rawSpreadsheetId)
        val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/Sets!D:D?valueRenderOption=UNFORMATTED_VALUE"
        val res = requestJson("GET", url)
        val values = res.getAsJsonArray("values") ?: return

        val updates = mutableListOf<Map<String, Any>>()
        for (i in 0 until values.size()) {
            val rowArr = values[i].asJsonArray
            if (rowArr.size() > 0 && !rowArr[0].isJsonNull) {
                val exName = rowArr[0].asString
                if (exName.equals(oldName, ignoreCase = true)) {
                    val rowNumber = i + 1 // 1-based row index in Google Sheets
                    updates.add(
                        mapOf(
                            "range" to "Sets!D$rowNumber",
                            "values" to listOf(listOf(newName))
                        )
                    )
                }
            }
        }

        if (updates.isNotEmpty()) {
            requestJson(
                "POST",
                "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values:batchUpdate",
                mapOf(
                    "valueInputOption" to "RAW",
                    "data" to updates
                )
            )
        }
    }

    fun deleteWorkouts(rawSpreadsheetId: String, workoutIds: Set<String>) {
        if (workoutIds.isEmpty()) return
        val spreadsheetId = extractSheetId(rawSpreadsheetId)
        val meta = ensureTabs(spreadsheetId)

        val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values:batchGet" +
            "?ranges=Workouts!A:A&ranges=Sets!B:B&valueRenderOption=UNFORMATTED_VALUE"
        val res = requestJson("GET", url)
        val valueRanges = res.getAsJsonArray("valueRanges") ?: return

        fun extractFirstCol(index: Int): List<String> {
            if (index >= valueRanges.size()) return emptyList()
            val values = valueRanges[index].asJsonObject.getAsJsonArray("values") ?: return emptyList()
            return values.map { row ->
                val arr = row.asJsonArray
                if (arr.size() > 0 && !arr[0].isJsonNull) arr[0].asString else ""
            }
        }

        val wCol = extractFirstCol(0)
        val sCol = extractFirstCol(1)
        val deleteRequests = mutableListOf<Map<String, Any>>()

        fun collectDeletes(tab: String, col: List<String>) {
            val sheetId = meta.sheetIds[tab] ?: return
            val rowsToDelete = mutableListOf<Int>()
            col.forEachIndexed { idx, value ->
                if (idx > 0 && value in workoutIds) {
                    rowsToDelete.add(idx)
                }
            }
            rowsToDelete.sortedDescending().forEach { rowIndex ->
                deleteRequests.add(
                    mapOf(
                        "deleteDimension" to mapOf(
                            "range" to mapOf(
                                "sheetId" to sheetId,
                                "dimension" to "ROWS",
                                "startIndex" to rowIndex,
                                "endIndex" to rowIndex + 1
                            )
                        )
                    )
                )
            }
        }

        collectDeletes("Sets", sCol)
        collectDeletes("Workouts", wCol)

        if (deleteRequests.isNotEmpty()) {
            requestJson(
                "POST",
                "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId:batchUpdate",
                mapOf("requests" to deleteRequests)
            )
        }
    }
}

