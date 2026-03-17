package com.example.smartwatch

import android.content.ActivityNotFoundException
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
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val WORK_TAG = "sleep_monitor"

        /**
         * Health Connect 공식 API 방식으로 권한 문자열 Set 구성.
         * HealthPermission.getReadPermission()은 "androidx.health.permission.SleepSession" 형태의 String 반환.
         */
        val HEALTH_PERMISSIONS = setOf(
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
    private lateinit var pickerDeadline: TimePicker
    private lateinit var tvDeadlineSummary: TextView
    private lateinit var cardSleepData: View
    private lateinit var tvSessionTime: TextView
    private lateinit var tvActualSleepTime: TextView
    private lateinit var tvSleepDiff: TextView

    /**
     * 공식 Health Connect 권한 요청 런처.
     * PermissionController.createRequestPermissionResultContract()은
     * 내부적으로 com.android.healthconnect.controller(Android 14+) 또는
     * com.google.android.apps.healthdata(Android 13 이하)를 타겟으로 하는
     * ActivityResultContract를 반환한다.
     *
     * HC가 앱을 인식하려면 AndroidManifest에 아래 두 가지가 필수:
     *  1) <activity> with action=ACTION_SHOW_PERMISSIONS_RATIONALE
     *  2) <activity-alias> with action=VIEW_PERMISSION_USAGE + category=HEALTH_PERMISSIONS
     */
    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        Log.d(TAG, "HC permission result granted=$granted")
        val hasAll = granted.containsAll(HEALTH_PERMISSIONS)
        applyPermissionState(hasAll)
        if (!hasAll) {
            // 다이얼로그가 열렸지만 거부된 경우, 또는 HC가 앱을 인식 못해 즉시 빈 결과를 반환한 경우
            showPermissionManualGuide()
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

        pickerDeadline    = findViewById(R.id.picker_deadline)
        tvDeadlineSummary = findViewById(R.id.tv_deadline_summary)
        cardSleepData    = findViewById(R.id.card_sleep_data)
        tvSessionTime    = findViewById(R.id.tv_session_time)
        tvActualSleepTime = findViewById(R.id.tv_actual_sleep_time)
        tvSleepDiff      = findViewById(R.id.tv_sleep_diff)

        setupPickers()
        setupDeadlinePicker()
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
        // 앱으로 복귀할 때마다 권한 재확인 (HC 설정에서 수동 허용 후 복귀 시 자동 반영)
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
            Log.d(TAG, "Currently granted: $granted")
            applyPermissionState(granted.containsAll(HEALTH_PERMISSIONS))
        }
    }

    private fun applyPermissionState(granted: Boolean) {
        btnToggle.isEnabled     = granted
        btnPermission.isEnabled = !granted
        btnPermission.text = if (granted) "Health Connect 권한 허용됨 ✓"
                             else         "Health Connect 권한 허용"
        if (granted) loadSleepData()
        updateUI()
    }

    private fun loadSleepData() {
        lifecycleScope.launch {
            val data = runCatching {
                HealthConnectHelper(this@MainActivity).readSleepData()
            }.getOrNull()

            if (data != null) {
                cardSleepData.visibility = View.VISIBLE
                tvSessionTime.text = formatMinutes(data.sessionMinutes)
                tvActualSleepTime.text = formatMinutes(data.actualSleepMinutes)
                val diff = data.sessionMinutes - data.actualSleepMinutes
                tvSleepDiff.text = "-${formatMinutes(diff)}  "
            } else {
                cardSleepData.visibility = View.GONE
            }
        }
    }

    private fun formatMinutes(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return "${h}시간 ${m}분"
    }

    /**
     * 공식 Health Connect 권한 요청 진입점.
     *
     * PermissionController.createRequestPermissionResultContract()를 통해 생성된
     * permissionLauncher를 사용하는 것이 공식 권장 방식이다.
     * HC가 앱을 권한 목록에 표시하려면 AndroidManifest의 activity-alias가
     * 올바르게 선언되어야 한다.
     */
    private fun requestHealthPermissions() {
        val status = HealthConnectClient.getSdkStatus(this)
        Log.d(TAG, "requestHealthPermissions: HC status=$status")

        if (status != HealthConnectClient.SDK_AVAILABLE) {
            // HC가 없으면 Play Store로 이동
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=com.google.android.apps.healthdata")))
            } catch (e: ActivityNotFoundException) {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")))
            }
            return
        }

        // 공식 HC 권한 요청: PermissionController 런처 실행
        Log.d(TAG, "Launching HC permission dialog via PermissionController")
        permissionLauncher.launch(HEALTH_PERMISSIONS)
    }

    /**
     * permissionLauncher가 권한을 부여받지 못한 경우 (거부 또는 HC가 앱 미인식) 표시하는 안내 다이얼로그.
     *
     * 1순위: android.health.connect.action.MANAGE_HEALTH_PERMISSIONS 인텐트로
     *        이 앱 전용 HC 권한 페이지 직접 이동 (Android 14+)
     * 2순위: HC 앱 홈 화면으로 이동
     */
    private fun showPermissionManualGuide() {
        AlertDialog.Builder(this)
            .setTitle("수면 권한 허용 필요")
            .setMessage(
                "Health Connect에서 이 앱의 수면 권한을 직접 허용해주세요.\n\n" +
                "경로: Health Connect → 앱 권한 → 수면 알람 → '수면 세션 읽기' 허용\n\n" +
                "허용 후 앱으로 돌아오면 자동으로 반영됩니다."
            )
            .setPositiveButton("Health Connect 열기") { _, _ ->
                openHCPermissionsForThisApp()
            }
            .setNeutralButton("다시 시도") { _, _ ->
                permissionLauncher.launch(HEALTH_PERMISSIONS)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /**
     * 이 앱의 Health Connect 권한 페이지를 직접 여는 함수.
     *
     * Android 14+: MANAGE_HEALTH_PERMISSIONS + EXTRA_PACKAGE_NAME으로
     *              이 앱 전용 HC 권한 화면 직접 오픈
     * Fallback:    HC 앱 홈 화면 → 시스템 설정 순으로 시도
     */
    private fun openHCPermissionsForThisApp() {
        // 1순위: Android 14+ 전용 앱 권한 페이지
        try {
            val intent = Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS").apply {
                putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
            }
            startActivity(intent)
            Log.d(TAG, "Opened MANAGE_HEALTH_PERMISSIONS for $packageName")
            return
        } catch (e: Exception) {
            Log.w(TAG, "MANAGE_HEALTH_PERMISSIONS failed: ${e.message}")
        }

        // 2순위: HC 시스템 홈
        try {
            startActivity(Intent("android.health.connect.action.HEALTH_HOME_SETTINGS"))
            Log.d(TAG, "Opened HEALTH_HOME_SETTINGS")
            Toast.makeText(
                this,
                "앱 권한 → 수면 알람 → '수면 세션 읽기' 허용 후 돌아오세요.",
                Toast.LENGTH_LONG
            ).show()
            return
        } catch (e: Exception) {
            Log.w(TAG, "HEALTH_HOME_SETTINGS failed: ${e.message}")
        }

        // 3순위: HC 패키지 직접 실행
        val hcPackages = listOf(
            "com.android.healthconnect.controller",   // Android 14+ 내장
            "com.google.android.apps.healthdata",     // Android 13 이하 독립 앱
            "com.samsung.android.healthconnect"       // Samsung
        )
        for (pkg in hcPackages) {
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                Log.d(TAG, "Opening HC via package launch: $pkg")
                Toast.makeText(
                    this,
                    "앱 권한 → 수면 알람 → '수면 세션 읽기' 허용 후 돌아오세요.",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(launchIntent)
                return
            }
        }

        // 최후 수단: 시스템 설정
        Log.w(TAG, "All HC open attempts failed, opening system settings")
        Toast.makeText(this, "설정 → 개인정보 보호 → Health Connect에서 권한을 허용해주세요.", Toast.LENGTH_LONG).show()
        startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
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

        // 기상 마감 시간을 epoch millis로 계산하여 저장
        val deadlineHour = pickerDeadline.hour
        val deadlineMinute = pickerDeadline.minute
        val deadlineMillis = calculateDeadlineMillis(deadlineHour, deadlineMinute)
        prefs.setDeadlineMillis(deadlineMillis)

        // 마감 시간으로 시스템 알람 즉시 설정
        AlarmReceiver.setDeadlineAlarm(this, deadlineHour, deadlineMinute)

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WORK_TAG,
            ExistingPeriodicWorkPolicy.REPLACE,
            PeriodicWorkRequest.Builder(SleepMonitorWorker::class.java, 1, TimeUnit.MINUTES)
                .addTag(WORK_TAG).build()
        )
        Toast.makeText(this, "수면 모니터링을 시작합니다.", Toast.LENGTH_SHORT).show()
    }

    /**
     * 설정된 시/분을 기준으로 다음 마감 시각(epoch millis)을 계산합니다.
     * 현재 시각보다 과거면 다음 날로 설정합니다.
     */
    private fun calculateDeadlineMillis(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // 이미 지난 시간이면 다음 날로
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }

    private fun stopMonitoring() {
        prefs.reset()
        AlarmReceiver.dismissDeadlineAlarm(this)
        AlarmReceiver.dismissPreviousAlarm(this)
        WorkManager.getInstance(this).cancelAllWorkByTag(WORK_TAG)
        Toast.makeText(this, "수면 모니터링이 중지되었습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        val active = prefs.isMonitoringActive
        btnToggle.text  = if (active) "모니터링 중지" else "수면 모니터링 시작"
        tvStatus.text   = if (active) "수면 모니터링 중 (1분 주기 확인)" else "모니터링 중지됨"
        tvSleepProgress.visibility = if (active) View.VISIBLE else View.GONE
        if (active) tvSleepProgress.text = "수면 데이터 확인 중..."
        pickerHours.isEnabled   = !active
        pickerMinutes.isEnabled = !active
        pickerDeadline.isEnabled = !active
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

    private fun setupDeadlinePicker() {
        pickerDeadline.setIs24HourView(false)
        pickerDeadline.hour = prefs.deadlineHour
        pickerDeadline.minute = prefs.deadlineMinute
        updateDeadlineSummary(prefs.deadlineHour, prefs.deadlineMinute)

        pickerDeadline.setOnTimeChangedListener { _, hourOfDay, minute ->
            prefs.setDeadlineTime(hourOfDay, minute)
            updateDeadlineSummary(hourOfDay, minute)
        }
    }

    private fun updateDeadlineSummary(hour: Int, minute: Int) {
        val amPm = if (hour < 12) "오전" else "오후"
        val displayHour = if (hour % 12 == 0) 12 else hour % 12
        tvDeadlineSummary.text = "${amPm} ${displayHour}시 ${String.format("%02d", minute)}분까지 기상"
    }
}
