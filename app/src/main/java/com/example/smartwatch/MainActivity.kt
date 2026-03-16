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
import androidx.appcompat.app.AlertDialog
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
        private const val WORK_TAG = "sleep_monitor"
        private val HEALTH_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(SleepSessionRecord::class)
        )
    }

    private lateinit var prefs: SleepPreferences
    private lateinit var pickerHours: NumberPicker
    private lateinit var pickerMinutes: NumberPicker
    private lateinit var tvStatus: TextView
    private lateinit var tvSleepProgress: TextView
    private lateinit var tvGoalSummary: TextView
    private lateinit var btnToggle: Button
    private lateinit var btnPermission: Button

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        val hasAll = granted.containsAll(HEALTH_PERMISSIONS)
        btnToggle.isEnabled = hasAll
        if (hasAll) {
            btnPermission.text = "Health Connect 권한 허용됨 ✓"
            btnPermission.isEnabled = false
            Toast.makeText(this, "Health Connect 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "수면 데이터 권한이 필요합니다.", Toast.LENGTH_LONG).show()
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = SleepPreferences(this)
        pickerHours    = findViewById(R.id.picker_hours)
        pickerMinutes  = findViewById(R.id.picker_minutes)
        tvStatus       = findViewById(R.id.tv_status)
        tvSleepProgress = findViewById(R.id.tv_sleep_progress)
        tvGoalSummary  = findViewById(R.id.tv_goal_summary)
        btnToggle      = findViewById(R.id.btn_toggle)
        btnPermission  = findViewById(R.id.btn_permission)

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
        updateUI()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(statusReceiver)
    }

    private fun setupPickers() {
        pickerHours.minValue = 0
        pickerHours.maxValue = 12
        pickerMinutes.minValue = 0
        pickerMinutes.maxValue = 59

        val saved = prefs.goalMinutes
        pickerHours.value   = saved / 60
        pickerMinutes.value = saved % 60

        val listener = NumberPicker.OnValueChangeListener { _, _, _ ->
            val total = pickerHours.value * 60 + pickerMinutes.value
            prefs.setGoalMinutes(total)
            updateGoalSummary(total)
        }
        pickerHours.setOnValueChangedListener(listener)
        pickerMinutes.setOnValueChangedListener(listener)

        updateGoalSummary(saved)
    }

    private fun updateGoalSummary(totalMinutes: Int) {
        tvGoalSummary.text = "${totalMinutes / 60}시간 ${totalMinutes % 60}분 수면 목표"
    }

    private fun checkPermissionAndUpdateUI() {
        val status = HealthConnectClient.getSdkStatus(this)
        Log.d("MainActivity", "Health Connect SDK status: $status")

        when (status) {
            HealthConnectClient.SDK_UNAVAILABLE -> {
                btnPermission.text = "Health Connect 설치 필요 (탭하여 설치)"
                tvStatus.text = "Health Connect 앱이 설치되어 있지 않습니다."
                return
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                btnPermission.text = "Health Connect 업데이트 필요 (탭하여 업데이트)"
                tvStatus.text = "Health Connect 앱을 업데이트해주세요."
                return
            }
        }

        // SDK_AVAILABLE
        lifecycleScope.launch {
            val granted = HealthConnectClient.getOrCreate(this@MainActivity)
                .permissionController
                .getGrantedPermissions()
            val hasAll = granted.containsAll(HEALTH_PERMISSIONS)
            btnToggle.isEnabled = hasAll
            if (hasAll) {
                btnPermission.text = "Health Connect 권한 허용됨 ✓"
                btnPermission.isEnabled = false
            }
            updateUI()
        }
    }

    private fun requestHealthPermissions() {
        val status = HealthConnectClient.getSdkStatus(this)

        // 미설치 또는 업데이트 필요 → Play 스토어로 이동
        if (status != HealthConnectClient.SDK_AVAILABLE) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"
                )))
            } catch (e: Exception) {
                Toast.makeText(this, "Health Connect 앱을 Play 스토어에서 설치해주세요.", Toast.LENGTH_LONG).show()
            }
            return
        }

        // 권한 요청 시도
        try {
            permissionLauncher.launch(HEALTH_PERMISSIONS)
        } catch (e: Exception) {
            Log.e("MainActivity", "Permission launcher failed: ${e.message}")
            // fallback: Health Connect 설정 화면 직접 열기
            showHealthConnectFallbackDialog()
        }
    }

    private fun showHealthConnectFallbackDialog() {
        AlertDialog.Builder(this)
            .setTitle("Health Connect 권한 설정")
            .setMessage(
                "자동 권한 요청에 실패했습니다.\n\n" +
                "Health Connect 앱 → 앱 권한 → '수면 알람' → " +
                "'수면' 항목을 직접 허용해주세요."
            )
            .setPositiveButton("Health Connect 열기") { _, _ ->
                try {
                    startActivity(
                        packageManager.getLaunchIntentForPackage(
                            "com.google.android.apps.healthdata"
                        ) ?: Intent(Intent.ACTION_VIEW, Uri.parse(
                            "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"
                        ))
                    )
                } catch (e: Exception) {
                    Toast.makeText(this, "Health Connect 앱을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
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
        ).setConstraints(Constraints.Builder().build())
         .addTag(WORK_TAG)
         .build()

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
        tvStatus.text = if (active) "수면 모니터링 중 (15분 주기 확인)" else "모니터링 중지됨"
        tvSleepProgress.visibility = if (active) View.VISIBLE else View.GONE
        if (active) tvSleepProgress.text = "수면 데이터 확인 중..."
        pickerHours.isEnabled = !active
        pickerMinutes.isEnabled = !active
    }
}
