package com.example.smartwatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.AlarmClock;
import android.util.Log;

import java.util.Calendar;

/**
 * AlarmManager로부터 트리거를 받아 시스템 시계 앱에 알람을 설정하고
 * 워치로 알람 신호를 전송하는 BroadcastReceiver.
 */
public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";
    public static final String ALARM_LABEL = "수면 목표 달성";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "Alarm triggered!");

        // 이전 수면 알람 제거 (누적 방지)
        dismissPreviousAlarm(context);

        // 시스템 시계 앱으로 알람 설정
        setSystemAlarm(context);

        // 워치 알람 전송 (백그라운드 스레드)
        new Thread(() -> WatchNotifier.sendAlarmStart(context)).start();
    }

    /** 같은 라벨의 이전 알람을 시스템 시계 앱에서 제거합니다. */
    public static void dismissPreviousAlarm(Context context) {
        try {
            Intent dismissIntent = new Intent(AlarmClock.ACTION_DISMISS_ALARM);
            dismissIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            dismissIntent.putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE,
                    AlarmClock.ALARM_SEARCH_MODE_LABEL);
            dismissIntent.putExtra(AlarmClock.EXTRA_MESSAGE, ALARM_LABEL);
            context.startActivity(dismissIntent);
            Log.d(TAG, "Dismissed previous alarm with label: " + ALARM_LABEL);
        } catch (Exception e) {
            Log.w(TAG, "Failed to dismiss previous alarm: " + e.getMessage());
        }
    }

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
