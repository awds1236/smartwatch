package com.example.smartwatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val WORK_TAG = "sleep_monitor"

        // Health Connect SDK 권한 문자열
        private val HEALTH_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(SleepSessionRecord::class)
        )

        // Android 14+ 런타임 권한 문자열
        private const val SLEEP_PERMISSION = "android.permission.health.READ_SLEEP_SESSION"
    }

    private lateinit var prefs: SleepPreferences
    private lateinit var pickerHours: NumberPicker
    private lateinit var pickerMinutes: NumberPicker
    private lateinit var tvStatus: TextView
    private lateinit var tvSleepProgress: TextView
    private lateinit var tvGoalSummary: TextView
    private lateinit var btnToggle: Button
    private lateinit var btnPermission: Button

    // Android 14+ 표준 런타임 권한 요청
    private val runtimePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[SLEEP_PERMISSION] == true
        Log.d(TAG, "Runtime permission result: $results")
        onPermissionResult(granted)
    }

    // Android 13 이하 Health Connect SDK 방식
    private val sdkPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        Log.d(TAG, "SDK permission result: $granted")
        onPermissionResult(granted.containsAll(HEALTH_PERMISSIONS))
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { updateUI() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs        = SleepPreferences(this)
        pickerHours  = findViewById(R.id.picker_hours)
        pickerMinutes = findViewById(R.id.picker_minutes)
        tvStatus     = findViewById(R.id.tv_status)
        tvSleepProgress = findViewById(R.id.tv_sleep_progress)
        tvGoalSummary = findViewById(R.id.tv_goal_summary)
        btnToggle    = findViewById(R.id.btn_toggle)
        btnPermission = findViewById(R.id.btn_permission)

        setupPickers()
        btnPermission.setOnClickListener { requestHealthPermissions() }
        btnToggle.setOnClickListener { onToggleMonitoring() }

        checkPermissionAndUpdateUI()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(AlarmReceiver::class.java.name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }
        // 설정에서 돌아왔을 때 권한 상태 재확인
        checkPermissionAndUpdateUI()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(statusReceiver)
    }

    private fun setupPickers() {
        pickerHours.minValue = 0; pickerHours.maxValue = 12
        pickerMinutes.minValue = 0; pickerMinutes.maxValue = 59

        val saved = prefs.goalMinutes
        pickerHours.value = saved / 60
        pickerMinutes.value = saved % 60

        val onChange = NumberPicker.OnValueChangeListener { _, _, _ ->
            val total = pickerHours.value * 60 + pickerMinutes.value
            prefs.setGoalMinutes(total)
            updateGoalSummary(total)
        }
        pickerHours.setOnValueChangedListener(onChange)
        pickerMinutes.setOnValueChangedListener(onChange)
        updateGoalSummary(saved)
    }

    private fun updateGoalSummary(totalMinutes: Int) {
        tvGoalSummary.text = "${totalMinutes / 60}시간 ${totalMinutes % 60}분 수면 목표"
    }

    private fun checkPermissionAndUpdateUI() {
        val sdkStatus = HealthConnectClient.getSdkStatus(this)
        Log.d(TAG, "HC SDK status: $sdkStatus")

        if (sdkStatus == HealthConnectClient.SDK_UNAVAILABLE) {
            btnPermission.text = "Health Connect 설치 필요"
            tvStatus.text = "Health Connect 앱이 필요합니다."
            return
        }
        if (sdkStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            btnPermission.text = "Health Connect 업데이트 필요"
            tvStatus.text = "Health Connect를 업데이트해주세요."
            return
        }

        // Android 14+ : 표준 권한으로 확인
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val granted = checkSelfPermission(SLEEP_PERMISSION) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Android 14+ runtime permission granted: $granted")
            applyPermissionState(granted)
        } else {
            // Android 13 이하 : HC SDK로 확인
            lifecycleScope.launch {
                val granted = HealthConnectClient.getOrCreate(this@MainActivity)
                    .permissionController.getGrantedPermissions()
                    .containsAll(HEALTH_PERMISSIONS)
                applyPermissionState(granted)
            }
        }
    }

    private fun applyPermissionState(granted: Boolean) {
        btnToggle.isEnabled = granted
        if (granted) {
            btnPermission.text = "Health Connect 권한 허용됨 ✓"
            btnPermission.isEnabled = false
        } else {
            btnPermission.text = "Health Connect 권한 허용"
            btnPermission.isEnabled = true
        }
        updateUI()
    }

    private fun requestHealthPermissions() {
        val sdkStatus = HealthConnectClient.getSdkStatus(this)
        if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")))
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ : 표준 런타임 권한 요청
            Log.d(TAG, "Requesting via runtime permission (Android 14+)")
            runtimePermissionLauncher.launch(arrayOf(SLEEP_PERMISSION))
        } else {
            // Android 13 이하 : Health Connect SDK 방식
            Log.d(TAG, "Requesting via HC SDK (Android 13-)")
            sdkPermissionLauncher.launch(HEALTH_PERMISSIONS)
        }
    }

    private fun onPermissionResult(granted: Boolean) {
        applyPermissionState(granted)
        if (granted) {
            Toast.makeText(this, "수면 데이터 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "수면 데이터 권한이 거부되었습니다.\nHealth Connect에서 직접 허용해주세요.", Toast.LENGTH_LONG).show()
        }
    }

    private fun onToggleMonitoring() {
        if (prefs.isMonitoringActive) stopMonitoring() else startMonitoring()
        updateUI()
    }

    private fun startMonitoring() {
        val goalMinutes = pickerHours.value * 60 + pickerMinutes.value
        if (goalMinutes < 30) {
            Toast.makeText(this, "최소 30분 이상 설정해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.setGoalMinutes(goalMinutes)
        prefs.setMonitoringActive(true)
        prefs.setAlarmFired(false)

        val workRequest = PeriodicWorkRequest.Builder(
            SleepMonitorWorker::class.java, 15, TimeUnit.MINUTES
        ).addTag(WORK_TAG).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WORK_TAG, ExistingPeriodicWorkPolicy.REPLACE, workRequest
        )
        Toast.makeText(this, "수면 모니터링을 시작합니다.", Toast.LENGTH_SHORT).show()
    }

    private fun stopMonitoring() {
        prefs.reset()
        WorkManager.getInstance(this).cancelAllWorkByTag(WORK_TAG)
        Toast.makeText(this, "수면 모니터링이 중지되었습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        val active = prefs.isMonitoringActive
        btnToggle.text = if (active) "모니터링 중지" else "수면 모니터링 시작"
        tvStatus.text  = if (active) "수면 모니터링 중 (15분 주기 확인)" else "모니터링 중지됨"
        tvSleepProgress.visibility = if (active) View.VISIBLE else View.GONE
        if (active) tvSleepProgress.text = "수면 데이터 확인 중..."
        pickerHours.isEnabled   = !active
        pickerMinutes.isEnabled = !active
    }
}
