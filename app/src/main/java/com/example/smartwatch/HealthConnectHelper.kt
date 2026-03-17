package com.example.smartwatch

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Health Connect에서 수면 데이터를 읽어오는 헬퍼.
 * Samsung Health는 Galaxy Watch의 수면 감지 결과를 자동으로 Health Connect에 기록합니다.
 */
class HealthConnectHelper(context: Context) {

    private val context = context.applicationContext

    /** 수면 세션 시간과 실제 수면 시간을 담는 데이터 클래스 */
    data class SleepData(
        val sessionMinutes: Long,
        val actualSleepMinutes: Long
    )

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
        return readSleepData().actualSleepMinutes
    }

    /**
     * 수면 세션 시간(전체)과 실제 수면 시간(AWAKE/OUT_OF_BED 제외)을 함께 반환합니다.
     */
    suspend fun readSleepData(): SleepData {
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

        val awakeTypes = setOf(
            SleepSessionRecord.STAGE_TYPE_AWAKE,
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED
        )

        var totalSessionMs = 0L
        var totalActualSleepMs = 0L

        for (session in records) {
            totalSessionMs += Duration.between(session.startTime, session.endTime).toMillis()

            val stages = session.stages
            if (stages.isEmpty()) {
                totalActualSleepMs += Duration.between(session.startTime, session.endTime).toMillis()
            } else {
                for (stage in stages) {
                    if (stage.stage !in awakeTypes) {
                        totalActualSleepMs += Duration.between(stage.startTime, stage.endTime).toMillis()
                    }
                }
            }
        }

        val sessionMinutes = Duration.ofMillis(totalSessionMs).toMinutes()
        val actualMinutes = Duration.ofMillis(totalActualSleepMs).toMinutes()
        Log.d(TAG, "Session: ${sessionMinutes}min, Actual sleep: ${actualMinutes}min (${records.size} sessions)")
        return SleepData(sessionMinutes, actualMinutes)
    }
}
