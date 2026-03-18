package com.example.smartwatch

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 수면 모니터링을 위한 Foreground Service.
 * WorkManager는 Doze 모드에서 지연되므로, Foreground Service로 안정적으로 수면 데이터를 확인합니다.
 * 2분 주기로 Health Connect에서 수면 데이터를 읽어 목표 달성 시 알람을 트리거합니다.
 */
class SleepMonitorService : Service() {

    companion object {
        private const val TAG = "SleepMonitorService"
        private const val CHANNEL_ID = "sleep_monitor_channel"
        private const val NOTIFICATION_ID = 4001
        private const val CHECK_INTERVAL_MS = 2 * 60 * 1000L // 2분

        @JvmStatic
        fun start(context: Context) {
            val intent = Intent(context, SleepMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        @JvmStatic
        fun stop(context: Context) {
            context.stopService(Intent(context, SleepMonitorService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())

    private val checkRunnable = object : Runnable {
        override fun run() {
            performCheck()
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        // 즉시 첫 체크 후 2분 주기로 반복
        handler.removeCallbacks(checkRunnable)
        handler.post(checkRunnable)

        Log.i(TAG, "Sleep monitor service started")
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(checkRunnable)
        serviceScope.cancel()
        Log.i(TAG, "Sleep monitor service stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun performCheck() {
        val ctx = applicationContext
        val prefs = SleepPreferences(ctx)

        if (!prefs.isMonitoringActive) {
            Log.d(TAG, "Monitoring not active — stopping service.")
            stopSelf()
            return
        }
        if (prefs.isAlarmFired) {
            Log.d(TAG, "Alarm already fired — stopping service.")
            stopSelf()
            return
        }
        if (!HealthConnectHelper.isAvailable(ctx)) {
            Log.w(TAG, "Health Connect not available.")
            return
        }

        serviceScope.launch {
            try {
                val monitoringStartMillis = prefs.monitoringStartMillis
                val data = HealthConnectHelper(ctx).readSleepData(monitoringStartMillis)
                val sleepMinutes = data.actualSleepMinutes
                val goalMinutes = prefs.goalMinutes.toLong()
                Log.i(TAG, "Sleep: ${sleepMinutes}min / Goal: ${goalMinutes}min")

                // 수면 상태 최초 감지 시 수면 소리 30분 자동 종료 시작
                if (sleepMinutes > 0 && !prefs.isSleepDetected) {
                    Log.i(TAG, "Sleep detected! Starting 30-min auto-stop for sleep sound.")
                    prefs.setSleepDetected(true)
                    SleepSoundService.notifySleepDetected(ctx)
                }

                if (sleepMinutes >= goalMinutes) {
                    Log.i(TAG, "Goal reached! Triggering goal alarm.")
                    prefs.setAlarmFired(true)
                    triggerAlarm(ctx)
                    stopSelf()
                } else {
                    // 알림 업데이트: 진행 상태 표시
                    updateNotification(sleepMinutes, goalMinutes)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading sleep data", e)
            }
        }
    }

    private fun triggerAlarm(ctx: Context) {
        val intent = Intent(ctx, AlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 500L, pi)
    }

    // ── 알림 ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "수면 모니터링",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "수면 모니터링 진행 중 알림"
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("수면 모니터링 중")
            .setContentText("수면 데이터를 확인하고 있습니다")
            .setContentIntent(openPi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(sleepMinutes: Long, goalMinutes: Long) {
        val openPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sleepH = sleepMinutes / 60
        val sleepM = sleepMinutes % 60
        val goalH = goalMinutes / 60
        val goalM = goalMinutes % 60
        val progress = "${sleepH}시간 ${sleepM}분 / ${goalH}시간 ${goalM}분"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("수면 모니터링 중")
            .setContentText(progress)
            .setContentIntent(openPi)
            .setOngoing(true)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, notification)
    }
}
