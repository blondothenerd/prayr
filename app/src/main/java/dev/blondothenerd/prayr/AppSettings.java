package dev.blondothenerd.prayr;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalTime;

/** Centralised, update-safe preferences for reminders, resets and appearance. */
public final class AppSettings {
    public static final int MAX_REMINDERS = 24;
    public static final int MAX_INTERVAL_MINUTES = 10 * 60;
    public static final String RESET_CYCLE = "cycle";
    public static final String RESET_DAILY = "daily";
    public static final String RESET_HOURLY = "hourly";
    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";
    public static final String SELECTION_RANDOM = "random";
    public static final String SELECTION_SEQUENTIAL = "sequential";
    public static final String COLOR_PURPLE = "purple";
    public static final String COLOR_BLUE = "blue";
    public static final String COLOR_TEAL = "teal";
    public static final String COLOR_GREEN = "green";
    public static final String COLOR_ORANGE = "orange";
    public static final String COLOR_ROSE = "rose";

    private static final String PREFS = "prayr_settings";

    private AppSettings() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int remindersPerDay(Context context) { return Math.max(1, Math.min(MAX_REMINDERS, prefs(context).getInt("reminders_per_day", 4))); }
    public static void setRemindersPerDay(Context context, int value) { prefs(context).edit().putInt("reminders_per_day", Math.max(1, Math.min(MAX_REMINDERS, value))).apply(); }
    public static int snoozeMinutes(Context context) { return Math.max(5, Math.min(120, prefs(context).getInt("snooze_minutes", 15))); }
    public static void setSnoozeMinutes(Context context, int value) { prefs(context).edit().putInt("snooze_minutes", Math.max(5, Math.min(120, value))).apply(); }
    public static String selectionMode(Context context) { return prefs(context).getString("selection_mode", SELECTION_RANDOM); }
    public static void setSelectionMode(Context context, String value) { prefs(context).edit().putString("selection_mode", value).apply(); }

    /** Zero means random timing; positive values are fixed 20-minute increments. */
    public static int intervalMinutes(Context context) {
        int value = prefs(context).getInt("interval_minutes", 0);
        if (value <= 0) return 0;
        return Math.max(20, Math.min(MAX_INTERVAL_MINUTES, Math.round(value / 20f) * 20));
    }
    public static void setIntervalMinutes(Context context, int value) {
        int clean = value <= 0 ? 0 : Math.max(20, Math.min(MAX_INTERVAL_MINUTES, Math.round(value / 20f) * 20));
        prefs(context).edit().putInt("interval_minutes", clean).apply();
    }
    public static boolean allAtOnce(Context context) { return prefs(context).getBoolean("all_at_once", false); }
    public static void setAllAtOnce(Context context, boolean value) { prefs(context).edit().putBoolean("all_at_once", value).apply(); }
    public static int allAtOnceMinutes(Context context) { return Math.max(0, Math.min(1439, prefs(context).getInt("all_at_once_time", 9 * 60))); }
    public static void setAllAtOnceMinutes(Context context, int value) { prefs(context).edit().putInt("all_at_once_time", Math.max(0, Math.min(1439, value))).apply(); }

    public static String resetMode(Context context) { return prefs(context).getString("reset_mode", RESET_CYCLE); }
    public static void setResetMode(Context context, String value) { prefs(context).edit().putString("reset_mode", value).apply(); }
    public static boolean dndEnabled(Context context) { return prefs(context).getBoolean("dnd_enabled", true); }
    public static void setDndEnabled(Context context, boolean value) { prefs(context).edit().putBoolean("dnd_enabled", value).apply(); }
    public static int dndStartMinutes(Context context) { return prefs(context).getInt("dnd_start", 21 * 60 + 30); }
    public static int dndEndMinutes(Context context) { return prefs(context).getInt("dnd_end", 7 * 60); }
    public static void setDndStartMinutes(Context context, int value) { prefs(context).edit().putInt("dnd_start", value).apply(); }
    public static void setDndEndMinutes(Context context, int value) { prefs(context).edit().putInt("dnd_end", value).apply(); }
    public static String theme(Context context) { return prefs(context).getString("theme", THEME_SYSTEM); }
    public static void setTheme(Context context, String value) { prefs(context).edit().putString("theme", value).apply(); }
    public static String themeColor(Context context) { return prefs(context).getString("theme_color", COLOR_PURPLE); }
    public static void setThemeColor(Context context, String value) { prefs(context).edit().putString("theme_color", value).apply(); }

    /** Driving mode changes Snooze to Mute; mute stays active until manually resumed. */
    public static boolean drivingMode(Context context) { return prefs(context).getBoolean("driving_mode", false); }
    public static void setDrivingMode(Context context, boolean value) { prefs(context).edit().putBoolean("driving_mode", value).apply(); }
    public static boolean drivingMuted(Context context) { return prefs(context).getBoolean("driving_muted", false); }
    public static void setDrivingMuted(Context context, boolean value) { prefs(context).edit().putBoolean("driving_muted", value).apply(); }

    public static boolean isDark(Context context) {
        String mode = theme(context);
        if (THEME_DARK.equals(mode)) return true;
        if (THEME_LIGHT.equals(mode)) return false;
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    public static void applyTheme(Activity activity) {
        activity.setTheme(isDark(activity) ? R.style.Theme_Prayr_Dark : R.style.Theme_Prayr_Light);
    }

    public static int primaryColor(Context context) {
        boolean dark = isDark(context);
        String value = themeColor(context);
        if (COLOR_BLUE.equals(value)) return Color.parseColor(dark ? "#75A7FF" : "#2563EB");
        if (COLOR_TEAL.equals(value)) return Color.parseColor(dark ? "#55D6C9" : "#0F8C83");
        if (COLOR_GREEN.equals(value)) return Color.parseColor(dark ? "#6ED99B" : "#23834D");
        if (COLOR_ORANGE.equals(value)) return Color.parseColor(dark ? "#FFB36B" : "#D4661B");
        if (COLOR_ROSE.equals(value)) return Color.parseColor(dark ? "#FF88B3" : "#C43F72");
        return Color.parseColor(dark ? "#9A90FF" : "#6252E8");
    }

    public static boolean isWithinDnd(Context context, LocalTime time) {
        if (!dndEnabled(context)) return false;
        int now = time.getHour() * 60 + time.getMinute();
        int start = dndStartMinutes(context);
        int end = dndEndMinutes(context);
        if (start == end) return false;
        return start < end ? now >= start && now < end : now >= start || now < end;
    }

    public static JSONObject toJson(Context context) throws JSONException {
        JSONObject object = new JSONObject();
        object.put("remindersPerDay", remindersPerDay(context));
        object.put("snoozeMinutes", snoozeMinutes(context));
        object.put("selectionMode", selectionMode(context));
        object.put("intervalMinutes", intervalMinutes(context));
        object.put("allAtOnce", allAtOnce(context));
        object.put("allAtOnceMinutes", allAtOnceMinutes(context));
        object.put("resetMode", resetMode(context));
        object.put("dndEnabled", dndEnabled(context));
        object.put("dndStartMinutes", dndStartMinutes(context));
        object.put("dndEndMinutes", dndEndMinutes(context));
        object.put("theme", theme(context));
        object.put("themeColor", themeColor(context));
        object.put("drivingMode", drivingMode(context));
        object.put("drivingMuted", drivingMuted(context));
        return object;
    }

    public static void restoreFromJson(Context context, JSONObject object) {
        if (object == null) return;
        SharedPreferences.Editor editor = prefs(context).edit();
        editor.putInt("reminders_per_day", Math.max(1, Math.min(MAX_REMINDERS, object.optInt("remindersPerDay", 4))));
        editor.putInt("snooze_minutes", Math.max(5, Math.min(120, object.optInt("snoozeMinutes", 15))));
        editor.putString("selection_mode", object.optString("selectionMode", SELECTION_RANDOM));
        int interval = object.optInt("intervalMinutes", 0);
        editor.putInt("interval_minutes", interval <= 0 ? 0 : Math.max(20, Math.min(MAX_INTERVAL_MINUTES, Math.round(interval / 20f) * 20)));
        editor.putBoolean("all_at_once", object.optBoolean("allAtOnce", false));
        editor.putInt("all_at_once_time", Math.max(0, Math.min(1439, object.optInt("allAtOnceMinutes", 9 * 60))));
        editor.putString("reset_mode", object.optString("resetMode", RESET_CYCLE));
        editor.putBoolean("dnd_enabled", object.optBoolean("dndEnabled", true));
        editor.putInt("dnd_start", object.optInt("dndStartMinutes", 21 * 60 + 30));
        editor.putInt("dnd_end", object.optInt("dndEndMinutes", 7 * 60));
        editor.putString("theme", object.optString("theme", THEME_SYSTEM));
        editor.putString("theme_color", object.optString("themeColor", COLOR_PURPLE));
        editor.putBoolean("driving_mode", object.optBoolean("drivingMode", false));
        editor.putBoolean("driving_muted", object.optBoolean("drivingMuted", false));
        editor.apply();
    }
}
