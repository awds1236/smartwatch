package com.example.smartwatch;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.Calendar;

/**
 * AlarmManager로부터 트리거를 받아 시스템 시계 앱에 알람을 설정하고
 * 워치로 알람 신호를 전송하는 BroadcastReceiver.
 */
public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";
    private static final String CHANNEL_ID = "alarm_info_channel";
    private static final int NOTIFICATION_ID_DEADLINE = 1001;
    public static final String ALARM_LABEL = "수면 목표 달성";
    public static final String DEADLINE_ALARM_LABEL = "기상 마감 알람";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "Alarm triggered! (sleep goal reached)");

        // 마감 알람 제거 (목표 달성으로 더 이상 필요 없음)
        dismissDeadlineAlarm(context);

        // 시스템 시계 앱으로 알람 설정 (현재 + 1분, 동일 라벨은 덮어씀)
        setSystemAlarm(context);

        // 워치 알람 전송 (백그라운드 스레드)
        new Thread(() -> WatchNotifier.sendAlarmStart(context)).start();
    }

    /**
     * 기상 마감 시간으로 시스템 시계 앱에 알람을 설정합니다.
     * 기존 동일 라벨 알람을 먼저 삭제 후 새로 생성하여 시간 변경이 확실히 반영되도록 합니다.
     */
    public static void setDeadlineAlarm(Context context, int hour, int minute) {
        // 1단계: 기존 마감 알람 삭제
        dismissDeadlineAlarm(context);

        // 2단계: 삭제 처리 후 새 알람 생성 (알람 앱이 dismiss를 처리할 시간 확보)
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent alarmIntent = new Intent(AlarmClock.ACTION_SET_ALARM);
            alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            alarmIntent.putExtra(AlarmClock.EXTRA_HOUR, hour);
            alarmIntent.putExtra(AlarmClock.EXTRA_MINUTES, minute);
            alarmIntent.putExtra(AlarmClock.EXTRA_MESSAGE, DEADLINE_ALARM_LABEL);
            alarmIntent.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
            alarmIntent.putExtra(AlarmClock.EXTRA_VIBRATE, true);

            try {
                context.startActivity(alarmIntent);
                String timeText = String.format("%d:%02d", hour, minute);
                Log.i(TAG, "Deadline alarm set for " + timeText);
                showAlarmSetNotification(context, timeText);
            } catch (Exception e) {
                Log.e(TAG, "Failed to set deadline alarm", e);
            }
        }, 1000);
    }

    /** 알람 설정 완료 알림을 표시합니다. */
    private static void showAlarmSetNotification(Context context, String timeText) {
        ensureNotificationChannel(context);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("기상 마감 알람 설정됨")
                .setContentText(timeText + "에 알람이 설정되었습니다.")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID_DEADLINE, builder.build());
        }
    }

    /** 알림 채널을 생성합니다 (Android 8.0+). */
    private static void ensureNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "알람 정보", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("알람 설정 알림");
            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /** 기상 마감 알람을 시스템 시계 앱에서 제거합니다. */
    public static void dismissDeadlineAlarm(Context context) {
        dismissAlarmByLabel(context, DEADLINE_ALARM_LABEL);
    }

    /** 수면 목표 달성 알람을 시스템 시계 앱에서 제거합니다. */
    public static void dismissPreviousAlarm(Context context) {
        dismissAlarmByLabel(context, ALARM_LABEL);
    }

    /** 지정된 라벨의 알람을 시스템 시계 앱에서 제거합니다 (UI 전환 없이). */
    private static void dismissAlarmByLabel(Context context, String label) {
        try {
            Intent dismissIntent = new Intent(AlarmClock.ACTION_DISMISS_ALARM);
            dismissIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    | Intent.FLAG_ACTIVITY_NO_HISTORY);
            dismissIntent.putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE,
                    AlarmClock.ALARM_SEARCH_MODE_LABEL);
            dismissIntent.putExtra(AlarmClock.EXTRA_MESSAGE, label);
            context.startActivity(dismissIntent);
            Log.d(TAG, "Dismissed alarm with label: " + label);
        } catch (Exception e) {
            Log.w(TAG, "Failed to dismiss alarm (" + label + "): " + e.getMessage());
        }
    }

    /** 현재 시각 + 1분으로 시스템 알람을 설정합니다. */
    private void setSystemAlarm(Context context) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, 1);

        Intent alarmIntent = new Intent(AlarmClock.ACTION_SET_ALARM);
        alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        alarmIntent.putExtra(AlarmClock.EXTRA_HOUR, cal.get(Calendar.HOUR_OF_DAY));
        alarmIntent.putExtra(AlarmClock.EXTRA_MINUTES, cal.get(Calendar.MINUTE));
        alarmIntent.putExtra(AlarmClock.EXTRA_MESSAGE, ALARM_LABEL);
        alarmIntent.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        alarmIntent.putExtra(AlarmClock.EXTRA_VIBRATE, true);

        try {
            context.startActivity(alarmIntent);
            Log.i(TAG, "System alarm set for " + cal.get(Calendar.HOUR_OF_DAY)
                    + ":" + String.format("%02d", cal.get(Calendar.MINUTE)));
        } catch (Exception e) {
            Log.e(TAG, "Failed to set system alarm", e);
        }
    }
}
