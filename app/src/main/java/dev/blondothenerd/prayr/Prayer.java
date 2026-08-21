package dev.blondothenerd.prayr;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.UUID;

/** A single prayer or praise item and its current cycle/reminder state. */
public final class Prayer {
    public static final String TYPE_PRAYER = "prayer";
    public static final String TYPE_PRAISE = "praise";
    public static final String REPEAT_HOURLY = "hourly";
    public static final String REPEAT_DAILY = "daily";
    public static final String REPEAT_WEEKLY = "weekly";

    public String id;
    public String name;
    public String reason;
    public String specifics;
    public String type = TYPE_PRAYER;
    public boolean noReminder;
    public boolean customReminder;
    public int reminderTimeMinutes = 9 * 60;
    public String repeatMode = REPEAT_DAILY;
    public int repeatWeekday = LocalDate.now().getDayOfWeek().getValue();
    public boolean prayed;
    // Kept as "healed" internally and in JSON so older installs migrate safely.
    // The user-facing state is now called Answered everywhere.
    public boolean healed;
    public long createdAt;
    public long prayedAt;
    public long healedAt;

    public Prayer(String name, String reason, String specifics) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.reason = reason;
        this.specifics = specifics;
        this.createdAt = System.currentTimeMillis();
    }

    private Prayer() {}

    public boolean isPraise() {
        return TYPE_PRAISE.equals(type);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("name", name);
        object.put("reason", reason);
        object.put("specifics", specifics);
        object.put("type", type);
        object.put("noReminder", noReminder);
        object.put("customReminder", customReminder);
        object.put("reminderTimeMinutes", reminderTimeMinutes);
        object.put("repeatMode", repeatMode);
        object.put("repeatWeekday", repeatWeekday);
        object.put("prayed", prayed);
        object.put("healed", healed);
        object.put("createdAt", createdAt);
        object.put("prayedAt", prayedAt);
        object.put("healedAt", healedAt);
        return object;
    }

    public static Prayer fromJson(JSONObject object) throws JSONException {
        Prayer prayer = new Prayer();
        prayer.id = object.optString("id", UUID.randomUUID().toString());
        prayer.name = object.optString("name", "Someone");
        prayer.reason = object.optString("reason", "");
        prayer.specifics = object.optString("specifics", "");
        prayer.type = object.optString("type", TYPE_PRAYER);
        prayer.noReminder = object.optBoolean("noReminder", false);
        prayer.customReminder = object.optBoolean("customReminder", false);
        prayer.reminderTimeMinutes = Math.max(0, Math.min(1439, object.optInt("reminderTimeMinutes", 9 * 60)));
        prayer.repeatMode = object.optString("repeatMode", REPEAT_DAILY);
        prayer.repeatWeekday = Math.max(1, Math.min(7, object.optInt("repeatWeekday", LocalDate.now().getDayOfWeek().getValue())));
        prayer.prayed = object.optBoolean("prayed", false);
        prayer.healed = object.optBoolean("healed", false);
        prayer.createdAt = object.optLong("createdAt", System.currentTimeMillis());
        prayer.prayedAt = object.optLong("prayedAt", 0L);
        prayer.healedAt = object.optLong("healedAt", 0L);
        return prayer;
    }

    /** Shared single items are imported as a new local item, never an overwrite. */
    public Prayer importedCopy() {
        Prayer copy = new Prayer(name, reason, specifics);
        copy.type = type;
        copy.noReminder = noReminder;
        copy.customReminder = customReminder;
        copy.reminderTimeMinutes = reminderTimeMinutes;
        copy.repeatMode = repeatMode;
        copy.repeatWeekday = repeatWeekday;
        return copy;
    }
}
