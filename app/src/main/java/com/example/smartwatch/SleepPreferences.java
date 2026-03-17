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
    private static final String KEY_SLEEP_DETECTED = "sleep_detected";
    private static final String KEY_DEADLINE_HOUR   = "deadline_hour";
    private static final String KEY_DEADLINE_MINUTE = "deadline_minute";
    private static final String KEY_DEADLINE_MILLIS = "deadline_millis";
    private static final String KEY_THEME_MODE       = "theme_mode";
    private static final String KEY_SOUND_ENABLED    = "sound_enabled";
    private static final String KEY_SOUND_RES_ID     = "sound_res_id";
    private static final String KEY_SOUND_TITLE      = "sound_title";
    private static final String KEY_MONITORING_START_MILLIS = "monitoring_start_millis";

    /** 테마 모드 상수 */
    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT  = 1;
    public static final int THEME_DARK   = 2;

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

    /** 수면 상태가 처음 감지되었는지 여부 (수면 소리 자동 종료용). */
    public boolean isSleepDetected() {
        return prefs.getBoolean(KEY_SLEEP_DETECTED, false);
    }

    public void setSleepDetected(boolean detected) {
        prefs.edit().putBoolean(KEY_SLEEP_DETECTED, detected).apply();
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

    /** 모니터링 시작 시각(epoch millis). 이 시점 이후의 수면만 카운트한다. */
    public long getMonitoringStartMillis() {
        return prefs.getLong(KEY_MONITORING_START_MILLIS, 0L);
    }

    public void setMonitoringStartMillis(long millis) {
        prefs.edit().putLong(KEY_MONITORING_START_MILLIS, millis).apply();
    }

    public void reset() {
        prefs.edit()
             .putBoolean(KEY_MONITORING, false)
             .putBoolean(KEY_ALARM_FIRED, false)
             .putBoolean(KEY_SLEEP_DETECTED, false)
             .putLong(KEY_DEADLINE_MILLIS, 0L)
             .putLong(KEY_MONITORING_START_MILLIS, 0L)
             .apply();
    }

    // ── 테마 설정 ──────────────────────────────────────────────────

    /** 테마 모드 (0=시스템, 1=라이트, 2=다크). 기본값 시스템. */
    public int getThemeMode() {
        return prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM);
    }

    public void setThemeMode(int mode) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply();
    }

    // ── 소리 설정 ──────────────────────────────────────────────────

    /** 수면 소리 활성화 여부. 기본값 true. */
    public boolean isSoundEnabled() {
        return prefs.getBoolean(KEY_SOUND_ENABLED, true);
    }

    public void setSoundEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply();
    }

    /** 선택된 소리의 raw 리소스 ID. 0이면 미선택. */
    public int getSoundResId() {
        return prefs.getInt(KEY_SOUND_RES_ID, 0);
    }

    /** 선택된 소리의 표시 제목. */
    public String getSoundTitle() {
        return prefs.getString(KEY_SOUND_TITLE, "");
    }

    public void setSelectedSound(int resId, String title) {
        prefs.edit()
             .putInt(KEY_SOUND_RES_ID, resId)
             .putString(KEY_SOUND_TITLE, title)
             .apply();
    }
}
