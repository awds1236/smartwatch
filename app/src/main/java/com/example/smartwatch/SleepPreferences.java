package com.example.smartwatch;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SharedPreferences 래퍼 — 목표 수면 시간 및 모니터링 상태 저장.
 */
public class SleepPreferences {

    private static final String PREFS_NAME        = "sleep_prefs";
    private static final String KEY_GOAL_MINUTES  = "goal_minutes";
    private static final String KEY_MONITORING    = "monitoring_active";
    private static final String KEY_ALARM_FIRED   = "alarm_fired";
    private static final String KEY_DEADLINE_HOUR   = "deadline_hour";
    private static final String KEY_DEADLINE_MINUTE = "deadline_minute";
    private static final String KEY_DEADLINE_MILLIS = "deadline_millis";

    private final SharedPreferences prefs;

    public SleepPreferences(Context context) {
        prefs = context.getApplicationContext()
                       .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** 목표 수면 시간 (분 단위). 기본값 480분(8시간). */
    public int getGoalMinutes() {
        return prefs.getInt(KEY_GOAL_MINUTES, 480);
    }

    public void setGoalMinutes(int minutes) {
        prefs.edit().putInt(KEY_GOAL_MINUTES, minutes).apply();
    }

    public boolean isMonitoringActive() {
        return prefs.getBoolean(KEY_MONITORING, false);
    }

    public void setMonitoringActive(boolean active) {
        prefs.edit().putBoolean(KEY_MONITORING, active).apply();
    }

    /** 오늘 이미 알람이 울렸는지 여부 (중복 방지). */
    public boolean isAlarmFired() {
        return prefs.getBoolean(KEY_ALARM_FIRED, false);
    }

    public void setAlarmFired(boolean fired) {
        prefs.edit().putBoolean(KEY_ALARM_FIRED, fired).apply();
    }

    /** 기상 마감 시간 (시). 기본값 8시. */
    public int getDeadlineHour() {
        return prefs.getInt(KEY_DEADLINE_HOUR, 8);
    }

    /** 기상 마감 시간 (분). 기본값 0분. */
    public int getDeadlineMinute() {
        return prefs.getInt(KEY_DEADLINE_MINUTE, 0);
    }

    public void setDeadlineTime(int hour, int minute) {
        prefs.edit()
             .putInt(KEY_DEADLINE_HOUR, hour)
             .putInt(KEY_DEADLINE_MINUTE, minute)
             .apply();
    }

    /** 모니터링 시작 시 계산된 마감 시각(epoch millis). */
    public long getDeadlineMillis() {
        return prefs.getLong(KEY_DEADLINE_MILLIS, 0L);
    }

    public void setDeadlineMillis(long millis) {
        prefs.edit().putLong(KEY_DEADLINE_MILLIS, millis).apply();
    }

    public void reset() {
        prefs.edit()
             .putBoolean(KEY_MONITORING, false)
             .putBoolean(KEY_ALARM_FIRED, false)
             .putLong(KEY_DEADLINE_MILLIS, 0L)
             .apply();
    }
}
