package com.example.smartwatch

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Health Connect가 이 앱을 권한 목록에 표시하고 권한 다이얼로그를 열려면
 * androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE 인텐트를 처리하는
 * Activity가 반드시 존재해야 합니다.
 *
 * 이 화면은 "왜 수면 데이터가 필요한가"를 사용자에게 설명합니다.
 */
class HealthRationaleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_health_rationale)
        findViewById<Button>(R.id.btn_close).setOnClickListener { finish() }
    }
}
