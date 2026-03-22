package com.example.smartwatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 기기 부팅 완료 시 수면 모니터링 서비스를 자동으로 재시작합니다.
 * 사용자가 모니터링을 시작한 상태에서 기기가 재부팅되면,
 * SleepMonitorService가 자동으로 복구됩니다.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = SleepPreferences(context)

        if (!prefs.isMonitoringActive) {
            Log.d(TAG, "Monitoring not active — skipping service restart.")
            return
        }

        if (prefs.isAlarmFired) {
            Log.d(TAG, "Alarm already fired — skipping service restart.")
            return
        }

        // 마감 시간이 이미 지났으면 모니터링 리셋
        val deadlineMillis = prefs.deadlineMillis
        if (deadlineMillis > 0 && System.currentTimeMillis() > deadlineMillis) {
            Log.i(TAG, "Deadline already passed — resetting monitoring state.")
            prefs.reset()
            return
        }

        Log.i(TAG, "Boot completed — restarting SleepMonitorService.")
        SleepMonitorService.start(context)
    }
}