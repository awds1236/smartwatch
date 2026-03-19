package com.example.smartwatch

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
 * 2분 주기로 Health Connect에서 수면 데이터를 읽어 REM 수면 감지 시 스마트 알람을 트리거합니다.
 */
class SleepMonitorService : Service() {

    companion object {
        private const val TAG = "SleepMonitorService"
        private const val CHANNEL_ID = "sleep_monitor_channel"
        private const val NOTIFICATION_ID = 4001
        private const val CHECK_INTERVAL_MS = 2 * 60 * 1000L // 2분
        private const val MEDIA_AUTO_STOP_DELAY_MS = 20 * 60 * 1000L // 20분
        private const val REM_ALARM_WINDOW_MS = 15 * 60 * 1000L // 마감 15분 전부터 REM 감지 시 알람

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
    private var mediaAutoStopScheduled = false
    private var audioFocusRequest: AudioFocusRequest? = null

    /**
     * 수면 감지 20분 후 외부 미디어(음악, 유튜브 등)를 중지시키는 Runnable.
     * AudioManager.requestAudioFocus()를 호출하여 다른 앱의 오디오 재생을 중단시킨 뒤,
     * 즉시 포커스를 해제하여 시스템 상태를 원래대로 복원합니다.
     */
    private val mediaAutoStopRunnable = Runnable {
        Log.i(TAG, "수면 감지 20분 경과 – 외부 미디어 자동 중지")
        stopExternalMedia()
    }

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
        handler.removeCallbacks(mediaAutoStopRunnable)
        releaseAudioFocus()
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
                Log.i(TAG, "Sleep: ${sleepMinutes}min")

                // 수면 상태 최초 감지 시 수면 소리 + 외부 미디어 20분 자동 종료 시작
                if (sleepMinutes > 0 && !prefs.isSleepDetected) {
                    Log.i(TAG, "Sleep detected! Starting 20-min auto-stop for sleep sound & external media.")
                    prefs.setSleepDetected(true)
                    SleepSoundService.notifySleepDetected(ctx)
                    scheduleMediaAutoStop()
                }

                if (shouldTriggerRemAlarm(prefs, data.isCurrentlyInRem)) {
                    Log.i(TAG, "REM sleep detected within 15min of deadline! Triggering smart alarm.")
                    prefs.setAlarmFired(true)
                    AlarmReceiver.cancelDeadlineAlarm(ctx)
                    triggerAlarm(ctx)
                    stopSelf()
                } else {
                    // 알림 업데이트: 진행 상태 표시
                    updateNotification(sleepMinutes)
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
        // setAlarmClock()을 사용해야 Android 12+에서 백그라운드 Activity 시작 권한이 부여됨
        val showIntent = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + 500L
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), pi)
    }

    // ── REM 스마트 알람 ──────────────────────────────────────────────

    /**
     * 마감 시간 15분 전~마감 시간 사이에 REM 수면이 감지되면 true를 반환합니다.
     * REM 수면은 얕은 수면 상태이므로, 이 시점에 깨우면 더 상쾌하게 기상할 수 있습니다.
     */
    private fun shouldTriggerRemAlarm(prefs: SleepPreferences, isCurrentlyInRem: Boolean): Boolean {
        if (!isCurrentlyInRem) return false

        val deadlineMillis = prefs.deadlineMillis
        if (deadlineMillis <= 0L) return false

        val now = System.currentTimeMillis()
        val windowStart = deadlineMillis - REM_ALARM_WINDOW_MS

        val inWindow = now in windowStart..deadlineMillis
        if (inWindow) {
            Log.i(TAG, "REM alarm window active: ${(deadlineMillis - now) / 60000}min before deadline")
        }
        return inWindow
    }

    // ── 외부 미디어 자동 중지 ─────────────────────────────────────────

    private fun scheduleMediaAutoStop() {
        if (mediaAutoStopScheduled) return
        mediaAutoStopScheduled = true
        handler.postDelayed(mediaAutoStopRunnable, MEDIA_AUTO_STOP_DELAY_MS)
        Log.i(TAG, "외부 미디어 자동 중지 20분 후 예약됨")
    }

    /**
     * AudioFocus를 GAIN으로 요청하여 다른 앱(음악, 유튜브 등)의 재생을 중단시킨 뒤,
     * 바로 포커스를 해제합니다.
     */
    private fun stopExternalMedia() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener { }
                .build()
            audioFocusRequest = focusRequest

            val result = audioManager.requestAudioFocus(focusRequest)
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.i(TAG, "오디오 포커스 획득 – 외부 미디어 중지됨")
                // 잠시 후 포커스를 해제하여 시스템 상태 복원
                handler.postDelayed({ releaseAudioFocus() }, 1000L)
            } else {
                Log.w(TAG, "오디오 포커스 요청 실패: $result")
            }
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                { },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.i(TAG, "오디오 포커스 획득 – 외부 미디어 중지됨")
                handler.postDelayed({
                    @Suppress("DEPRECATION")
                    audioManager.abandonAudioFocus { }
                }, 1000L)
            }
        }
    }

    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.abandonAudioFocusRequest(it)
                audioFocusRequest = null
                Log.d(TAG, "오디오 포커스 해제됨")
            }
        }
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

    private fun updateNotification(sleepMinutes: Long) {
        val openPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sleepH = sleepMinutes / 60
        val sleepM = sleepMinutes % 60
        val progress = "수면 ${sleepH}시간 ${sleepM}분 경과"

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
