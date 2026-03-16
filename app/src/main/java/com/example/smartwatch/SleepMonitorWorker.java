package com.example.smartwatch;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * WorkManager Worker — 15분 주기로 Health Connect의 수면 데이터를 확인하고,
 * 목표 수면 시간 달성 시 AlarmReceiver를 통해 알람을 울립니다.
 */
public class SleepMonitorWorker extends Worker {

    private static final String TAG = "SleepMonitorWorker";

    public SleepMonitorWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        SleepPreferences prefs = new SleepPreferences(ctx);

        if (!prefs.isMonitoringActive()) {
            Log.d(TAG, "Monitoring is not active — stopping.");
            return Result.success();
        }

        if (prefs.isAlarmFired()) {
            Log.d(TAG, "Alarm already fired today — skipping.");
            return Result.success();
        }

        if (!HealthConnectHelper.isAvailable(ctx)) {
            Log.w(TAG, "Health Connect not available.");
            return Result.retry();
        }

        try {
            HealthConnectHelper helper = new HealthConnectHelper(ctx);
            long sleepMinutes = helper.readTotalSleepMinutesSync();
            long goalMinutes  = prefs.getGoalMinutes();

            Log.i(TAG, "Sleep: " + sleepMinutes + "min / Goal: " + goalMinutes + "min");

            if (sleepMinutes >= goalMinutes) {
                Log.i(TAG, "Goal reached! Triggering alarm.");
                prefs.setAlarmFired(true);
                triggerAlarm(ctx);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading sleep data", e);
            return Result.retry();
        }

        return Result.success();
    }

    private void triggerAlarm(Context ctx) {
        Intent intent = new Intent(ctx, AlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                ctx, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        // 즉시 발동
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 1000, pi);
    }
}
