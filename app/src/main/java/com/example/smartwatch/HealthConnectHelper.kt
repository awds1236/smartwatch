package com.example.smartwatch

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Health Connect에서 수면 데이터를 읽어오는 헬퍼.
 * Samsung Health는 Galaxy Watch의 수면 감지 결과를 자동으로 Health Connect에 기록합니다.
 */
class HealthConnectHelper(context: Context) {

    private val context = context.applicationContext

    companion object {
        private const val TAG = "HealthConnectHelper"

        @JvmStatic
        fun isAvailable(context: Context): Boolean =
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    /**
     * 당일 수면 세션(전날 오후 6시 ~ 현재)의 총 수면 시간(분)을 반환합니다.
     * suspend 함수이므로 코루틴 컨텍스트에서 호출해야 합니다.
     */
    suspend fun readTotalSleepMinutes(): Long {
        val client = HealthConnectClient.getOrCreate(context)

        val windowStart: Instant = LocalDate.now(ZoneId.systemDefault())
            .atTime(LocalTime.of(18, 0))
            .minusDays(1)
            .atZone(ZoneId.systemDefault())
            .toInstant()

        val request = ReadRecordsRequest(
            recordType = SleepSessionRecord::class,
            timeRangeFilter = TimeRangeFilter.between(windowStart, Instant.now())
        )

        val records = client.readRecords(request).records

        var totalSleepMs = 0L
        for (session in records) {
            val stages = session.stages
            if (stages.isEmpty()) {
                totalSleepMs += session.endTime.toEpochMilli() - session.startTime.toEpochMilli()
            } else {
                for (stage in stages) {
                    if (stage.stage != SleepSessionRecord.StageType.STAGE_TYPE_AWAKE) {
                        totalSleepMs += stage.endTime.toEpochMilli() - stage.startTime.toEpochMilli()
                    }
                }
            }
        }

        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalSleepMs)
        Log.d(TAG, "Total sleep: ${totalMinutes}min (${records.size} sessions)")
        return totalMinutes
    }
}
