package com.example.smartwatch.wear;

import android.content.Intent;
import android.provider.AlarmClock;
import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.Calendar;

/**
 * 폰에서 전송된 Wearable MessageClient 메시지를 수신하는 서비스.
 *
 * <p>/alarm/start  → 시스템 시계 앱에 알람 설정</p>
 * <p>/alarm/dismiss → 이전 알람 제거</p>
 */
public class WearAlarmService extends WearableListenerService {

    private static final String TAG = "WearAlarmService";
    private static final String ALARM_LABEL = "수면 알람";

    @Override
    public void onMessageReceived(MessageEvent event) {
        String path = event.getPath();
        Log.i(TAG, "Message received: " + path);

        if (MessagePaths.ALARM_START.equals(path)) {
            dismissPreviousAlarm();
            setSystemAlarm();
        } else if (MessagePaths.ALARM_DISMISS.equals(path)) {
            dismissPreviousAlarm();
        }
    }

    private void dismissPreviousAlarm() {
        try {
            Intent dismissIntent = new Intent(AlarmClock.ACTION_DISMISS_ALARM);
            dismissIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            dismissIntent.putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE,
                    AlarmClock.ALARM_SEARCH_MODE_LABEL);
            dismissIntent.putExtra(AlarmClock.EXTRA_MESSAGE, ALARM_LABEL);
            startActivity(dismissIntent);
            Log.d(TAG, "Dismissed previous alarm on watch");
        } catch (Exception e) {
            Log.w(TAG, "Failed to dismiss previous alarm: " + e.getMessage());
        }
    }

    private void setSystemAlarm() {
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
            startActivity(alarmIntent);
            Log.i(TAG, "System alarm set on watch for " + cal.get(Calendar.HOUR_OF_DAY)
                    + ":" + String.format("%02d", cal.get(Calendar.MINUTE)));
        } catch (Exception e) {
            Log.e(TAG, "Failed to set system alarm on watch", e);
        }
    }
}
