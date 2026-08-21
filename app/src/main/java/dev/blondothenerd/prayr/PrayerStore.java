package dev.blondothenerd.prayr;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** App-private on-device store. SharedPreferences survives signed in-place updates. */
public final class PrayerStore {
    private static final String PREFS = "prayr_data";
    private static final String KEY_PRAYERS = "prayers";
    private static final String KEY_RESET_BUCKET = "reset_bucket";
    private static final String KEY_CYCLES = "completed_cycles";
    private static final String KEY_CYCLE_PENDING = "cycle_pending_reset";

    private PrayerStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized List<Prayer> getAll(Context context) {
        resetIfNeeded(context);
        return load(context);
    }

    private static List<Prayer> load(Context context) {
        List<Prayer> result = new ArrayList<>();
        String raw = prefs(context).getString(KEY_PRAYERS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) result.add(Prayer.fromJson(array.getJSONObject(i)));
        } catch (JSONException ignored) {
            // Preserve usability if local data is malformed rather than crashing.
        }
        return result;
    }

    public static synchronized void save(Context context, List<Prayer> prayers) {
        JSONArray array = new JSONArray();
        for (Prayer prayer : prayers) {
            try { array.put(prayer.toJson()); } catch (JSONException ignored) {}
        }
        prefs(context).edit().putString(KEY_PRAYERS, array.toString()).apply();
    }

    public static synchronized void add(Context context, Prayer prayer) {
        List<Prayer> prayers = load(context);
        prayers.add(0, prayer);
        save(context, prayers);
        prefs(context).edit().putBoolean(KEY_CYCLE_PENDING, false).apply();
    }

    public static synchronized Prayer find(Context context, String id) {
        if (id == null) return null;
        for (Prayer prayer : load(context)) if (id.equals(prayer.id)) return prayer;
        return null;
    }

    public static synchronized void update(Context context, Prayer updated) {
        List<Prayer> prayers = load(context);
        for (int i = 0; i < prayers.size(); i++) {
            if (prayers.get(i).id.equals(updated.id)) {
                prayers.set(i, updated);
                save(context, prayers);
                return;
            }
        }
    }

    public static synchronized boolean markPrayed(Context context, String id, boolean prayed) {
        List<Prayer> prayers = load(context);
        boolean found = false;
        for (Prayer prayer : prayers) {
            if (prayer.id.equals(id) && !prayer.healed) {
                prayer.prayed = prayed;
                prayer.prayedAt = prayed ? System.currentTimeMillis() : 0L;
                found = true;
                break;
            }
        }
        if (!found) return false;

        boolean hasActive = false;
        boolean allPrayed = true;
        for (Prayer prayer : prayers) {
            if (!prayer.healed) {
                hasActive = true;
                if (!prayer.prayed) allPrayed = false;
            }
        }

        boolean completedCycle = hasActive && allPrayed;
        if (completedCycle && AppSettings.RESET_CYCLE.equals(AppSettings.resetMode(context))) {
            boolean alreadyPending = prefs(context).getBoolean(KEY_CYCLE_PENDING, false);
            SharedPreferences.Editor editor = prefs(context).edit().putBoolean(KEY_CYCLE_PENDING, true);
            if (!alreadyPending) editor.putInt(KEY_CYCLES, prefs(context).getInt(KEY_CYCLES, 0) + 1);
            editor.apply();
        } else if (!completedCycle || !AppSettings.RESET_CYCLE.equals(AppSettings.resetMode(context))) {
            prefs(context).edit().putBoolean(KEY_CYCLE_PENDING, false).apply();
        }
        save(context, prayers);
        return completedCycle;
    }

    public static synchronized void markHealed(Context context, String id, boolean healed) {
        List<Prayer> prayers = load(context);
        for (Prayer prayer : prayers) {
            if (prayer.id.equals(id)) {
                prayer.healed = healed;
                prayer.healedAt = healed ? System.currentTimeMillis() : 0L;
                prayer.prayed = false;
                break;
            }
        }
        save(context, prayers);
        prefs(context).edit().putBoolean(KEY_CYCLE_PENDING, false).apply();
    }

    public static synchronized void delete(Context context, String id) {
        List<Prayer> prayers = load(context);
        prayers.removeIf(prayer -> prayer.id.equals(id));
        save(context, prayers);
        prefs(context).edit().putBoolean(KEY_CYCLE_PENDING, false).apply();
    }

    public static synchronized void resetAllActive(Context context) {
        List<Prayer> prayers = load(context);
        for (Prayer prayer : prayers) if (!prayer.healed) prayer.prayed = false;
        save(context, prayers);
        prefs(context).edit().putBoolean(KEY_CYCLE_PENDING, false).apply();
    }

    public static synchronized void resetIfNeeded(Context context) {
        String mode = AppSettings.resetMode(context);
        if (AppSettings.RESET_CYCLE.equals(mode)) return;
        LocalDateTime now = LocalDateTime.now();
        String bucket = AppSettings.RESET_DAILY.equals(mode)
            ? LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            : now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));
        String prior = prefs(context).getString(KEY_RESET_BUCKET, "");
        if (!bucket.equals(prior)) {
            List<Prayer> prayers = load(context);
            for (Prayer prayer : prayers) if (!prayer.healed) prayer.prayed = false;
            save(context, prayers);
            prefs(context).edit().putString(KEY_RESET_BUCKET, bucket).putBoolean(KEY_CYCLE_PENDING, false).apply();
        }
    }

    public static synchronized Prayer nextUnprayed(Context context) {
        return nextUnprayed(context, AppSettings.selectionMode(context));
    }

    public static synchronized Prayer nextUnprayed(Context context, String selectionMode) {
        List<Prayer> candidates = new ArrayList<>();
        List<Prayer> prayers = getAll(context);
        for (Prayer prayer : prayers) {
            if (!prayer.healed && !prayer.prayed && !prayer.noReminder && !prayer.customReminder) candidates.add(prayer);
        }
        // A completed notification rotation begins afresh on its next alarm.
        // No-reminder and custom-timed items retain their independent tick state.
        if (candidates.isEmpty() && AppSettings.RESET_CYCLE.equals(AppSettings.resetMode(context))) {
            for (Prayer prayer : prayers) {
                if (!prayer.healed && !prayer.noReminder && !prayer.customReminder) {
                    prayer.prayed = false;
                    prayer.prayedAt = 0L;
                    candidates.add(prayer);
                }
            }
            if (!candidates.isEmpty()) {
                save(context, prayers);
                prefs(context).edit().putBoolean(KEY_CYCLE_PENDING, false).apply();
            }
        }
        if (candidates.isEmpty()) return null;
        if (AppSettings.SELECTION_RANDOM.equals(selectionMode)) Collections.shuffle(candidates);
        return candidates.get(0);
    }

    public static synchronized JSONObject toJson(Context context) throws JSONException {
        JSONObject object = new JSONObject();
        JSONArray array = new JSONArray();
        for (Prayer prayer : load(context)) array.put(prayer.toJson());
        object.put("prayers", array);
        object.put("completedCycles", prefs(context).getInt(KEY_CYCLES, 0));
        object.put("resetBucket", prefs(context).getString(KEY_RESET_BUCKET, ""));
        object.put("cyclePending", prefs(context).getBoolean(KEY_CYCLE_PENDING, false));
        return object;
    }

    public static synchronized void restoreFromJson(Context context, JSONObject object) throws JSONException {
        JSONArray array = object == null ? null : object.optJSONArray("prayers");
        if (array == null) throw new JSONException("Backup does not contain a prayer list");
        List<Prayer> prayers = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) prayers.add(Prayer.fromJson(array.getJSONObject(i)));
        save(context, prayers);
        prefs(context).edit()
            .putInt(KEY_CYCLES, object.optInt("completedCycles", 0))
            .putString(KEY_RESET_BUCKET, object.optString("resetBucket", ""))
            .putBoolean(KEY_CYCLE_PENDING, object.optBoolean("cyclePending", false))
            .apply();
    }

    public static int completedCycles(Context context) { return prefs(context).getInt(KEY_CYCLES, 0); }
}
