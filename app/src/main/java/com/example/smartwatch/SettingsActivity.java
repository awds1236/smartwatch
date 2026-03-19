package com.example.smartwatch;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * 설정 화면: 테마 모드, 수면 소리 on/off, 소리 선택.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final int[][] SOUNDS = {
            {R.raw.gentle_rain, R.string.sound_gentle_rain, R.string.sound_gentle_rain_desc},
            {R.raw.rain_drops_on_window, R.string.sound_rain_drops, R.string.sound_rain_drops_desc},
            {R.raw.rainy_window_asmr, R.string.sound_rainy_window, R.string.sound_rainy_window_desc},
            {R.raw.rainy_day_in_town, R.string.sound_rainy_day, R.string.sound_rainy_day_desc},
            {R.raw.forest_sounds, R.string.sound_forest, R.string.sound_forest_desc},
            {R.raw.forest_sounds_2, R.string.sound_forest_2, R.string.sound_forest_2_desc},
    };

    private static final String[] ICONS = {
            "\uD83C\uDF27\uFE0F", "\uD83D\uDCA7", "\uD83C\uDF2C\uFE0F",
            "\uD83C\uDF06", "\uD83C\uDF32", "\uD83C\uDF3F",
    };

    private SleepPreferences prefs;
    private MaterialSwitch switchSound;
    private TextView tvSoundStatus;
    private LinearLayout soundSelectContainer;
    private View lastSelectedCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = new SleepPreferences(this);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        setupThemeSection();
        setupSoundSection();
        findViewById(R.id.btn_permission_setup).setOnClickListener(v ->
            startActivity(new Intent(this, PermissionSetupActivity.class))
        );
    }

    // ── 테마 설정 ──────────────────────────────────────────────────

    private void setupThemeSection() {
        RadioGroup rgTheme = findViewById(R.id.rg_theme);

        // 현재 저장된 테마 반영
        switch (prefs.getThemeMode()) {
            case SleepPreferences.THEME_LIGHT:
                rgTheme.check(R.id.rb_theme_light);
                break;
            case SleepPreferences.THEME_DARK:
                rgTheme.check(R.id.rb_theme_dark);
                break;
            default:
                rgTheme.check(R.id.rb_theme_system);
                break;
        }

        rgTheme.setOnCheckedChangeListener((group, checkedId) -> {
            int mode;
            if (checkedId == R.id.rb_theme_light) {
                mode = SleepPreferences.THEME_LIGHT;
            } else if (checkedId == R.id.rb_theme_dark) {
                mode = SleepPreferences.THEME_DARK;
            } else {
                mode = SleepPreferences.THEME_SYSTEM;
            }
            prefs.setThemeMode(mode);
            applyTheme(mode);
        });
    }

    /** 테마 모드를 AppCompatDelegate에 즉시 적용. */
    public static void applyTheme(int mode) {
        switch (mode) {
            case SleepPreferences.THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case SleepPreferences.THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    // ── 소리 설정 ──────────────────────────────────────────────────

    private void setupSoundSection() {
        switchSound = findViewById(R.id.switch_sound);
        tvSoundStatus = findViewById(R.id.tv_sound_status);
        soundSelectContainer = findViewById(R.id.sound_select_container);

        boolean monitoring = prefs.isMonitoringActive();
        boolean soundEnabled = prefs.isSoundEnabled();
        boolean playing = SleepSoundService.isRunning();

        switchSound.setChecked(soundEnabled && playing);
        // 모니터링 중이 아니면 스위치 비활성화
        switchSound.setEnabled(monitoring);
        updateSoundStatus(monitoring, playing);

        switchSound.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.setSoundEnabled(isChecked);
            if (!prefs.isMonitoringActive()) return;

            if (isChecked) {
                int resId = prefs.getSoundResId();
                String title = prefs.getSoundTitle();
                if (resId != 0 && !title.isEmpty()) {
                    SleepSoundService.start(this, resId, title);
                    updateSoundStatus(true, true);
                } else {
                    // 소리가 선택되지 않은 경우 — 스위치 되돌리기
                    switchSound.setChecked(false);
                    prefs.setSoundEnabled(false);
                    android.widget.Toast.makeText(this,
                            "아래에서 소리를 먼저 선택해주세요.", android.widget.Toast.LENGTH_SHORT).show();
                }
            } else {
                SleepSoundService.stop(this);
                updateSoundStatus(true, false);
            }
        });

        buildSoundList();
    }

    private void updateSoundStatus(boolean monitoring, boolean playing) {
        if (!monitoring) {
            tvSoundStatus.setText("모니터링을 시작하면 소리를 켤 수 있습니다");
        } else if (playing) {
            String title = prefs.getSoundTitle();
            tvSoundStatus.setText(title.isEmpty() ? "재생 중" : title + " 재생 중");
        } else {
            tvSoundStatus.setText("재생 중이 아닙니다");
        }
    }

    private void buildSoundList() {
        LayoutInflater inflater = LayoutInflater.from(this);
        int selectedResId = prefs.getSoundResId();

        for (int i = 0; i < SOUNDS.length; i++) {
            final int resId = SOUNDS[i][0];
            final String title = getString(SOUNDS[i][1]);
            final String desc = getString(SOUNDS[i][2]);
            final String icon = ICONS[i];

            View card = inflater.inflate(R.layout.item_sound_card, soundSelectContainer, false);

            ((TextView) card.findViewById(R.id.tv_sound_icon)).setText(icon);
            ((TextView) card.findViewById(R.id.tv_sound_title)).setText(title);
            ((TextView) card.findViewById(R.id.tv_sound_desc)).setText(desc);

            // 현재 선택된 소리 표시
            if (resId == selectedResId) {
                highlightCard(card);
                lastSelectedCard = card;
            }

            card.setOnClickListener(v -> onSoundCardClicked(v, resId, title));
            soundSelectContainer.addView(card);
        }
    }

    private void onSoundCardClicked(View card, int resId, String title) {
        // 이전 선택 해제
        if (lastSelectedCard != null) {
            unhighlightCard(lastSelectedCard);
        }
        highlightCard(card);
        lastSelectedCard = card;

        prefs.setSelectedSound(resId, title);

        // 모니터링 중이고 소리가 켜져 있으면 즉시 소리 변경
        if (prefs.isMonitoringActive() && switchSound.isChecked()) {
            SleepSoundService.start(this, resId, title);
            updateSoundStatus(true, true);
        }
    }

    private void highlightCard(View card) {
        if (card instanceof MaterialCardView) {
            ((MaterialCardView) card).setStrokeWidth(4);
            ((MaterialCardView) card).setStrokeColor(
                    com.google.android.material.color.MaterialColors.getColor(
                            card, androidx.appcompat.R.attr.colorPrimary, 0));
        }
    }

    private void unhighlightCard(View card) {
        if (card instanceof MaterialCardView) {
            ((MaterialCardView) card).setStrokeWidth(0);
        }
    }
}
