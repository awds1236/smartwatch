package com.example.smartwatch

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val COUNTDOWN_CHANNEL_ID = "deadline_countdown_channel"
        private const val COUNTDOWN_NOTIFICATION_ID = 3001
        private const val COUNTDOWN_INTERVAL_MS = 60_000L // 1분
        private const val ALARM_CANCELLED_NOTIFICATION_ID = 3002

        /**
         * Health Connect 공식 API 방식으로 권한 문자열 Set 구성.
         * HealthPermission.getReadPermission()은 "androidx.health.permission.SleepSession" 형태의 String 반환.
         */
        val HEALTH_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(SleepSessionRecord::class)
        )
    }

    private lateinit var prefs: SleepPreferences
    private lateinit var presetManager: PresetManager
    private lateinit var presetSection: View
    private lateinit var presetContainer: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var tvSleepProgress: TextView
    private lateinit var btnToggle: Button
    private lateinit var btnPermission: Button
    private lateinit var pickerDeadline: TimePicker
    private lateinit var tvDeadlineSummary: TextView
    private lateinit var cardSleepData: View
    private lateinit var tvSessionTime: TextView
    private lateinit var tvActualSleepTime: TextView
    private lateinit var tvSleepDiff: TextView
    private lateinit var tvDeadlineCountdown: TextView
    private lateinit var cardActiveAlarm: View
    private lateinit var tvAlarmTime: TextView
    private lateinit var tvAlarmAmPm: TextView
    private lateinit var tvAlarmRemaining: TextView

    private val countdownHandler = Handler(Looper.getMainLooper())
    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (prefs.isMonitoringActive) {
                updateCountdown()
                countdownHandler.postDelayed(this, COUNTDOWN_INTERVAL_MS)
            }
        }
    }

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

    /**
     * 알림 권한 요청 런처 (Android 13+).
     * 결과와 무관하게 알림은 best-effort로 표시하므로 그냥 무시.
     */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 거부해도 모니터링 자체에는 영향 없음 */ }

    /**
     * ACTIVITY_RECOGNITION 권한 요청 런처.
     * targetSDK 35에서 foregroundServiceType="health" 서비스 시작 시 필수.
     */
    private val activityRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(TAG, "ACTIVITY_RECOGNITION granted=$granted")
        if (!granted) {
            Toast.makeText(this, "수면 모니터링을 위해 활동 인식 권한이 필요합니다.", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 수면 소리 선택 화면에서 돌아오면 실제 모니터링을 시작한다.
     */
    private val sleepSoundsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            doStartMonitoring()
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { updateUI() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 저장된 테마 적용
        val tempPrefs = SleepPreferences(this)
        SettingsActivity.applyTheme(tempPrefs.themeMode)

        // 상태바/네비게이션바 색상 강제 적용 (Edge-to-Edge 투명 방지)
        window.statusBarColor = getColor(R.color.sleep_bg_dark)
        window.navigationBarColor = getColor(R.color.sleep_bg_dark)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false   // 밝은 아이콘 (다크 배경)
            isAppearanceLightNavigationBars = false
        }

        setContentView(R.layout.activity_main)

        prefs           = tempPrefs
        presetManager   = PresetManager(this)
        presetSection   = findViewById(R.id.preset_section)
        presetContainer = findViewById(R.id.preset_container)
        tvStatus        = findViewById(R.id.tv_status)
        tvSleepProgress = findViewById(R.id.tv_sleep_progress)
        btnToggle       = findViewById(R.id.btn_toggle)
        btnPermission   = findViewById(R.id.btn_permission)

        pickerDeadline    = findViewById(R.id.picker_deadline)
        tvDeadlineSummary = findViewById(R.id.tv_deadline_summary)
        cardSleepData    = findViewById(R.id.card_sleep_data)
        tvSessionTime    = findViewById(R.id.tv_session_time)
        tvActualSleepTime = findViewById(R.id.tv_actual_sleep_time)
        tvSleepDiff      = findViewById(R.id.tv_sleep_diff)
        tvDeadlineCountdown = findViewById(R.id.tv_deadline_countdown)
        cardActiveAlarm     = findViewById(R.id.card_active_alarm)
        tvAlarmTime         = findViewById(R.id.tv_alarm_time)
        tvAlarmAmPm         = findViewById(R.id.tv_alarm_ampm)
        tvAlarmRemaining    = findViewById(R.id.tv_alarm_remaining)

        createCountdownNotificationChannel()
        requestNotificationPermission()
        requestActivityRecognitionPermission()
        setupDeadlinePicker()
        btnPermission.setOnClickListener { requestHealthPermissions() }
        btnToggle.setOnClickListener { onToggleMonitoring() }
        findViewById<View>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.btn_save_preset).setOnClickListener { showSavePresetDialog() }
        refreshPresetList()
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
        startCountdownTimer()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(statusReceiver)
        countdownHandler.removeCallbacks(countdownRunnable)
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
                val startMillis = prefs.monitoringStartMillis
                HealthConnectHelper(this@MainActivity).readSleepData(startMillis)
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
        // 수면 소리 선택 화면을 먼저 열고, 결과를 받으면 실제 모니터링 시작
        sleepSoundsLauncher.launch(Intent(this, SleepSoundsActivity::class.java))
    }

    /** 수면 소리 선택(또는 스킵) 후 실제 모니터링을 시작한다. */
    private fun doStartMonitoring() {
        // ACTIVITY_RECOGNITION 권한 확인 (foregroundServiceType="health" 필수)
        if (!hasActivityRecognitionPermission()) {
            Toast.makeText(this, "활동 인식 권한이 필요합니다. 권한을 허용해주세요.", Toast.LENGTH_LONG).show()
            requestActivityRecognitionPermission()
            return
        }

        // Android 12+: 정확한 알람 권한 확인
        if (!canScheduleExactAlarms()) {
            Toast.makeText(this, "알람 설정을 위해 권한을 허용해주세요.", Toast.LENGTH_LONG).show()
            requestExactAlarmPermission()
            return
        }

        // Android 14+: 전체 화면 알림 권한 확인 (소리/진동은 작동하지만 화면 표시에 필요)
        checkFullScreenIntentPermission()

        prefs.setMonitoringActive(true)
        prefs.setAlarmFired(false)
        prefs.setSleepDetected(false)
        prefs.setMonitoringStartMillis(System.currentTimeMillis())

        // 기상 마감 시간을 epoch millis로 계산하여 저장
        val deadlineHour = pickerDeadline.hour
        val deadlineMinute = pickerDeadline.minute
        val deadlineMillis = calculateDeadlineMillis(deadlineHour, deadlineMinute)
        prefs.setDeadlineMillis(deadlineMillis)

        // 마감 시간으로 시스템 알람 설정 (내부에서 기존 알람 삭제 후 새로 생성)
        AlarmReceiver.setDeadlineAlarm(this, deadlineHour, deadlineMinute)

        // Foreground Service로 수면 모니터링 시작 (Doze 모드에서도 안정 동작)
        SleepMonitorService.start(this)
        showDeadlineNotification(deadlineHour, deadlineMinute)
        startCountdownTimer()

        Toast.makeText(this, "수면 모니터링을 시작합니다.", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    /** Android 12+에서 정확한 알람 예약이 허용되어 있는지 확인합니다. */
    private fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    /** ACTIVITY_RECOGNITION 권한을 요청합니다 (foregroundServiceType="health" 필수). */
    private fun requestActivityRecognitionPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val perm = android.Manifest.permission.ACTIVITY_RECOGNITION
            if (checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                activityRecognitionLauncher.launch(perm)
            }
        }
    }

    /** ACTIVITY_RECOGNITION 권한이 부여되었는지 확인합니다. */
    private fun hasActivityRecognitionPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /** Android 13+에서 알림 권한을 요청합니다. */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val perm = android.Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(perm)
            }
        }
    }

    /** Android 14+에서 전체 화면 알림 권한을 확인하고 안내합니다. */
    private fun checkFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = getSystemService(NotificationManager::class.java)
            if (!nm.canUseFullScreenIntent()) {
                AlertDialog.Builder(this)
                    .setTitle("전체 화면 알람 권한 필요")
                    .setMessage(
                        "알람이 화면에 자동으로 표시되려면 전체 화면 알림 권한이 필요합니다.\n\n" +
                        "이 권한이 없어도 알람 소리와 진동은 작동하지만, " +
                        "알람 화면이 자동으로 나타나지 않을 수 있습니다."
                    )
                    .setPositiveButton("설정 열기") { _, _ ->
                        startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                            data = Uri.parse("package:$packageName")
                        })
                    }
                    .setNegativeButton("나중에", null)
                    .show()
            }
        }
    }

    /** 정확한 알람 권한 설정 화면으로 이동합니다. */
    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            })
        }
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
        SleepSoundService.stop(this)
        SleepMonitorService.stop(this)

        // AlarmManager 기반이므로 즉시 취소 (Activity 시작 없음)
        AlarmReceiver.cancelDeadlineAlarm(this)

        // 카운트다운 타이머 & 알림 정리
        countdownHandler.removeCallbacks(countdownRunnable)
        cancelDeadlineNotification()

        // 알람 종료 알림 표시
        showAlarmCancelledNotification()

        Toast.makeText(this, "수면 모니터링이 중지되었습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        val active = prefs.isMonitoringActive
        btnToggle.text  = if (active) "모니터링 중지" else "수면 모니터링 시작"
        tvStatus.text   = if (active) "수면 모니터링 중" else "모니터링 중지됨"
        tvSleepProgress.visibility = if (active) View.VISIBLE else View.GONE
        tvDeadlineCountdown.visibility = if (active) View.VISIBLE else View.GONE
        if (active) {
            tvSleepProgress.text = "수면 데이터 확인 중..."
            updateCountdown()
        }
        pickerDeadline.isEnabled = !active
        updateActiveAlarmCard()
    }

    // ── Picker 초기화 ──────────────────────────────────────────────

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

    // ── 카운트다운 타이머 ────────────────────────────────────────────

    private fun startCountdownTimer() {
        countdownHandler.removeCallbacks(countdownRunnable)
        if (prefs.isMonitoringActive && prefs.deadlineMillis > 0) {
            countdownRunnable.run()
        }
    }

    private fun updateCountdown() {
        val deadlineMillis = prefs.deadlineMillis
        if (deadlineMillis <= 0L) return

        val remainMs = deadlineMillis - System.currentTimeMillis()
        if (remainMs <= 0L) {
            tvDeadlineCountdown.text = "알람 시간 도달"
            return
        }

        val totalMin = (remainMs / 60_000L).toInt()
        val hours = totalMin / 60
        val minutes = totalMin % 60
        tvDeadlineCountdown.text = "알람까지 ${hours}시간 ${minutes}분 남음"
    }

    // ── 활성 알람 카드 ─────────────────────────────────────────────

    private fun updateActiveAlarmCard() {
        val active = prefs.isMonitoringActive
        cardActiveAlarm.visibility = if (active) View.VISIBLE else View.GONE
        if (!active) return

        val hour = prefs.deadlineHour
        val minute = prefs.deadlineMinute
        val amPm = if (hour < 12) "오전" else "오후"
        val displayHour = if (hour % 12 == 0) 12 else hour % 12
        tvAlarmTime.text = "${displayHour}:${String.format("%02d", minute)}"
        tvAlarmAmPm.text = amPm

        // 남은 시간
        val deadlineMillis = prefs.deadlineMillis
        if (deadlineMillis > 0) {
            val remainMs = deadlineMillis - System.currentTimeMillis()
            if (remainMs > 0) {
                val totalMin = (remainMs / 60_000L).toInt()
                val h = totalMin / 60
                val m = totalMin % 60
                tvAlarmRemaining.text = "${h}시간 ${m}분 후 울림"
            } else {
                tvAlarmRemaining.text = "알람 시간 도달"
            }
        }

    }

    // ── 알림 (Notification) ─────────────────────────────────────────

    private fun createCountdownNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                COUNTDOWN_CHANNEL_ID,
                "기상 알람 정보",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "기상 마감 알람 시간 알림"
                setSound(null, null)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun showDeadlineNotification(hour: Int, minute: Int) {
        val amPm = if (hour < 12) "오전" else "오후"
        val displayHour = if (hour % 12 == 0) 12 else hour % 12
        val timeText = "${amPm} ${displayHour}시 ${String.format("%02d", minute)}분"

        val deadlineMillis = prefs.deadlineMillis
        val remainMs = deadlineMillis - System.currentTimeMillis()
        val totalMin = if (remainMs > 0) (remainMs / 60_000L).toInt() else 0
        val h = totalMin / 60
        val m = totalMin % 60

        val openPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, COUNTDOWN_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("기상 알람: $timeText")
            .setContentText("${h}시간 ${m}분 후에 알람이 울립니다")
            .setContentIntent(openPi)
            .setOngoing(true)
            .setWhen(deadlineMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(COUNTDOWN_NOTIFICATION_ID, notification)
    }

    private fun cancelDeadlineNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.cancel(COUNTDOWN_NOTIFICATION_ID)
    }

    private fun showAlarmCancelledNotification() {
        val notification = NotificationCompat.Builder(this, COUNTDOWN_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("알람 종료")
            .setContentText("설정된 기상 알람이 해제되었습니다.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(ALARM_CANCELLED_NOTIFICATION_ID, notification)
    }

    // ── 프리셋 ──────────────────────────────────────────────────────

    private fun showSavePresetDialog() {
        val input = EditText(this).apply {
            hint = "프리셋 이름 (예: 평일, 주말)"
            setTextColor(getColor(R.color.sleep_text_primary))
            setHintTextColor(getColor(R.color.sleep_text_secondary))
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("프리셋 저장")
            .setMessage("현재 설정을 저장합니다.")
            .setView(input)
            .setPositiveButton("저장") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                // 현재 picker 값을 먼저 저장
                prefs.setDeadlineTime(pickerDeadline.hour, pickerDeadline.minute)

                val preset = presetManager.createFromCurrent(name, prefs)
                presetManager.save(preset)
                refreshPresetList()
                Toast.makeText(this, "'${name}' 프리셋이 저장되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun refreshPresetList() {
        val presets = presetManager.all
        presetContainer.removeAllViews()

        if (presets.isEmpty()) {
            presetSection.visibility = View.GONE
            return
        }

        presetSection.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(this)

        for (preset in presets) {
            val card = inflater.inflate(R.layout.item_preset_card, presetContainer, false)
            card.findViewById<TextView>(R.id.tv_preset_name).text = preset.name
            card.findViewById<TextView>(R.id.tv_preset_deadline).text = preset.deadlineText

            // 원터치 시작: 프리셋 로드 → 모니터링 시작
            card.setOnClickListener { loadPresetAndStart(preset) }

            // 삭제 버튼
            card.findViewById<View>(R.id.btn_preset_delete).setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("프리셋 삭제")
                    .setMessage("'${preset.name}' 프리셋을 삭제할까요?")
                    .setPositiveButton("삭제") { _, _ ->
                        presetManager.delete(preset.id)
                        refreshPresetList()
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }

            presetContainer.addView(card)
        }
    }

    private fun loadPresetAndStart(preset: PresetManager.Preset) {
        if (prefs.isMonitoringActive) {
            Toast.makeText(this, "이미 모니터링 중입니다. 먼저 중지해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 프리셋 값을 UI와 설정에 적용
        pickerDeadline.hour = preset.deadlineHour
        pickerDeadline.minute = preset.deadlineMinute
        prefs.setDeadlineTime(preset.deadlineHour, preset.deadlineMinute)
        updateDeadlineSummary(preset.deadlineHour, preset.deadlineMinute)

        // 소리 설정 적용
        prefs.setSoundEnabled(preset.soundEnabled)
        if (preset.soundResId != 0) {
            prefs.setSelectedSound(preset.soundResId, preset.soundTitle)
        }

        Toast.makeText(this, "'${preset.name}' 프리셋 적용 - 모니터링을 시작합니다.", Toast.LENGTH_SHORT).show()

        // 바로 모니터링 시작 (수면 소리 선택 화면으로)
        startMonitoring()
    }
}
