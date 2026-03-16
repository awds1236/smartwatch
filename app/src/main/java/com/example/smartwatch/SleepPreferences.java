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

    public void reset() {
        prefs.edit()
             .putBoolean(KEY_MONITORING, false)
             .putBoolean(KEY_ALARM_FIRED, false)
             .apply();
    }
}
