package com.example.smartwatch.wear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.WindowManager;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.util.List;

/**
 * 갤럭시워치에서 표시되는 전체화면 알람 화면.
 * 진동을 반복하며 사용자가 '해제' 버튼을 누르면 폰에도 dismiss 메시지를 전송합니다.
 */
public class WearAlarmActivity extends AppCompatActivity {

    public static final String ACTION_DISMISS = "com.example.smartwatch.wear.DISMISS";

    private Vibrator vibrator;

    private final BroadcastReceiver dismissReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopAlarmAndFinish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        setContentView(R.layout.activity_wear_alarm);

        startVibration();

        Button btnDismiss = findViewById(R.id.btn_wear_dismiss);
        btnDismiss.setOnClickListener(v -> {
            // 폰에 dismiss 알림 전송 후 종료
            new Thread(this::notifyPhoneDismiss).start();
            stopAlarmAndFinish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(ACTION_DISMISS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dismissReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(dismissReceiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(dismissReceiver);
    }

    private void startVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
            vibrator = vm.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        }
        if (vibrator != null) {
            long[] pattern = {0, 600, 400, 600, 400};
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
        }
    }

    private void stopAlarmAndFinish() {
        if (vibrator != null) vibrator.cancel();
        finish();
    }

    private void notifyPhoneDismiss() {
        try {
            List<Node> nodes = Tasks.await(
                    Wearable.getNodeClient(this).getConnectedNodes());
            for (Node node : nodes) {
                Tasks.await(Wearable.getMessageClient(this)
                        .sendMessage(node.getId(), MessagePaths.ALARM_DISMISS, new byte[0]));
            }
        } catch (Exception e) {
            // 전송 실패는 무시 (워치 해제가 우선)
        }
    }

    @Override
    protected void onDestroy() {
        if (vibrator != null) vibrator.cancel();
        super.onDestroy();
    }
}
