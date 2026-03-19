package com.example.smartwatch

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * WorkManager CoroutineWorker — 1분 주기로 Health Connect의 수면 데이터를 확인합니다.
 * 수면 감지 시 수면 소리 자동 종료를 트리거합니다.
 */
class SleepMonitorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SleepMonitorWorker"
    }

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = SleepPreferences(ctx)

        if (!prefs.isMonitoringActive) {
            Log.d(TAG, "Monitoring not active — stopping.")
            return Result.success()
        }
        if (prefs.isAlarmFired) {
            Log.d(TAG, "Alarm already fired — skipping.")
            return Result.success()
        }
        if (!HealthConnectHelper.isAvailable(ctx)) {
            Log.w(TAG, "Health Connect not available.")
            return Result.retry()
        }

        return try {
            val monitoringStartMillis = prefs.monitoringStartMillis
            val sleepMinutes = HealthConnectHelper(ctx).readTotalSleepMinutes(monitoringStartMillis)
            Log.i(TAG, "Sleep: ${sleepMinutes}min")

            // 수면 상태 최초 감지 시 수면 소리 20분 자동 종료 시작
            if (sleepMinutes > 0 && !prefs.isSleepDetected) {
                Log.i(TAG, "Sleep detected! Starting 20-min auto-stop for sleep sound.")
                prefs.setSleepDetected(true)
                SleepSoundService.notifySleepDetected(ctx)
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading sleep data", e)
            Result.retry()
        }
    }
}
