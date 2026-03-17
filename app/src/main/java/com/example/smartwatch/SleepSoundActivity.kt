package com.example.smartwatch

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * 수면 사운드 선택 화면.
 * 6개 사운드 중 하나를 선택하면 SleepSoundService가 반복 재생을 시작하고
 * 30분 후 자동으로 페이드아웃 종료됩니다.
 */
class SleepSoundActivity : AppCompatActivity() {

    /**
     * 사운드 항목 데이터.
     * res/raw 폴더의 파일명(확장자 제외)이 리소스 ID에 매핑됩니다.
     *
     * 파일명 규칙:
     *   rain.mp3, ocean.mp3, thunder.mp3, forest.mp3, fireplace.mp3, white_noise.mp3
     */
    private data class SoundItem(
        val viewId: Int,
        val rawResId: Int,
        val displayName: String
    )

    private val sounds = listOf(
        SoundItem(R.id.item_rain,        R.raw.rain,        "빗소리"),
        SoundItem(R.id.item_ocean,       R.raw.ocean,       "파도소리"),
        SoundItem(R.id.item_thunder,     R.raw.thunder,     "천둥소리"),
        SoundItem(R.id.item_forest,      R.raw.forest,      "숲소리"),
        SoundItem(R.id.item_fireplace,   R.raw.fireplace,   "벽난로"),
        SoundItem(R.id.item_white_noise, R.raw.white_noise, "백색소음")
    )

    private lateinit var cardNowPlaying: MaterialCardView
    private lateinit var tvNowPlayingName: TextView
    private lateinit var btnStop: MaterialButton

    private var currentlyPlaying: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sleep_sound)

        cardNowPlaying   = findViewById(R.id.card_now_playing)
        tvNowPlayingName = findViewById(R.id.tv_now_playing_name)
        btnStop          = findViewById(R.id.btn_stop)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        btnStop.setOnClickListener {
            SleepSoundService.stop(this)
            updateNowPlaying(null)
            Toast.makeText(this, "수면 사운드를 중지했습니다.", Toast.LENGTH_SHORT).show()
        }

        for (sound in sounds) {
            findViewById<View>(sound.viewId).setOnClickListener {
                playSound(sound)
            }
        }
    }

    private fun playSound(sound: SoundItem) {
        SleepSoundService.start(this, sound.rawResId, sound.displayName)
        updateNowPlaying(sound.displayName)
        Toast.makeText(this, "${sound.displayName} 재생 시작 (30분 후 자동 종료)", Toast.LENGTH_SHORT).show()
    }

    private fun updateNowPlaying(name: String?) {
        currentlyPlaying = name
        if (name != null) {
            cardNowPlaying.visibility = View.VISIBLE
            tvNowPlayingName.text = name
        } else {
            cardNowPlaying.visibility = View.GONE
        }
    }
}
