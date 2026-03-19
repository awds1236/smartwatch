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
        val actualSleepMinutes: Long,
        /** 가장 최근 수면 스테이지가 REM인지 여부 */
        val isCurrentlyInRem: Boolean = false
    )

    companion object {
        private const val TAG = "HealthConnectHelper"

        @JvmStatic
        fun isAvailable(context: Context): Boolean =
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    /**
     * 지정된 시각 이후의 실제 수면 시간(분)을 반환합니다.
     * monitoringStartMillis가 0이면 기본 윈도우(전날 18시)를 사용합니다.
     */
    suspend fun readTotalSleepMinutes(monitoringStartMillis: Long = 0L): Long {
        return readSleepData(monitoringStartMillis).actualSleepMinutes
    }

    /**
     * 수면 세션 시간(전체)과 실제 수면 시간(AWAKE/OUT_OF_BED 제외)을 함께 반환합니다.
     *
     * @param monitoringStartMillis 모니터링 시작 시각(epoch millis).
     *        0이면 기본 윈도우(전날 18시~현재), 양수이면 해당 시각~현재를 조회합니다.
     *        세션이 모니터링 시작 전에 시작된 경우, 시작 이후 부분만 카운트합니다.
     */
    suspend fun readSleepData(monitoringStartMillis: Long = 0L): SleepData {
        val client = HealthConnectClient.getOrCreate(context)

        // 조회 윈도우 시작: 모니터링 시작 시각 또는 기본(전날 18시)
        val windowStart: Instant = if (monitoringStartMillis > 0) {
            Instant.ofEpochMilli(monitoringStartMillis)
        } else {
            LocalDate.now(ZoneId.systemDefault())
                .atTime(LocalTime.of(18, 0))
                .minusDays(1)
                .atZone(ZoneId.systemDefault())
                .toInstant()
        }

        val monitoringStart: Instant = if (monitoringStartMillis > 0) {
            Instant.ofEpochMilli(monitoringStartMillis)
        } else {
            windowStart
        }

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
        // 가장 최근 스테이지의 REM 여부를 추적
        var latestStageTime: Instant = Instant.MIN
        var latestStageIsRem = false

        for (session in records) {
            // 세션이 모니터링 시작 전에 완전히 끝났으면 무시
            if (session.endTime.isBefore(monitoringStart)) continue

            // 세션의 유효 시작 시각: 모니터링 시작 이후 부분만 카운트
            val effectiveStart = if (session.startTime.isBefore(monitoringStart)) {
                monitoringStart
            } else {
                session.startTime
            }

            totalSessionMs += Duration.between(effectiveStart, session.endTime).toMillis()

            val stages = session.stages
            if (stages.isEmpty()) {
                // 스테이지 정보가 없으면 세션 전체를 수면으로 간주
                totalActualSleepMs += Duration.between(effectiveStart, session.endTime).toMillis()
            } else {
                for (stage in stages) {
                    // 모니터링 시작 이전 스테이지는 무시
                    if (stage.endTime.isBefore(monitoringStart)) continue

                    val stageStart = if (stage.startTime.isBefore(monitoringStart)) {
                        monitoringStart
                    } else {
                        stage.startTime
                    }

                    if (stage.stage !in awakeTypes) {
                        totalActualSleepMs += Duration.between(stageStart, stage.endTime).toMillis()
                    }

                    // 가장 최근 스테이지 추적 (REM 감지용)
                    if (stage.endTime.isAfter(latestStageTime)) {
                        latestStageTime = stage.endTime
                        latestStageIsRem = (stage.stage == SleepSessionRecord.STAGE_TYPE_REM)
                    }
                }
            }
        }

        val sessionMinutes = Duration.ofMillis(totalSessionMs).toMinutes()
        val actualMinutes = Duration.ofMillis(totalActualSleepMs).toMinutes()
        Log.d(TAG, "Window start: $windowStart, Monitoring start: $monitoringStart")
        Log.d(TAG, "Session: ${sessionMinutes}min, Actual sleep: ${actualMinutes}min (${records.size} sessions)")
        if (latestStageIsRem) {
            Log.d(TAG, "REM sleep detected in latest stage (endTime: $latestStageTime)")
        }
        return SleepData(sessionMinutes, actualMinutes, latestStageIsRem)
    }
}
