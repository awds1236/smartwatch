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
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

/**
 * 알람 소리와 진동을 재생하는 Foreground Service.
 * AlarmActivity 실행 여부와 무관하게 알람이 즉시 울리도록 합니다.
 */
public class AlarmService extends Service {

    private static final String TAG = "AlarmService";
    private static final String CHANNEL_ID = "alarm_service_channel";
    private static final int NOTIFICATION_ID = 5001;
    private static final long AUTO_STOP_DELAY_MS = 5 * 60 * 1000L; // 5분 안전 타임아웃

    public static final String ACTION_DISMISS = "com.example.smartwatch.ACTION_DISMISS_ALARM";

    private Vibrator vibrator;
    private MediaPlayer mediaPlayer;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static volatile boolean running = false;

    private final Runnable autoStopRunnable = () -> {
        Log.d(TAG, "5분 타임아웃 — 알람 자동 종료");
        dismissAlarmInternal();
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_DISMISS.equals(intent.getAction())) {
            dismissAlarmInternal();
            return START_NOT_STICKY;
        }

        // 이미 실행 중이면 중복 시작 방지 (진동/소리 리셋)
        stopVibration();
        stopRingtone();
        handler.removeCallbacks(autoStopRunnable);

        startForeground(NOTIFICATION_ID, buildNotification());
        running = true;

        startVibration();
        startRingtone();

        // 5분 안전 타임아웃
        handler.postDelayed(autoStopRunnable, AUTO_STOP_DELAY_MS);

        Log.i(TAG, "알람 서비스 시작 — 소리/진동 재생 중");
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(autoStopRunnable);
        stopVibration();
        stopRingtone();
        Log.i(TAG, "알람 서비스 종료");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ── 진동 ──────────────────────────────────────────────────────────

    private void startVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
            vibrator = vm.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        }
        if (vibrator != null) {
            long[] pattern = {0, 500, 300, 500, 300};
            VibrationEffect effect = VibrationEffect.createWaveform(pattern, 0); // 0 = 반복
            vibrator.vibrate(effect);
        }
    }

    private void stopVibration() {
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
    }

    // ── 소리 ──────────────────────────────────────────────────────────

    private void startRingtone() {
        try {
            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (ringtoneUri == null) {
                ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mediaPlayer.setDataSource(this, ringtoneUri);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            Log.w(TAG, "링톤 재생 실패 — 진동만으로 동작", e);
        }
    }

    private void stopRingtone() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    // ── 알람 해제 (내부) ──────────────────────────────────────────────

    private void dismissAlarmInternal() {
        Context ctx = getApplicationContext();

        // fullscreen 알림 취소
        NotificationManager nm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(1002); // AlarmReceiver.NOTIFICATION_ID_ALARM

        // 수면 소리 정지
        SleepSoundService.stop(ctx);

        // 워치 알람 해제
        new Thread(() -> WatchNotifier.sendAlarmDismiss(ctx)).start();

        // 모니터링 상태 초기화
        new SleepPreferences(ctx).reset();

        stopSelf();
    }

    // ── 알림 채널 & 알림 ──────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "알람 재생",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("알람 소리/진동 재생 중 알림");
            channel.setSound(null, null);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        // 알람 해제 액션
        Intent dismissIntent = new Intent(this, AlarmService.class);
        dismissIntent.setAction(ACTION_DISMISS);
        PendingIntent dismissPi = PendingIntent.getService(
                this, 0, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 알람 화면 열기
        Intent openIntent = new Intent(this, AlarmActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("수면 알람")
                .setContentText("알람이 울리고 있습니다")
                .setContentIntent(openPi)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "알람 해제", dismissPi)
                .setOngoing(true)
                .build();
    }

    // ── 정적 헬퍼 ────────────────────────────────────────────────────

    public static boolean isRunning() {
        return running;
    }

    /** AlarmReceiver에서 호출 — 즉시 알람 소리/진동 시작 */
    public static void start(Context context) {
        Intent intent = new Intent(context, AlarmService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /** 서비스 중지 헬퍼 */
    public static void stop(Context context) {
        context.stopService(new Intent(context, AlarmService.class));
        running = false;
    }

    /**
     * 알람 해제 공통 진입점.
     * AlarmActivity 및 알림 액션에서 호출됩니다.
     */
    public static void dismissAlarm(Context context) {
        // fullscreen 알림 취소
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(1002); // AlarmReceiver.NOTIFICATION_ID_ALARM

        // 수면 소리 정지
        SleepSoundService.stop(context);

        // 워치 알람 해제
        new Thread(() -> WatchNotifier.sendAlarmDismiss(context.getApplicationContext())).start();

        // 모니터링 상태 초기화
        new SleepPreferences(context).reset();

        // 서비스 종료 (소리/진동 중지)
        stop(context);
    }
}
