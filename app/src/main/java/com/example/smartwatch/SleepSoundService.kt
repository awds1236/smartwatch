package com.example.smartwatch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

/**
 * 수면 사운드를 백그라운드에서 반복 재생하는 Foreground Service.
 * 30분 후 페이드아웃으로 자연스럽게 종료됩니다.
 */
class SleepSoundService : Service() {

    companion object {
        private const val TAG = "SleepSoundService"
        private const val CHANNEL_ID = "sleep_sound_channel"
        private const val NOTIFICATION_ID = 2001
        private const val EXTRA_RAW_RES_ID = "raw_res_id"
        private const val EXTRA_SOUND_NAME = "sound_name"

        /** 30분 = 1,800,000ms */
        private const val AUTO_STOP_DELAY_MS = 30L * 60 * 1000

        /** 페이드아웃 총 시간: 10초 */
        private const val FADE_OUT_DURATION_MS = 10_000L
        private const val FADE_OUT_INTERVAL_MS = 200L

        fun start(context: Context, rawResId: Int, soundName: String) {
            val intent = Intent(context, SleepSoundService::class.java).apply {
                putExtra(EXTRA_RAW_RES_ID, rawResId)
                putExtra(EXTRA_SOUND_NAME, soundName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SleepSoundService::class.java))
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var currentVolume = 1.0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val rawResId = intent?.getIntExtra(EXTRA_RAW_RES_ID, 0) ?: 0
        val soundName = intent?.getStringExtra(EXTRA_SOUND_NAME) ?: "수면 사운드"

        if (rawResId == 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 이전 재생 중지
        releasePlayer()
        handler.removeCallbacksAndMessages(null)

        // Foreground notification
        startForeground(NOTIFICATION_ID, buildNotification(soundName))

        // MediaPlayer 반복 재생
        try {
            mediaPlayer = MediaPlayer.create(this, rawResId).apply {
                isLooping = true
                start()
            }
            currentVolume = 1.0f
            Log.i(TAG, "Playing: $soundName (resId=$rawResId), auto-stop in 30min")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaPlayer", e)
            stopSelf()
            return START_NOT_STICKY
        }

        // 30분 후 페이드아웃
        handler.postDelayed({ startFadeOut() }, AUTO_STOP_DELAY_MS)

        return START_NOT_STICKY
    }

    private fun startFadeOut() {
        Log.i(TAG, "Starting fade-out over ${FADE_OUT_DURATION_MS / 1000}s")
        val steps = (FADE_OUT_DURATION_MS / FADE_OUT_INTERVAL_MS).toInt()
        val volumeStep = currentVolume / steps

        val fadeRunnable = object : Runnable {
            var remaining = steps
            override fun run() {
                if (remaining <= 0 || mediaPlayer == null) {
                    Log.i(TAG, "Fade-out complete, stopping service")
                    stopSelf()
                    return
                }
                currentVolume = (currentVolume - volumeStep).coerceAtLeast(0f)
                mediaPlayer?.setVolume(currentVolume, currentVolume)
                remaining--
                handler.postDelayed(this, FADE_OUT_INTERVAL_MS)
            }
        }
        handler.post(fadeRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
        Log.i(TAG, "Service destroyed")
        super.onDestroy()
    }

    private fun releasePlayer() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "수면 사운드",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "수면 사운드 재생 알림"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(soundName: String): Notification {
        val openIntent = Intent(this, SleepSoundActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("수면 사운드 재생 중")
            .setContentText("$soundName - 30분 후 자동 종료")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
