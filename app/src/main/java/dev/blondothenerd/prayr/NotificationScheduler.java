package dev.blondothenerd.prayr;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Plans global and per-item reminders without requiring exact-alarm permission. */
public final class NotificationScheduler {
    public static final String ACTION_REMINDER = "dev.blondothenerd.prayr.REMINDER";
    public static final String ACTION_CUSTOM = "dev.blondothenerd.prayr.CUSTOM_REMINDER";
    public static final String ACTION_PLAN_DAY = "dev.blondothenerd.prayr.PLAN_DAY";
    public static final String EXTRA_PRAYER_ID = "prayer_id";
    public static final String EXTRA_CUSTOM = "custom_reminder";

    private static final String PREFS = "prayr_schedule";
    private static final int REMINDER_BASE = 2100;
    private static final int DAY_PLANNER = 2199;
    private static final int CUSTOM_BASE = 10000;

    private NotificationScheduler() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void ensureScheduled(Context context) {
        scheduleToday(context, false);
        scheduleCustomPrayers(context);
        scheduleNextPlanner(context);
    }

    public static void refresh(Context context) {
        cancelGeneralReminders(context);
        cancelCustomReminders(context);
        prefs(context).edit().remove("schedule_day").remove("schedule_times").apply();
        scheduleToday(context, true);
        scheduleCustomPrayers(context);
        scheduleNextPlanner(context);
    }

    public static void scheduleToday(Context context, boolean forceNewTimes) {
        PrayerStore.resetIfNeeded(context);
        LocalDate today = LocalDate.now();
        String storedDay = prefs(context).getString("schedule_day", "");
        List<Long> times = new ArrayList<>();

        if (!forceNewTimes && today.toString().equals(storedDay)) {
            String raw = prefs(context).getString("schedule_times", "[]");
            try {
                JSONArray array = new JSONArray(raw);
                for (int i = 0; i < array.length(); i++) times.add(array.getLong(i));
            } catch (JSONException ignored) {}
        }

        boolean batch = AppSettings.allAtOnce(context);
        List<String> batchIds = batch ? batchPrayerIds(context) : Collections.emptyList();
        int quantity = batch ? batchIds.size() : AppSettings.remindersPerDay(context);
        if (times.isEmpty()) {
            if (batch) times = createBatchTimes(context, today, quantity);
            else if (AppSettings.intervalMinutes(context) == 0) times = createRandomTimes(context, today, quantity);
            else times = createIntervalTimes(context, today, quantity, AppSettings.intervalMinutes(context));
            JSONArray array = new JSONArray();
            for (Long time : times) array.put(time);
            prefs(context).edit().putString("schedule_day", today.toString()).putString("schedule_times", array.toString()).apply();
        }

        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        long now = System.currentTimeMillis();
        for (int i = 0; i < times.size() && i < AppSettings.MAX_REMINDERS; i++) {
            if (times.get(i) <= now) continue;
            Intent intent = new Intent(context, ReminderReceiver.class)
                .setAction(ACTION_REMINDER)
                .putExtra("slot", i);
            if (batch && i < batchIds.size()) intent.putExtra(EXTRA_PRAYER_ID, batchIds.get(i));
            PendingIntent pending = PendingIntent.getBroadcast(context, REMINDER_BASE + i, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, times.get(i), pending);
        }
    }

    private static List<String> batchPrayerIds(Context context) {
        List<String> result = new ArrayList<>();
        for (Prayer prayer : PrayerStore.getAll(context)) {
            if (!prayer.healed && !prayer.prayed && !prayer.noReminder && !prayer.customReminder) result.add(prayer.id);
        }
        if (result.isEmpty() && AppSettings.RESET_CYCLE.equals(AppSettings.resetMode(context))) {
            PrayerStore.nextUnprayed(context, AppSettings.selectionMode(context));
            for (Prayer prayer : PrayerStore.getAll(context)) {
                if (!prayer.healed && !prayer.prayed && !prayer.noReminder && !prayer.customReminder) result.add(prayer.id);
            }
        }
        if (AppSettings.SELECTION_RANDOM.equals(AppSettings.selectionMode(context))) Collections.shuffle(result);
        int max = Math.min(AppSettings.remindersPerDay(context), AppSettings.MAX_REMINDERS);
        if (result.size() > max) return new ArrayList<>(result.subList(0, max));
        return result;
    }

    private static List<Long> createBatchTimes(Context context, LocalDate date, int quantity) {
        List<Long> result = new ArrayList<>();
        if (quantity <= 0) return result;
        int minute = AppSettings.allAtOnceMinutes(context);
        LocalDateTime target = date.atTime(minute / 60, minute % 60);
        if (!target.isAfter(LocalDateTime.now().plusMinutes(2))) target = target.plusDays(1);
        target = moveOutOfDnd(context, target);
        long millis = toMillis(target);
        for (int i = 0; i < quantity; i++) result.add(millis + i * 250L);
        return result;
    }

    private static List<Long> createIntervalTimes(Context context, LocalDate date, int quantity, int intervalMinutes) {
        List<Long> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime target = date.atTime(7, 0);
        if (date.equals(now.toLocalDate()) && !target.isAfter(now.plusMinutes(2))) target = now.plusMinutes(3).withSecond(0).withNano(0);
        target = moveOutOfDnd(context, target);
        while (result.size() < quantity && target.toLocalDate().equals(date)) {
            result.add(toMillis(target));
            target = moveOutOfDnd(context, target.plusMinutes(intervalMinutes));
        }
        return result;
    }

    private static List<Long> createRandomTimes(Context context, LocalDate date, int quantity) {
        LocalDateTime now = LocalDateTime.now();
        int firstMinute = 7 * 60;
        if (date.equals(now.toLocalDate())) firstMinute = Math.max(firstMinute, now.getHour() * 60 + now.getMinute() + 3);
        int lastMinute = 21 * 60 + 30;
        List<Integer> candidates = new ArrayList<>();
        int rounded = ((firstMinute + 9) / 10) * 10;
        for (int minute = rounded; minute <= lastMinute; minute += 10) {
            LocalTime time = LocalTime.of(minute / 60, minute % 60);
            if (!AppSettings.isWithinDnd(context, time)) candidates.add(minute);
        }
        Collections.shuffle(candidates);

        int minimumGap = quantity > 16 ? 20 : quantity > 10 ? 30 : 45;
        List<Integer> chosen = new ArrayList<>();
        for (Integer candidate : candidates) {
            boolean separated = true;
            for (Integer existing : chosen) if (Math.abs(candidate - existing) < minimumGap) separated = false;
            if (separated) chosen.add(candidate);
            if (chosen.size() >= quantity) break;
        }
        Collections.sort(chosen);

        List<Long> result = new ArrayList<>();
        for (Integer minute : chosen) result.add(toMillis(date.atTime(minute / 60, minute % 60)));
        return result;
    }

    public static void scheduleSnooze(Context context, String prayerId) {
        LocalDateTime target = moveOutOfDnd(context, LocalDateTime.now().plusMinutes(AppSettings.snoozeMinutes(context)));
        Intent intent = new Intent(context, ReminderReceiver.class)
            .setAction(ACTION_REMINDER)
            .setData(Uri.parse("prayr://snooze/" + prayerId))
            .putExtra(EXTRA_PRAYER_ID, prayerId)
            .putExtra("snoozed", true);
        int code = 4000 + Math.abs(prayerId.hashCode() % 100000);
        PendingIntent pending = PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, toMillis(target), pending);
    }

    private static void scheduleCustomPrayers(Context context) {
        JSONArray tracked = new JSONArray();
        for (Prayer prayer : PrayerStore.getAll(context)) {
            if (!prayer.healed && !prayer.noReminder && prayer.customReminder) {
                scheduleCustomPrayer(context, prayer, LocalDateTime.now().plusSeconds(20));
                tracked.put(prayer.id);
            }
        }
        prefs(context).edit().putString("custom_ids", tracked.toString()).apply();
    }

    public static void scheduleNextCustomAfterFire(Context context, String prayerId) {
        Prayer prayer = PrayerStore.find(context, prayerId);
        if (prayer == null || prayer.healed || prayer.noReminder || !prayer.customReminder) return;
        scheduleCustomPrayer(context, prayer, LocalDateTime.now().plusMinutes(1));
    }

    private static void scheduleCustomPrayer(Context context, Prayer prayer, LocalDateTime after) {
        LocalDateTime target = nextCustomTime(prayer, after);
        target = moveOutOfDnd(context, target);
        Intent intent = new Intent(context, ReminderReceiver.class)
            .setAction(ACTION_CUSTOM)
            .setData(Uri.parse("prayr://custom/" + prayer.id))
            .putExtra(EXTRA_PRAYER_ID, prayer.id)
            .putExtra(EXTRA_CUSTOM, true);
        PendingIntent pending = PendingIntent.getBroadcast(context, customRequestCode(prayer.id), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, toMillis(target), pending);
    }

    private static LocalDateTime nextCustomTime(Prayer prayer, LocalDateTime after) {
        int hour = prayer.reminderTimeMinutes / 60;
        int minute = prayer.reminderTimeMinutes % 60;
        if (Prayer.REPEAT_HOURLY.equals(prayer.repeatMode)) {
            LocalDateTime candidate = after.withMinute(minute).withSecond(0).withNano(0);
            return candidate.isAfter(after) ? candidate : candidate.plusHours(1);
        }
        if (Prayer.REPEAT_WEEKLY.equals(prayer.repeatMode)) {
            DayOfWeek weekday = DayOfWeek.of(Math.max(1, Math.min(7, prayer.repeatWeekday)));
            LocalDate date = after.toLocalDate().with(TemporalAdjusters.nextOrSame(weekday));
            LocalDateTime candidate = date.atTime(hour, minute);
            return candidate.isAfter(after) ? candidate : candidate.plusWeeks(1);
        }
        LocalDateTime candidate = after.toLocalDate().atTime(hour, minute);
        return candidate.isAfter(after) ? candidate : candidate.plusDays(1);
    }

    private static LocalDateTime moveOutOfDnd(Context context, LocalDateTime target) {
        for (int i = 0; i < 24 * 60 && AppSettings.isWithinDnd(context, target.toLocalTime()); i++) target = target.plusMinutes(1);
        return target;
    }

    public static void scheduleNextPlanner(Context context) {
        LocalDateTime next = LocalDate.now().plusDays(1).atTime(0, 5);
        Intent intent = new Intent(context, ReminderReceiver.class).setAction(ACTION_PLAN_DAY);
        PendingIntent pending = PendingIntent.getBroadcast(context, DAY_PLANNER, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, toMillis(next), pending);
    }

    private static void cancelGeneralReminders(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        for (int i = 0; i < AppSettings.MAX_REMINDERS; i++) {
            Intent intent = new Intent(context, ReminderReceiver.class).setAction(ACTION_REMINDER);
            PendingIntent pending = PendingIntent.getBroadcast(context, REMINDER_BASE + i, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pending != null) {
                alarms.cancel(pending);
                pending.cancel();
            }
        }
    }

    private static void cancelCustomReminders(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        try {
            JSONArray array = new JSONArray(prefs(context).getString("custom_ids", "[]"));
            for (int i = 0; i < array.length(); i++) {
                String id = array.getString(i);
                Intent intent = new Intent(context, ReminderReceiver.class).setAction(ACTION_CUSTOM).setData(Uri.parse("prayr://custom/" + id));
                PendingIntent pending = PendingIntent.getBroadcast(context, customRequestCode(id), intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (pending != null) {
                    alarms.cancel(pending);
                    pending.cancel();
                }
            }
        } catch (JSONException ignored) {}
        prefs(context).edit().remove("custom_ids").apply();
    }

    private static int customRequestCode(String id) {
        return CUSTOM_BASE + (id.hashCode() & 0x0fffffff);
    }

    private static long toMillis(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
