package com.example.smartwatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * AlarmManager가 발사하는 알람을 수신하여 AlarmActivity를 실행합니다.
 * 또한 MainActivity의 UI 갱신을 위해 로컬 브로드캐스트도 전송합니다.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarm triggered — launching AlarmActivity")

        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(alarmIntent)

        // MainActivity statusReceiver에 UI 갱신 신호 전달
        context.sendBroadcast(Intent(AlarmReceiver::class.java.name))
    }
}
