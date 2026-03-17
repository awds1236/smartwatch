package com.example.smartwatch;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.AlarmClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.Calendar;

/**
 * AlarmManager로부터 트리거를 받아 알람을 처리하는 BroadcastReceiver.
 *
 * 두 가지 알람을 처리합니다:
 * 1. 수면 목표 달성 알람 (SleepMonitorWorker에서 트리거)
 * 2. 기상 마감 알람 (AlarmManager.setAlarmClock으로 예약)
 */
public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";
    private static final String CHANNEL_ID = "alarm_info_channel";
    private static final int NOTIFICATION_ID_DEADLINE = 1001;

    public static final String ALARM_LABEL = "수면 목표 달성";
    public static final String DEADLINE_ALARM_LABEL = "기상 마감 알람";

    /** 기상 마감 알람 Action (AlarmManager용) */
    public static final String ACTION_DEADLINE = "com.example.smartwatch.ACTION_DEADLINE";

    /** PendingIntent 구분용 request code */
    private static final int RC_DEADLINE = 100;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_DEADLINE.equals(action)) {
            // ── 기상 마감 알람 ──
            Log.i(TAG, "Deadline alarm triggered!");
            cancelCountdownNotification(context);

            Intent alarmUi = new Intent(context, AlarmActivity.class);
            alarmUi.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(alarmUi);

            new Thread(() -> WatchNotifier.sendAlarmStart(context)).start();
        } else {
            // ── 수면 목표 달성 알람 ──
            Log.i(TAG, "Goal alarm triggered!");

            // 마감 알람은 더 이상 필요 없으므로 취소
            cancelDeadlineAlarm(context);
            cancelCountdownNotification(context);

            // 알람 화면 표시
            Intent alarmUi = new Intent(context, AlarmActivity.class);
            alarmUi.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(alarmUi);

            new Thread(() -> WatchNotifier.sendAlarmStart(context)).start();
        }
    }

    // ── 기상 마감 알람 (AlarmManager 직접 관리) ──────────────────────

    /**
     * 기상 마감 알람을 AlarmManager.setAlarmClock()으로 예약합니다.
     * 시스템 상태바에 알람 아이콘이 표시되며, 앱에서 완전히 취소할 수 있습니다.
     */
    public static void setDeadlineAlarm(Context context, int hour, int minute) {
        // 기존 마감 알람이 있으면 먼저 취소
        cancelDeadlineAlarm(context);

        // 트리거 시간 계산
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        long triggerMillis = cal.getTimeInMillis();

        // AlarmReceiver를 트리거하는 PendingIntent
        PendingIntent alarmPi = getDeadlinePendingIntent(context);

        // 알람 탭 시 앱을 여는 PendingIntent (상태바 알람 아이콘 클릭용)
        Intent showIntent = new Intent(context, MainActivity.class);
        PendingIntent showPi = PendingIntent.getActivity(context, RC_DEADLINE, showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(triggerMillis, showPi);
        am.setAlarmClock(info, alarmPi);

        String timeText = String.format("%d:%02d", hour, minute);
        Log.i(TAG, "Deadline alarm scheduled for " + timeText + " (millis=" + triggerMillis + ")");
        showAlarmSetNotification(context, timeText);
    }

    /**
     * 기상 마감 알람을 완전히 취소합니다 (시스템 상태바 아이콘도 사라짐).
     */
    public static void cancelDeadlineAlarm(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = getDeadlinePendingIntent(context);
        am.cancel(pi);
        // pi.cancel()은 호출하지 않음 — PendingIntent를 무효화하면
        // 이후 setDeadlineAlarm()에서 같은 request code로 재생성이 실패할 수 있음
        Log.d(TAG, "Deadline alarm cancelled");
    }

    /** 기상 마감 알람용 PendingIntent를 생성/조회합니다. */
    private static PendingIntent getDeadlinePendingIntent(Context context) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction(ACTION_DEADLINE);
        return PendingIntent.getBroadcast(context, RC_DEADLINE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // ── 하위호환: 시계 앱 알람 라벨로 dismiss ─────────────────────────

    /** @deprecated 시계 앱에 직접 만든 알람용. 새 코드에서는 cancelDeadlineAlarm 사용. */
    public static void dismissDeadlineAlarm(Context context) {
        dismissAlarmByLabel(context, DEADLINE_ALARM_LABEL);
    }

    public static void dismissPreviousAlarm(Context context) {
        dismissAlarmByLabel(context, ALARM_LABEL);
    }

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

    // ── 알림 ──────────────────────────────────────────────────────────

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

    /** MainActivity에서 표시하는 카운트다운 알림을 제거합니다. */
    public static void cancelCountdownNotification(Context context) {
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(3001); // MainActivity.COUNTDOWN_NOTIFICATION_ID
        }
    }
}
