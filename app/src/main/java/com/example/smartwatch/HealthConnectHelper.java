package com.example.smartwatch;

import android.content.Context;
import android.util.Log;

import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.records.SleepSessionRecord;
import androidx.health.connect.client.records.SleepSessionRecord.Stage;
import androidx.health.connect.client.request.ReadRecordsRequest;
import androidx.health.connect.client.time.TimeRangeFilter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Health Connect에서 수면 데이터를 읽어오는 헬퍼 클래스.
 *
 * <p>삼성 헬스는 Galaxy Watch의 수면 감지 결과를 자동으로 Health Connect에 기록합니다.
 * 이 클래스는 당일(전날 오후 6시 ~ 현재) SleepSessionRecord를 읽어
 * 실제 수면 시간(AWAKE 단계 제외)의 합산값(분)을 반환합니다.</p>
 */
public class HealthConnectHelper {

    private static final String TAG = "HealthConnectHelper";

    public interface SleepDataCallback {
        void onResult(long sleepMinutes);
        void onError(Exception e);
    }

    private final Context context;

    public HealthConnectHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Health Connect SDK 사용 가능 여부를 반환합니다.
     */
    public static boolean isAvailable(Context context) {
        int status = HealthConnectClient.getSdkStatus(context);
        return status == HealthConnectClient.SDK_AVAILABLE;
    }

    /**
     * 당일 수면 세션의 총 수면 시간(분)을 비동기로 반환합니다.
     * 별도 스레드에서 호출해야 합니다 (WorkManager Worker 내부 등).
     */
    public long readTotalSleepMinutesSync() throws Exception {
        HealthConnectClient client = HealthConnectClient.getOrCreate(context);

        // 수면 윈도우: 전날 오후 6시 ~ 현재
        Instant windowStart = LocalDate.now(ZoneId.systemDefault())
                .atTime(LocalTime.of(18, 0))
                .minusDays(1)
                .atZone(ZoneId.systemDefault())
                .toInstant();
        Instant windowEnd = Instant.now();

        ReadRecordsRequest<SleepSessionRecord> request =
                new ReadRecordsRequest.Builder<>(
                        SleepSessionRecord.class,
                        TimeRangeFilter.between(windowStart, windowEnd))
                        .build();

        List<SleepSessionRecord> records = client.readRecords(request).getRecords();

        long totalSleepMs = 0;
        for (SleepSessionRecord session : records) {
            List<Stage> stages = session.getStages();
            if (stages.isEmpty()) {
                // 수면 단계 정보 없으면 전체 세션 시간을 사용
                totalSleepMs += session.getEndTime().toEpochMilli()
                        - session.getStartTime().toEpochMilli();
            } else {
                for (Stage stage : stages) {
                    // AWAKE 단계는 제외하고 나머지(LIGHT, DEEP, REM, UNKNOWN) 합산
                    if (stage.getStage() != SleepSessionRecord.StageType.STAGE_TYPE_AWAKE) {
                        totalSleepMs += stage.getEndTime().toEpochMilli()
                                - stage.getStartTime().toEpochMilli();
                    }
                }
            }
        }

        long totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalSleepMs);
        Log.d(TAG, "Total sleep minutes: " + totalMinutes + " (from " + records.size() + " sessions)");
        return totalMinutes;
    }
}
