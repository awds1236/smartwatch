package com.example.smartwatch

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * WorkManager CoroutineWorker — 1분 주기로 Health Connect의 수면 데이터를 확인하고,
 * 목표 수면 시간 달성 시 AlarmReceiver를 통해 알람을 울립니다.
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
            val sleepMinutes = HealthConnectHelper(ctx).readTotalSleepMinutes()
            val goalMinutes = prefs.goalMinutes.toLong()
            val deadlineMillis = prefs.deadlineMillis
            val now = System.currentTimeMillis()
            Log.i(TAG, "Sleep: ${sleepMinutes}min / Goal: ${goalMinutes}min / Deadline in: ${(deadlineMillis - now) / 60000}min")

            val goalReached = sleepMinutes >= goalMinutes
            val deadlinePassed = deadlineMillis > 0 && now >= deadlineMillis

            if (goalReached) {
                Log.i(TAG, "Goal reached! Triggering alarm.")
                prefs.setAlarmFired(true)
                triggerAlarm(ctx)
            } else if (deadlinePassed) {
                Log.i(TAG, "Deadline passed (goal not reached). Triggering alarm.")
                prefs.setAlarmFired(true)
                triggerAlarm(ctx)
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading sleep data", e)
            Result.retry()
        }
    }

    private fun triggerAlarm(ctx: Context) {
        val intent = Intent(ctx, AlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000L, pi)
    }
}
