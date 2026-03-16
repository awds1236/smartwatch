package com.example.smartwatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.PermissionController;
import androidx.health.connect.client.permission.HealthPermission;
import androidx.health.connect.client.records.SleepSessionRecord;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String WORK_TAG = "sleep_monitor";

    private static final Set<String> HEALTH_PERMISSIONS = Set.of(
            HealthPermission.getReadPermission(SleepSessionRecord.class)
    );

    private SleepPreferences prefs;
    private NumberPicker pickerHours;
    private NumberPicker pickerMinutes;
    private TextView tvStatus;
    private TextView tvSleepProgress;
    private TextView tvGoalSummary;
    private Button btnToggle;
    private Button btnPermission;

    private boolean hasPermission = false;

    private ActivityResultLauncher<Set<String>> permissionLauncher;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // AlarmReceiver가 울리면 UI 상태 갱신
            updateUI();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new SleepPreferences(this);

        pickerHours    = findViewById(R.id.picker_hours);
        pickerMinutes  = findViewById(R.id.picker_minutes);
        tvStatus       = findViewById(R.id.tv_status);
        tvSleepProgress = findViewById(R.id.tv_sleep_progress);
        tvGoalSummary  = findViewById(R.id.tv_goal_summary);
        btnToggle      = findViewById(R.id.btn_toggle);
        btnPermission  = findViewById(R.id.btn_permission);

        setupPickers();
        setupPermissionLauncher();

        btnPermission.setOnClickListener(v -> requestHealthPermissions());
        btnToggle.setOnClickListener(v -> onToggleMonitoring());

        checkPermissionAndUpdateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(AlarmReceiver.class.getName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(statusReceiver, filter);
        }
        updateUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(statusReceiver);
    }

    private void setupPickers() {
        pickerHours.setMinValue(0);
        pickerHours.setMaxValue(12);

        pickerMinutes.setMinValue(0);
        pickerMinutes.setMaxValue(59);

        int savedGoal = prefs.getGoalMinutes();
        pickerHours.setValue(savedGoal / 60);
        pickerMinutes.setValue(savedGoal % 60);

        NumberPicker.OnValueChangeListener listener = (picker, oldVal, newVal) -> {
            int total = pickerHours.getValue() * 60 + pickerMinutes.getValue();
            prefs.setGoalMinutes(total);
            updateGoalSummary(total);
        };
        pickerHours.setOnValueChangedListener(listener);
        pickerMinutes.setOnValueChangedListener(listener);

        updateGoalSummary(savedGoal);
    }

    private void updateGoalSummary(int totalMinutes) {
        int h = totalMinutes / 60;
        int m = totalMinutes % 60;
        tvGoalSummary.setText(h + "시간 " + m + "분 수면 목표");
    }

    private void setupPermissionLauncher() {
        PermissionController.createRequestPermissionResultContract();
        permissionLauncher = registerForActivityResult(
                PermissionController.createRequestPermissionResultContract(),
                granted -> {
                    hasPermission = granted.containsAll(HEALTH_PERMISSIONS);
                    btnToggle.setEnabled(hasPermission);
                    if (hasPermission) {
                        btnPermission.setText("Health Connect 권한 허용됨 ✓");
                        btnPermission.setEnabled(false);
                        Toast.makeText(this, "Health Connect 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "수면 데이터 권한이 필요합니다.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void checkPermissionAndUpdateUI() {
        if (!HealthConnectHelper.isAvailable(this)) {
            btnPermission.setText("Health Connect 앱 설치 필요");
            btnPermission.setEnabled(false);
            tvStatus.setText("Health Connect가 설치되어 있지 않습니다.");
            return;
        }

        HealthConnectClient client = HealthConnectClient.getOrCreate(this);
        client.getPermissionController()
              .getGrantedPermissions()
              .addOnSuccessListener(granted -> {
                  hasPermission = granted.containsAll(HEALTH_PERMISSIONS);
                  btnToggle.setEnabled(hasPermission);
                  if (hasPermission) {
                      btnPermission.setText("Health Connect 권한 허용됨 ✓");
                      btnPermission.setEnabled(false);
                  }
                  updateUI();
              });
    }

    private void requestHealthPermissions() {
        if (!HealthConnectHelper.isAvailable(this)) {
            Toast.makeText(this, "Health Connect 앱을 먼저 설치해주세요.", Toast.LENGTH_LONG).show();
            return;
        }
        permissionLauncher.launch(HEALTH_PERMISSIONS);
    }

    private void onToggleMonitoring() {
        if (prefs.isMonitoringActive()) {
            stopMonitoring();
        } else {
            startMonitoring();
        }
        updateUI();
    }

    private void startMonitoring() {
        int goalMinutes = pickerHours.getValue() * 60 + pickerMinutes.getValue();
        if (goalMinutes < 30) {
            Toast.makeText(this, "최소 30분 이상 설정해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.setGoalMinutes(goalMinutes);
        prefs.setMonitoringActive(true);
        prefs.setAlarmFired(false);

        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .build();

        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                SleepMonitorWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag(WORK_TAG)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                WORK_TAG,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest);

        Toast.makeText(this, "수면 모니터링을 시작합니다.", Toast.LENGTH_SHORT).show();
    }

    private void stopMonitoring() {
        prefs.reset();
        WorkManager.getInstance(this).cancelAllWorkByTag(WORK_TAG);
        Toast.makeText(this, "수면 모니터링이 중지되었습니다.", Toast.LENGTH_SHORT).show();
    }

    private void updateUI() {
        boolean active = prefs.isMonitoringActive();
        if (active) {
            btnToggle.setText("모니터링 중지");
            tvStatus.setText("수면 모니터링 중 (15분 주기 확인)");
            tvSleepProgress.setVisibility(android.view.View.VISIBLE);
            tvSleepProgress.setText("수면 데이터 확인 중...");
            pickerHours.setEnabled(false);
            pickerMinutes.setEnabled(false);
        } else {
            btnToggle.setText("수면 모니터링 시작");
            tvStatus.setText("모니터링 중지됨");
            tvSleepProgress.setVisibility(android.view.View.GONE);
            pickerHours.setEnabled(true);
            pickerMinutes.setEnabled(true);
        }
    }
}
