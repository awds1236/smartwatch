package com.example.smartwatch;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;

/**
 * 수면 모니터링 시작 전 수면 소리를 선택하는 화면.
 * 소리를 선택하면 SleepSoundService를 시작하고 결과를 반환합니다.
 */
public class SleepSoundsActivity extends AppCompatActivity {

    public static final String EXTRA_SOUND_SELECTED = "sound_selected";

    private static final int[][] SOUNDS = {
            {R.raw.gentle_rain, R.string.sound_gentle_rain, R.string.sound_gentle_rain_desc},
            {R.raw.rain_drops_on_window, R.string.sound_rain_drops, R.string.sound_rain_drops_desc},
            {R.raw.rainy_window_asmr, R.string.sound_rainy_window, R.string.sound_rainy_window_desc},
            {R.raw.rainy_day_in_town, R.string.sound_rainy_day, R.string.sound_rainy_day_desc},
            {R.raw.forest_sounds, R.string.sound_forest, R.string.sound_forest_desc},
            {R.raw.forest_sounds_2, R.string.sound_forest_2, R.string.sound_forest_2_desc},
    };

    private static final String[] ICONS = {
            "\uD83C\uDF27\uFE0F",  // 🌧️
            "\uD83D\uDCA7",        // 💧
            "\uD83C\uDF2C\uFE0F",  // 🌬️
            "\uD83C\uDF06",        // 🌆
            "\uD83C\uDF32",        // 🌲
            "\uD83C\uDF3F",        // 🌿
    };

    private View cardNowPlaying;
    private TextView tvNowPlayingTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sleep_sounds);

        cardNowPlaying = findViewById(R.id.card_now_playing);
        tvNowPlayingTitle = findViewById(R.id.tv_now_playing_title);

        findViewById(R.id.btn_stop_sound).setOnClickListener(v -> {
            SleepSoundService.stop(this);
            cardNowPlaying.setVisibility(View.GONE);
        });

        findViewById(R.id.btn_skip_sound).setOnClickListener(v -> {
            setResult(RESULT_OK);
            finish();
        });

        LinearLayout container = findViewById(R.id.sound_list_container);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < SOUNDS.length; i++) {
            final int resId = SOUNDS[i][0];
            final String title = getString(SOUNDS[i][1]);
            final String desc = getString(SOUNDS[i][2]);
            final String icon = ICONS[i];

            View card = inflater.inflate(R.layout.item_sound_card, container, false);

            ((TextView) card.findViewById(R.id.tv_sound_icon)).setText(icon);
            ((TextView) card.findViewById(R.id.tv_sound_title)).setText(title);
            ((TextView) card.findViewById(R.id.tv_sound_desc)).setText(desc);

            card.setOnClickListener(v -> onSoundSelected(resId, title));
            container.addView(card);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNowPlayingUI();
    }

    private void onSoundSelected(int resId, String title) {
        SleepSoundService.start(this, resId, title);
        updateNowPlayingUI(title);
        setResult(RESULT_OK);
        finish();
    }

    private void updateNowPlayingUI() {
        if (SleepSoundService.isRunning()) {
            cardNowPlaying.setVisibility(View.VISIBLE);
        } else {
            cardNowPlaying.setVisibility(View.GONE);
        }
    }

    private void updateNowPlayingUI(String title) {
        cardNowPlaying.setVisibility(View.VISIBLE);
        tvNowPlayingTitle.setText(title);
    }
}
