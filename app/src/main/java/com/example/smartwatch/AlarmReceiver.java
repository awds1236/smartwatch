package com.example.smartwatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * AlarmManager로부터 트리거를 받아 AlarmActivity를 실행하고
 * 워치로 알람 신호를 전송하는 BroadcastReceiver.
 */
public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "Alarm triggered!");

        // 폰 알람 화면 실행
        Intent activityIntent = new Intent(context, AlarmActivity.class);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(activityIntent);

        // 워치 알람 전송 (백그라운드 스레드)
        new Thread(() -> WatchNotifier.sendAlarmStart(context)).start();
    }
}
