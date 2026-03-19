package com.example.smartwatch

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

/**
 * 앱 첫 실행 시 모든 권한을 한 화면에서 설정할 수 있는 Activity.
 * 각 권한의 상태를 실시간으로 표시하고, 개별 버튼으로 요청할 수 있다.
 */
class PermissionSetupActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PermissionSetup"
    }

    private lateinit var prefs: SleepPreferences

    // 뱃지 (상태 표시)
    private lateinit var badgeNotification: TextView
    private lateinit var badgeActivityRecognition: TextView
    private lateinit var badgeHealthConnect: TextView
    private lateinit var badgeExactAlarm: TextView
    private lateinit var badgeFullscreen: TextView

    // 버튼
    private lateinit var btnNotification: MaterialButton
    private lateinit var btnActivityRecognition: MaterialButton
    private lateinit var btnHealthConnect: MaterialButton
    private lateinit var btnExactAlarm: MaterialButton
    private lateinit var btnFullscreen: MaterialButton
    private lateinit var btnDone: MaterialButton

    // 카드
    private lateinit var cardNotification: MaterialCardView
    private lateinit var cardActivityRecognition: MaterialCardView
    private lateinit var cardExactAlarm: MaterialCardView
    private lateinit var cardFullscreen: MaterialCardView

    // ── 권한 런처 ─────────────────────────────────────────────────

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(TAG, "POST_NOTIFICATIONS granted=$granted")
        refreshAllStatus()
    }

    private val activityRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(TAG, "ACTIVITY_RECOGNITION granted=$granted")
        refreshAllStatus()
    }

    private val healthConnectLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        Log.d(TAG, "Health Connect granted=$granted")
        refreshAllStatus()
    }

    // ── Lifecycle ─────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tempPrefs = SleepPreferences(this)
        SettingsActivity.applyTheme(tempPrefs.themeMode)

        window.statusBarColor = getColor(R.color.sleep_bg_dark)
        window.navigationBarColor = getColor(R.color.sleep_bg_dark)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContentView(R.layout.activity_permission_setup)
        prefs = tempPrefs

        // 뱃지
        badgeNotification = findViewById(R.id.badge_notification)
        badgeActivityRecognition = findViewById(R.id.badge_activity_recognition)
        badgeHealthConnect = findViewById(R.id.badge_health_connect)
        badgeExactAlarm = findViewById(R.id.badge_exact_alarm)
        badgeFullscreen = findViewById(R.id.badge_fullscreen)

        // 버튼
        btnNotification = findViewById(R.id.btn_notification)
        btnActivityRecognition = findViewById(R.id.btn_activity_recognition)
        btnHealthConnect = findViewById(R.id.btn_health_connect)
        btnExactAlarm = findViewById(R.id.btn_exact_alarm)
        btnFullscreen = findViewById(R.id.btn_fullscreen)
        btnDone = findViewById(R.id.btn_done)

        // 카드
        cardNotification = findViewById(R.id.card_notification)
        cardActivityRecognition = findViewById(R.id.card_activity_recognition)
        cardExactAlarm = findViewById(R.id.card_exact_alarm)
        cardFullscreen = findViewById(R.id.card_fullscreen)

        // 버전에 따라 불필요한 카드 숨김
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            cardNotification.visibility = View.GONE
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            cardActivityRecognition.visibility = View.GONE
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            cardExactAlarm.visibility = View.GONE
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            cardFullscreen.visibility = View.GONE
        }

        // 클릭 리스너
        btnNotification.setOnClickListener { requestNotification() }
        btnActivityRecognition.setOnClickListener { requestActivityRecognition() }
        btnHealthConnect.setOnClickListener { requestHealthConnect() }
        btnExactAlarm.setOnClickListener { requestExactAlarm() }
        btnFullscreen.setOnClickListener { requestFullscreen() }

        btnDone.setOnClickListener { finishSetup() }
        findViewById<TextView>(R.id.tv_skip).setOnClickListener { finishSetup() }
    }

    override fun onResume() {
        super.onResume()
        refreshAllStatus()
    }

    // ── 상태 갱신 ─────────────────────────────────────────────────

    private fun refreshAllStatus() {
        updateNotificationStatus()
        updateActivityRecognitionStatus()
        updateHealthConnectStatus()
        updateExactAlarmStatus()
        updateFullscreenStatus()
    }

    private fun updateNotificationStatus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            setBadgeGranted(badgeNotification)
            btnNotification.isEnabled = false
            return
        }
        val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            setBadgeGranted(badgeNotification)
            btnNotification.text = "허용됨"
            btnNotification.isEnabled = false
        } else {
            setBadgeRequired(badgeNotification)
            btnNotification.text = "알림 권한 허용"
            btnNotification.isEnabled = true
        }
    }

    private fun updateActivityRecognitionStatus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            setBadgeGranted(badgeActivityRecognition)
            btnActivityRecognition.isEnabled = false
            return
        }
        val granted = checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            setBadgeGranted(badgeActivityRecognition)
            btnActivityRecognition.text = "허용됨"
            btnActivityRecognition.isEnabled = false
        } else {
            setBadgeRequired(badgeActivityRecognition)
            btnActivityRecognition.text = "활동 인식 권한 허용"
            btnActivityRecognition.isEnabled = true
        }
    }

    private fun updateHealthConnectStatus() {
        val status = HealthConnectClient.getSdkStatus(this)
        if (status != HealthConnectClient.SDK_AVAILABLE) {
            setBadgeRequired(badgeHealthConnect)
            btnHealthConnect.text = "Health Connect 설치 필요"
            btnHealthConnect.isEnabled = true
            return
        }

        lifecycleScope.launch {
            val granted = runCatching {
                HealthConnectClient.getOrCreate(this@PermissionSetupActivity)
                    .permissionController
                    .getGrantedPermissions()
            }.getOrElse { emptySet() }

            val hasAll = granted.containsAll(MainActivity.HEALTH_PERMISSIONS)
            if (hasAll) {
                setBadgeGranted(badgeHealthConnect)
                btnHealthConnect.text = "허용됨"
                btnHealthConnect.isEnabled = false
            } else {
                setBadgeRequired(badgeHealthConnect)
                btnHealthConnect.text = "Health Connect 권한 허용"
                btnHealthConnect.isEnabled = true
            }
        }
    }

    private fun updateExactAlarmStatus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            setBadgeGranted(badgeExactAlarm)
            btnExactAlarm.isEnabled = false
            return
        }
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val granted = am.canScheduleExactAlarms()
        if (granted) {
            setBadgeGranted(badgeExactAlarm)
            btnExactAlarm.text = "허용됨"
            btnExactAlarm.isEnabled = false
        } else {
            setBadgeRequired(badgeExactAlarm)
            btnExactAlarm.text = "알람 권한 설정"
            btnExactAlarm.isEnabled = true
        }
    }

    private fun updateFullscreenStatus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            setBadgeGranted(badgeFullscreen)
            btnFullscreen.isEnabled = false
            return
        }
        val nm = getSystemService(NotificationManager::class.java)
        val granted = nm.canUseFullScreenIntent()
        if (granted) {
            setBadgeGranted(badgeFullscreen)
            btnFullscreen.text = "허용됨"
            btnFullscreen.isEnabled = false
        } else {
            setBadgeRequired(badgeFullscreen)
            btnFullscreen.text = "전체 화면 권한 설정"
            btnFullscreen.isEnabled = true
        }
    }

    // ── 뱃지 헬퍼 ─────────────────────────────────────────────────

    private fun setBadgeGranted(badge: TextView) {
        badge.text = "완료"
        badge.setBackgroundResource(R.drawable.badge_background_granted)
    }

    private fun setBadgeRequired(badge: TextView) {
        badge.text = "필요"
        badge.setBackgroundResource(R.drawable.badge_background)
    }

    // ── 권한 요청 ─────────────────────────────────────────────────

    private fun requestNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestActivityRecognition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activityRecognitionLauncher.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    private fun requestHealthConnect() {
        val status = HealthConnectClient.getSdkStatus(this)
        if (status != HealthConnectClient.SDK_AVAILABLE) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=com.google.android.apps.healthdata")))
            } catch (e: ActivityNotFoundException) {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")))
            }
            return
        }
        healthConnectLauncher.launch(MainActivity.HEALTH_PERMISSIONS)
    }

    private fun requestExactAlarm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            })
        }
    }

    private fun requestFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = Uri.parse("package:$packageName")
            })
        }
    }

    // ── 완료 ──────────────────────────────────────────────────────

    private fun finishSetup() {
        prefs.setPermissionSetupDone(true)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
