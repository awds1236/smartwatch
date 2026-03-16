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
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
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
        Log.d(TAG, "Permission result: $granted")
        val hasAll = granted.containsAll(HEALTH_PERMISSIONS)
        applyPermissionState(hasAll)
        if (!hasAll) {
            Toast.makeText(
                this,
                "권한이 거부되었습니다.\nHealth Connect 앱 → 앱 권한에서 직접 허용해주세요.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { updateUI() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs           = SleepPreferences(this)
        pickerHours     = findViewById(R.id.picker_hours)
        pickerMinutes   = findViewById(R.id.picker_minutes)
        tvStatus        = findViewById(R.id.tv_status)
        tvSleepProgress = findViewById(R.id.tv_sleep_progress)
        tvGoalSummary   = findViewById(R.id.tv_goal_summary)
        btnToggle       = findViewById(R.id.btn_toggle)
        btnPermission   = findViewById(R.id.btn_permission)

        setupPickers()
        btnPermission.setOnClickListener { requestHealthPermissions() }
        btnToggle.setOnClickListener { onToggleMonitoring() }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(AlarmReceiver::class.java.name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(statusReceiver, filter)
        }
        checkPermissionAndUpdateUI()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(statusReceiver)
    }

    // ── 권한 확인 ──────────────────────────────────────────────────

    private fun checkPermissionAndUpdateUI() {
        val status = HealthConnectClient.getSdkStatus(this)
        Log.d(TAG, "HC SDK status: $status")

        when (status) {
            HealthConnectClient.SDK_UNAVAILABLE -> {
                btnPermission.text = "Health Connect 설치 필요"
                btnPermission.isEnabled = true
                tvStatus.text = "Health Connect 앱이 필요합니다."
                return
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                btnPermission.text = "Health Connect 업데이트 필요"
                btnPermission.isEnabled = true
                tvStatus.text = "Health Connect를 업데이트해주세요."
                return
            }
        }

        lifecycleScope.launch {
            val granted = runCatching {
                HealthConnectClient.getOrCreate(this@MainActivity)
                    .permissionController
                    .getGrantedPermissions()
            }.getOrElse {
                Log.e(TAG, "getGrantedPermissions failed", it)
                emptySet()
            }
            Log.d(TAG, "Granted permissions: $granted")
            applyPermissionState(granted.containsAll(HEALTH_PERMISSIONS))
        }
    }

    private fun applyPermissionState(granted: Boolean) {
        btnToggle.isEnabled   = granted
        btnPermission.isEnabled = !granted
        btnPermission.text = if (granted) "Health Connect 권한 허용됨 ✓"
                             else         "Health Connect 권한 허용"
        updateUI()
    }

    private fun requestHealthPermissions() {
        val status = HealthConnectClient.getSdkStatus(this)
        if (status != HealthConnectClient.SDK_AVAILABLE) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")))
            return
        }

        // PermissionController 런처를 직접 실행 — resolveActivity 체크 없이 항상 시도
        // (Android 14 내장 HC는 resolveActivity가 null을 반환해도 실제론 처리 가능)
        try {
            Log.d(TAG, "Launching HC permission dialog")
            permissionLauncher.launch(HEALTH_PERMISSIONS)
        } catch (e: Exception) {
            // ActivityNotFoundException 등 — HC 설정 화면으로 직접 이동
            Log.w(TAG, "permissionLauncher failed: ${e.message}, falling back to HC settings")
            openHealthConnectSettings()
        }
    }

    /** Health Connect 앱(또는 시스템 설정)을 직접 열어 사용자가 수동 허용하도록 안내 */
    private fun openHealthConnectSettings() {
        val candidates = listOf(
            // Android 14+ 내장
            "com.android.healthconnect.controller",
            // Google Health Connect 독립 앱
            "com.google.android.apps.healthdata",
            // Samsung
            "com.samsung.android.healthconnect"
        )
        for (pkg in candidates) {
            val launch = packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                Log.d(TAG, "Opening HC via package: $pkg")
                Toast.makeText(
                    this,
                    "Health Connect → 앱 권한 → 수면 알람 → 수면 허용\n허용 후 돌아오면 자동 반영됩니다.",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(launch)
                return
            }
        }
        // 마지막 수단: 시스템 앱 설정
        Toast.makeText(this, "설정 → 개인정보 보호 → Health Connect 에서 권한을 허용해주세요.", Toast.LENGTH_LONG).show()
        try {
            startActivity(Intent("android.health.connect.action.HEALTH_HOME_SETTINGS"))
        } catch (e: Exception) {
            startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
        }
    }

    // ── 모니터링 ───────────────────────────────────────────────────

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

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WORK_TAG,
            ExistingPeriodicWorkPolicy.REPLACE,
            PeriodicWorkRequest.Builder(SleepMonitorWorker::class.java, 15, TimeUnit.MINUTES)
                .addTag(WORK_TAG).build()
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
        btnToggle.text  = if (active) "모니터링 중지" else "수면 모니터링 시작"
        tvStatus.text   = if (active) "수면 모니터링 중 (15분 주기 확인)" else "모니터링 중지됨"
        tvSleepProgress.visibility = if (active) View.VISIBLE else View.GONE
        if (active) tvSleepProgress.text = "수면 데이터 확인 중..."
        pickerHours.isEnabled   = !active
        pickerMinutes.isEnabled = !active
    }

    // ── Picker 초기화 ──────────────────────────────────────────────

    private fun setupPickers() {
        pickerHours.minValue = 0; pickerHours.maxValue = 12
        pickerMinutes.minValue = 0; pickerMinutes.maxValue = 59

        val saved = prefs.goalMinutes
        pickerHours.value   = saved / 60
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
}
