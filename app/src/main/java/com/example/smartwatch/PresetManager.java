package com.example.smartwatch;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 알람 프리셋 저장/로드 관리.
 * SharedPreferences에 JSON 배열로 저장한다.
 */
public class PresetManager {

    private static final String PREFS_NAME = "alarm_presets";
    private static final String KEY_PRESETS = "presets_json";

    private final SharedPreferences prefs;

    public PresetManager(Context context) {
        prefs = context.getApplicationContext()
                       .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ── 데이터 클래스 ──────────────────────────────────────────────

    public static class Preset {
        public final String id;
        public final String name;
        public final int deadlineHour;
        public final int deadlineMinute;
        public final boolean soundEnabled;
        public final int soundResId;
        public final String soundTitle;

        public Preset(String id, String name,
                      int deadlineHour, int deadlineMinute,
                      boolean soundEnabled, int soundResId, String soundTitle) {
            this.id = id;
            this.name = name;
            this.deadlineHour = deadlineHour;
            this.deadlineMinute = deadlineMinute;
            this.soundEnabled = soundEnabled;
            this.soundResId = soundResId;
            this.soundTitle = soundTitle;
        }

        public String getDeadlineText() {
            String amPm = deadlineHour < 12 ? "오전" : "오후";
            int displayHour = deadlineHour % 12 == 0 ? 12 : deadlineHour % 12;
            return amPm + " " + displayHour + ":" + String.format("%02d", deadlineMinute);
        }

        JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("name", name);
            obj.put("deadlineHour", deadlineHour);
            obj.put("deadlineMinute", deadlineMinute);
            obj.put("soundEnabled", soundEnabled);
            obj.put("soundResId", soundResId);
            obj.put("soundTitle", soundTitle);
            return obj;
        }

        static Preset fromJson(JSONObject obj) throws JSONException {
            return new Preset(
                obj.optString("id", UUID.randomUUID().toString()),
                obj.getString("name"),
                obj.getInt("deadlineHour"),
                obj.getInt("deadlineMinute"),
                obj.optBoolean("soundEnabled", true),
                obj.optInt("soundResId", 0),
                obj.optString("soundTitle", "")
            );
        }
    }

    // ── CRUD ──────────────────────────────────────────────────────

    public List<Preset> getAll() {
        String json = prefs.getString(KEY_PRESETS, "[]");
        List<Preset> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(Preset.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void save(Preset preset) {
        List<Preset> list = getAll();
        list.add(preset);
        persist(list);
    }

    public void delete(String presetId) {
        List<Preset> list = getAll();
        list.removeIf(p -> p.id.equals(presetId));
        persist(list);
    }

    public Preset createFromCurrent(String name, SleepPreferences sleepPrefs) {
        return new Preset(
            UUID.randomUUID().toString(),
            name,
            sleepPrefs.getDeadlineHour(),
            sleepPrefs.getDeadlineMinute(),
            sleepPrefs.isSoundEnabled(),
            sleepPrefs.getSoundResId(),
            sleepPrefs.getSoundTitle()
        );
    }

    private void persist(List<Preset> list) {
        JSONArray arr = new JSONArray();
        for (Preset p : list) {
            try {
                arr.put(p.toJson());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        prefs.edit().putString(KEY_PRESETS, arr.toString()).apply();
    }
}
