package com.strengthlogz.mobile.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.strengthlogz.mobile.model.BodyWeightEntry
import com.strengthlogz.mobile.util.DateUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Reads body weight from Android Health Connect (read-only). */
object HealthConnectWeightSource {
    /** Weight readings from this date onward are taken from Health Connect (earlier ones come from the seed). */
    val START_DATE: LocalDate = LocalDate.of(2026, 5, 1)

    private const val HISTORY_PERMISSION = "android.permission.health.READ_HEALTH_DATA_HISTORY"

    /** Permissions to request: weight, plus (where supported) history beyond 30 days. */
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HISTORY_PERMISSION
    )

    val weightPermission: String = HealthPermission.getReadPermission(WeightRecord::class)

    fun isAvailable(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasPermission(context: Context): Boolean {
        if (!isAvailable(context)) return false
        val client = HealthConnectClient.getOrCreate(context)
        return weightPermission in client.permissionController.getGrantedPermissions()
    }

    /** Returns one entry per day (the latest reading of the day), in kg. */
    suspend fun readWeights(context: Context): List<BodyWeightEntry> {
        val client = HealthConnectClient.getOrCreate(context)
        val zone = ZoneId.systemDefault()
        val start = START_DATE.atStartOfDay(zone).toInstant()
        val end = Instant.now().plusSeconds(86_400)

        val records = mutableListOf<WeightRecord>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageToken = pageToken
                )
            )
            records.addAll(response.records)
            pageToken = response.pageToken
        } while (pageToken != null)

        return records
            .groupBy { it.time.atZone(zone).toLocalDate() }
            .map { (day, dayRecords) ->
                val latest = dayRecords.maxByOrNull { it.time }!!
                BodyWeightEntry(
                    date = DateUtils.toDdMonYyyy(day.toString()),
                    weightKg = Math.round(latest.weight.inKilograms * 10.0) / 10.0,
                    source = "healthconnect"
                )
            }
            .sortedBy { DateUtils.toSortableIso(it.date) }
    }
}

