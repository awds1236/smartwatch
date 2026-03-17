package com.example.smartwatch

import android.content.Context

/**
 * 수면 모니터링 상태를 SharedPreferences에 저장/조회하는 래퍼.
 */
class SleepPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("sleep_prefs", Context.MODE_PRIVATE)

    val isMonitoringActive: Boolean get() = prefs.getBoolean(KEY_MONITORING_ACTIVE, false)
    val isAlarmFired: Boolean get() = prefs.getBoolean(KEY_ALARM_FIRED, false)
    val goalMinutes: Int get() = prefs.getInt(KEY_GOAL_MINUTES, 480) // 기본값 8시간

    fun setMonitoringActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_MONITORING_ACTIVE, active).apply()
    }

    fun setAlarmFired(fired: Boolean) {
        prefs.edit().putBoolean(KEY_ALARM_FIRED, fired).apply()
    }

    fun setGoalMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_GOAL_MINUTES, minutes).apply()
    }

    fun reset() {
        prefs.edit()
            .putBoolean(KEY_MONITORING_ACTIVE, false)
            .putBoolean(KEY_ALARM_FIRED, false)
            .apply()
    }

    companion object {
        private const val KEY_MONITORING_ACTIVE = "monitoring_active"
        private const val KEY_ALARM_FIRED = "alarm_fired"
        private const val KEY_GOAL_MINUTES = "goal_minutes"
    }
}
