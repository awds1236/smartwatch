package com.example.smartwatch.wear;

import android.content.Intent;
import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * 폰에서 전송된 Wearable MessageClient 메시지를 수신하는 서비스.
 *
 * <p>/alarm/start  → WearAlarmActivity 실행 (진동 + 화면 표시)</p>
 * <p>/alarm/dismiss → 실행 중인 알람 종료</p>
 */
public class WearAlarmService extends WearableListenerService {

    private static final String TAG = "WearAlarmService";

    @Override
    public void onMessageReceived(MessageEvent event) {
        String path = event.getPath();
        Log.i(TAG, "Message received: " + path);

        if (MessagePaths.ALARM_START.equals(path)) {
            Intent intent = new Intent(this, WearAlarmActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);

        } else if (MessagePaths.ALARM_DISMISS.equals(path)) {
            // AlarmActivity가 살아있으면 broadcast로 종료 요청
            Intent stopIntent = new Intent(WearAlarmActivity.ACTION_DISMISS);
            sendBroadcast(stopIntent);
        }
    }
}
