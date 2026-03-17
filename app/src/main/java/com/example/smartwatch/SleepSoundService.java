package com.example.smartwatch;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

/**
 * 수면 사운드를 백그라운드에서 반복 재생하는 Foreground Service.
 * 30분 후 볼륨을 서서히 줄인 뒤 자동 종료합니다.
 */
public class SleepSoundService extends Service {

    private static final String TAG = "SleepSoundService";
    private static final String CHANNEL_ID = "sleep_sound_channel";
    private static final int NOTIFICATION_ID = 2001;

    public static final String EXTRA_SOUND_RES_ID = "extra_sound_res_id";
    public static final String EXTRA_SOUND_TITLE = "extra_sound_title";
    public static final String ACTION_STOP = "com.example.smartwatch.STOP_SLEEP_SOUND";

    private static final long AUTO_STOP_DELAY_MS = 30 * 60 * 1000L; // 30분
    private static final long FADE_DURATION_MS = 30_000L; // 30초 페이드아웃
    private static final long FADE_INTERVAL_MS = 500L;

    private MediaPlayer mediaPlayer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private float currentVolume = 1.0f;

    private final Runnable fadeOutRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer == null || !mediaPlayer.isPlaying()) {
                stopSelf();
                return;
            }
            float step = FADE_INTERVAL_MS / (float) FADE_DURATION_MS;
            currentVolume -= step;
            if (currentVolume <= 0f) {
                currentVolume = 0f;
                stopSelf();
                return;
            }
            mediaPlayer.setVolume(currentVolume, currentVolume);
            handler.postDelayed(this, FADE_INTERVAL_MS);
        }
    };

    private final Runnable autoStopRunnable = () -> {
        Log.d(TAG, "30분 경과 – 페이드아웃 시작");
        handler.post(fadeOutRunnable);
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        int soundResId = intent != null ? intent.getIntExtra(EXTRA_SOUND_RES_ID, 0) : 0;
        String title = intent != null ? intent.getStringExtra(EXTRA_SOUND_TITLE) : "수면 소리";
        if (soundResId == 0) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // 이전 재생 정리
        releasePlayer();
        handler.removeCallbacks(autoStopRunnable);
        handler.removeCallbacks(fadeOutRunnable);

        // Foreground notification
        startForeground(NOTIFICATION_ID, buildNotification(title != null ? title : "수면 소리"));

        // MediaPlayer 설정
        try {
            mediaPlayer = MediaPlayer.create(this, soundResId);
            if (mediaPlayer == null) {
                Log.e(TAG, "MediaPlayer.create returned null for resId=" + soundResId);
                stopSelf();
                return START_NOT_STICKY;
            }
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mediaPlayer.setLooping(true);
            currentVolume = 1.0f;
            mediaPlayer.setVolume(currentVolume, currentVolume);
            mediaPlayer.start();
            Log.d(TAG, "재생 시작: " + title);

            // 30분 후 자동 페이드아웃 → 종료
            handler.postDelayed(autoStopRunnable, AUTO_STOP_DELAY_MS);
        } catch (Exception e) {
            Log.e(TAG, "MediaPlayer 초기화 실패", e);
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(autoStopRunnable);
        handler.removeCallbacks(fadeOutRunnable);
        releasePlayer();
        Log.d(TAG, "Service 종료");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "수면 소리",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("수면 사운드 재생 중 알림");
            channel.setSound(null, null);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String soundTitle) {
        Intent stopIntent = new Intent(this, SleepSoundService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(
                this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent openIntent = new Intent(this, SleepSoundsActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentTitle("수면 소리 재생 중")
                .setContentText(soundTitle + " · 30분 후 자동 종료")
                .setContentIntent(openPi)
                .addAction(android.R.drawable.ic_media_pause, "중지", stopPi)
                .setOngoing(true)
                .build();
    }

    /** 외부에서 서비스 실행 중인지 확인하기 위한 플래그 */
    private static volatile boolean running = false;

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        running = false;
    }

    /** 서비스 시작 헬퍼 */
    public static void start(Context context, int soundResId, String title) {
        Intent intent = new Intent(context, SleepSoundService.class);
        intent.putExtra(EXTRA_SOUND_RES_ID, soundResId);
        intent.putExtra(EXTRA_SOUND_TITLE, title);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
        running = true;
    }

    /** 서비스 중지 헬퍼 */
    public static void stop(Context context) {
        context.stopService(new Intent(context, SleepSoundService.class));
        running = false;
    }
}
