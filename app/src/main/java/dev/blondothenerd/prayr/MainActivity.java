package dev.blondothenerd.prayr;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * prayr V1.2: an offline prayer/praise list with rotating reminders,
 * Android Auto spoken playback, portable .pyr files and manual Answered state.
 */
public final class MainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION = 73;
    private static final int CREATE_BACKUP = 74;
    private static final int RESTORE_BACKUP = 75;

    private FrameLayout content;
    private TextView addButton;
    private TextView prayersTab;
    private TextView settingsTab;
    private Space topSafeArea;
    private boolean showingAnswered;
    private boolean showingSettings;
    private boolean dark;

    private int background;
    private int surface;
    private int surfaceAlt;
    private int text;
    private int muted;
    private int line;
    private int primary;
    private int primarySoft;
    private int gradientEnd;
    private int success;
    private int successSoft;
    private int praise;
    private int praiseSoft;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppSettings.applyTheme(this);
        super.onCreate(savedInstanceState);
        applyPalette();
        buildShell();
        NotificationScheduler.ensureScheduled(this);
        requestNotificationPermissionIfNeeded();
        handleAppIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showPrayerScreen();
        handleAppIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        PrayerStore.resetIfNeeded(this);
    }

    private void applyPalette() {
        dark = AppSettings.isDark(this);
        background = color(dark ? "#11131A" : "#F7F7FC");
        surface = color(dark ? "#1A1D27" : "#FFFFFF");
        surfaceAlt = color(dark ? "#232735" : "#EEEFFC");
        text = color(dark ? "#F5F5FA" : "#171822");
        muted = color(dark ? "#A6A9B8" : "#6F7180");
        line = color(dark ? "#303443" : "#E5E5EF");
        primary = AppSettings.primaryColor(this);
        primarySoft = blend(primary, surface, dark ? 0.24f : 0.12f);
        gradientEnd = blend(primary, dark ? Color.BLACK : Color.WHITE, dark ? 0.70f : 0.78f);
        success = color(dark ? "#4ED8B5" : "#159B79");
        successSoft = color(dark ? "#17342E" : "#E7F8F2");
        praise = color(dark ? "#FFC75F" : "#C87512");
        praiseSoft = blend(praise, surface, dark ? 0.18f : 0.10f);

        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        int flags = window.getDecorView().getSystemUiVisibility();
        if (!dark && Build.VERSION.SDK_INT >= 23) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        else if (Build.VERSION.SDK_INT >= 23) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private void buildShell() {
        LinearLayout root = vertical();
        root.setBackgroundColor(background);

        // An explicit safe-area view makes the header reliable even on OEMs
        // that report a zero status-bar inset while enforcing edge-to-edge.
        topSafeArea = new Space(this);
        root.addView(topSafeArea, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset;
            int bottomInset;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets topBars = insets.getInsets(WindowInsets.Type.statusBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets bottomBars = insets.getInsets(WindowInsets.Type.navigationBars());
                topInset = topBars.top;
                bottomInset = bottomBars.bottom;
            } else {
                topInset = insets.getSystemWindowInsetTop();
                bottomInset = insets.getSystemWindowInsetBottom();
            }
            int safeTop = Math.max(dp(44), topInset + dp(10));
            ViewGroup.LayoutParams topParams = topSafeArea.getLayoutParams();
            if (topParams.height != safeTop) {
                topParams.height = safeTop;
                topSafeArea.setLayoutParams(topParams);
            }
            view.setPadding(0, 0, 0, Math.max(bottomInset, systemBarHeight("navigation_bar_height", 0)));
            return insets;
        });

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(22), dp(4), dp(16), dp(8));
        toolbar.setBackgroundColor(background);
        root.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));

        TextView logo = makeText("prayr", 30, text, true);
        logo.setLetterSpacing(-0.03f);
        toolbar.addView(logo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        addButton = makeText("+", 30, Color.WHITE, false);
        addButton.setGravity(Gravity.CENTER);
        addButton.setBackground(round(primary, 18, 0, 0));
        addButton.setContentDescription("Add prayer or praise");
        addButton.setOnClickListener(v -> showPrayerEditor(null));
        toolbar.addView(addButton, new LinearLayout.LayoutParams(dp(46), dp(46)));

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(12), dp(6), dp(12), dp(8));
        bottom.setBackground(round(surface, 0, line, 1));
        root.addView(bottom, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76)));

        prayersTab = navItem("♡\nPrayers");
        settingsTab = navItem("⚙\nSettings");
        prayersTab.setOnClickListener(v -> showPrayerScreen());
        settingsTab.setOnClickListener(v -> showSettingsScreen());
        bottom.addView(prayersTab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        bottom.addView(settingsTab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        setContentView(root);
        root.requestApplyInsets();
        showPrayerScreen();
    }

    private TextView navItem(String value) {
        TextView view = makeText(value, 13, muted, true);
        view.setGravity(Gravity.CENTER);
        view.setLineSpacing(0, 0.92f);
        view.setBackground(round(Color.TRANSPARENT, 16, 0, 0));
        return view;
    }

    private void updateTabs() {
        prayersTab.setTextColor(showingSettings ? muted : primary);
        settingsTab.setTextColor(showingSettings ? primary : muted);
        prayersTab.setBackground(round(showingSettings ? Color.TRANSPARENT : primarySoft, 16, 0, 0));
        settingsTab.setBackground(round(showingSettings ? primarySoft : Color.TRANSPARENT, 16, 0, 0));
    }

    private void showPrayerScreen() {
        showingSettings = false;
        updateTabs();
        content.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = vertical();
        page.setPadding(dp(18), dp(8), dp(18), dp(20));
        scroll.addView(page, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(scroll);

        List<Prayer> all = PrayerStore.getAll(this);
        List<Prayer> active = new ArrayList<>();
        List<Prayer> answered = new ArrayList<>();
        int prayedCount = 0;
        for (Prayer prayer : all) {
            if (prayer.healed) answered.add(prayer);
            else {
                active.add(prayer);
                if (prayer.prayed) prayedCount++;
            }
        }

        page.addView(buildProgressCard(active.size(), prayedCount), marginParams(-1, -2, 0, 0, 0, 16));
        page.addView(buildFilterRow(active.size(), answered.size()), marginParams(-1, dp(46), 0, 0, 0, 18));

        List<Prayer> visible = showingAnswered ? answered : active;
        if (visible.isEmpty()) page.addView(buildEmptyState(showingAnswered), marginParams(-1, -2, 0, 18, 0, 0));
        else for (Prayer prayer : visible) page.addView(buildPrayerCard(prayer), marginParams(-1, -2, 0, 0, 0, 12));
    }

    private View buildProgressCard(int activeCount, int prayedCount) {
        LinearLayout card = vertical();
        card.setPadding(dp(20), dp(19), dp(20), dp(18));
        GradientDrawable backgroundDrawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{primary, gradientEnd});
        backgroundDrawable.setCornerRadius(dp(24));
        card.setBackground(backgroundDrawable);

        TextView eyebrow = makeText("YOUR PRAYER RHYTHM", 11, Color.WHITE, true);
        eyebrow.setAlpha(0.88f);
        eyebrow.setLetterSpacing(0.12f);
        card.addView(eyebrow);

        String headline = activeCount == 0 ? "Begin with someone on your heart" : prayedCount + " of " + activeCount + " prayed for";
        card.addView(makeText(headline, 23, Color.WHITE, true), marginParams(-1, -2, 0, 7, 0, 0));

        String mode = AppSettings.resetMode(this);
        String rhythm;
        if (AppSettings.RESET_CYCLE.equals(mode) && activeCount > 0 && prayedCount == activeCount) rhythm = "Cycle complete • A fresh round starts next reminder";
        else rhythm = AppSettings.RESET_CYCLE.equals(mode) ? "Resets after everyone has been prayed for" : AppSettings.RESET_DAILY.equals(mode) ? "Resets each day" : "Resets each hour";
        TextView rhythmView = makeText(rhythm, 13, Color.WHITE, false);
        rhythmView.setAlpha(0.88f);
        card.addView(rhythmView, marginParams(-1, -2, 0, 0, 0, 13));

        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(Math.max(activeCount, 1));
        progress.setProgress(prayedCount);
        progress.setProgressTintList(ColorStateList.valueOf(Color.WHITE));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(blend(Color.BLACK, primary, 0.18f)));
        card.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(7)));
        return card;
    }

    private View buildFilterRow(int activeCount, int answeredCount) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView active = filterPill("Prayers  " + activeCount, !showingAnswered);
        TextView answered = filterPill("Answered  " + answeredCount, showingAnswered);
        active.setOnClickListener(v -> { showingAnswered = false; showPrayerScreen(); });
        answered.setOnClickListener(v -> { showingAnswered = true; showPrayerScreen(); });
        row.addView(active, marginParams(-2, -1, 0, 0, 8, 0));
        row.addView(answered, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return row;
    }

    private TextView filterPill(String value, boolean selected) {
        TextView pill = makeText(value, 13, selected ? primary : muted, true);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(18), 0, dp(18), 0);
        pill.setBackground(round(selected ? primarySoft : surface, 20, selected ? primary : line, selected ? 2 : 1));
        return pill;
    }

    private View buildEmptyState(boolean answered) {
        LinearLayout empty = vertical();
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(30), dp(48), dp(30), dp(48));
        empty.setBackground(round(surface, 24, line, 1));
        empty.addView(makeText(answered ? "✦" : "♡", 44, answered ? success : primary, false));
        empty.addView(makeText(answered ? "Answers will live here" : "Your prayer list is ready", 19, text, true), marginParams(-2, -2, 0, 12, 0, 6));
        empty.addView(makeText(answered ? "When an answer arrives, mark the item as Answered." : "Tap + to add the first prayer or praise on your heart.", 14, muted, false), marginParams(-1, -2, 0, 0, 0, 18));
        if (!answered) {
            Button add = actionButton("Add an item", true);
            add.setOnClickListener(v -> showPrayerEditor(null));
            empty.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(46)));
        }
        return empty;
    }

    private View buildPrayerCard(Prayer prayerItem) {
        LinearLayout card = vertical();
        card.setPadding(dp(17), dp(16), dp(14), dp(12));
        int accent = prayerItem.isPraise() ? praise : primary;
        int fill = prayerItem.healed || prayerItem.prayed ? successSoft : prayerItem.isPraise() ? praiseSoft : surface;
        int stroke = prayerItem.healed || prayerItem.prayed ? success : prayerItem.isPraise() ? praise : line;
        int strokeWidth = prayerItem.healed || prayerItem.prayed || prayerItem.isPraise() ? 2 : 1;
        card.setBackground(round(fill, 21, stroke, strokeWidth));
        card.setOnClickListener(v -> showPrayerDetails(prayerItem));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout copy = vertical();
        TextView name = makeText(prayerItem.name, 19, text, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(name);
        String reason = prayerItem.reason.trim().isEmpty() ? "A quiet intention" : prayerItem.reason;
        TextView reasonView = makeText(reason, 14, muted, false);
        reasonView.setMaxLines(2);
        reasonView.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(reasonView, marginParams(-1, -2, 0, 4, 0, 0));
        top.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        int checkColor = prayerItem.healed || prayerItem.prayed ? success : accent;
        String symbol = prayerItem.healed ? "✦" : prayerItem.prayed ? "✓" : "○";
        TextView check = makeText(symbol, prayerItem.prayed || prayerItem.healed ? 23 : 30, prayerItem.healed || prayerItem.prayed ? Color.WHITE : checkColor, true);
        check.setGravity(Gravity.CENTER);
        check.setContentDescription(prayerItem.healed ? "Answered" : prayerItem.prayed ? "Mark as not prayed" : "Mark as prayed");
        check.setBackground(round(prayerItem.healed || prayerItem.prayed ? success : blend(accent, surface, 0.10f), 23, checkColor, 3));
        if (!prayerItem.healed) {
            check.setOnClickListener(v -> {
                boolean completed = PrayerStore.markPrayed(this, prayerItem.id, !prayerItem.prayed);
                if (completed && AppSettings.RESET_CYCLE.equals(AppSettings.resetMode(this))) Toast.makeText(this, "Cycle complete — everyone has been prayed for 🙏", Toast.LENGTH_LONG).show();
                showPrayerScreen();
            });
        }
        top.addView(check, marginParams(dp(46), dp(46), 12, 0, 0, 0));

        if (!prayerItem.specifics.trim().isEmpty()) {
            TextView specifics = makeText(prayerItem.specifics, 14, text, false);
            specifics.setMaxLines(3);
            specifics.setEllipsize(TextUtils.TruncateAt.END);
            specifics.setLineSpacing(dp(2), 1f);
            card.addView(specifics, marginParams(-1, -2, 0, 14, 0, 12));
        }

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        String prefix = prayerItem.isPraise() ? "PRAISE  •  " : "";
        String status = prayerItem.healed ? "ANSWERED  •  " + date(prayerItem.healedAt) : prayerItem.prayed ? "PRAYED THIS ROUND  ✓" : "WAITING THIS ROUND";
        String schedule = !prayerItem.healed && !prayerItem.prayed ? reminderLabel(prayerItem) : "";
        TextView state = makeText(prefix + status + schedule, 11, prayerItem.healed || prayerItem.prayed ? success : accent, true);
        state.setLetterSpacing(0.05f);
        state.setMaxLines(2);
        footer.addView(state, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView menu = makeText("⋯", 26, muted, true);
        menu.setGravity(Gravity.CENTER);
        menu.setOnClickListener(v -> showPrayerMenu(menu, prayerItem));
        footer.addView(menu, new LinearLayout.LayoutParams(dp(46), dp(40)));
        card.addView(footer, marginParams(-1, dp(40), 0, 4, 0, -4));
        return card;
    }

    private String reminderLabel(Prayer prayerItem) {
        if (prayerItem.noReminder) return "\nNO REMINDER";
        if (!prayerItem.customReminder) return "";
        String repeat = Prayer.REPEAT_HOURLY.equals(prayerItem.repeatMode) ? "HOURLY" : Prayer.REPEAT_WEEKLY.equals(prayerItem.repeatMode) ? "WEEKLY" : "DAILY";
        return "\n" + repeat + "  •  " + clock(prayerItem.reminderTimeMinutes);
    }

    private void showPrayerMenu(View anchor, Prayer prayerItem) {
        PopupMenu menu = new PopupMenu(this, anchor);
        if (prayerItem.healed) {
            menu.getMenu().add(0, 5, 0, "Share .pyr");
            menu.getMenu().add(0, 3, 1, "Return to prayer list");
            menu.getMenu().add(0, 4, 2, "Delete");
        } else {
            menu.getMenu().add(0, 1, 0, "Edit");
            menu.getMenu().add(0, 5, 1, "Share .pyr");
            menu.getMenu().add(0, 2, 2, "Mark as Answered");
            menu.getMenu().add(0, 4, 3, "Delete");
        }
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) showPrayerEditor(prayerItem);
            if (item.getItemId() == 2) confirmAnswered(prayerItem);
            if (item.getItemId() == 3) {
                PrayerStore.markHealed(this, prayerItem.id, false);
                showingAnswered = false;
                NotificationScheduler.refresh(this);
                showPrayerScreen();
            }
            if (item.getItemId() == 4) confirmDelete(prayerItem);
            if (item.getItemId() == 5) sharePrayer(prayerItem);
            return true;
        });
        menu.show();
    }

    private void confirmAnswered(Prayer prayerItem) {
        new AlertDialog.Builder(this)
            .setTitle("Mark as Answered?")
            .setMessage(prayerItem.name + " will leave the active rotation and move to Answered. This can be undone later.")
            .setNegativeButton("Not yet", null)
            .setPositiveButton("Mark Answered", (dialog, which) -> {
                PrayerStore.markHealed(this, prayerItem.id, true);
                NotificationScheduler.refresh(this);
                Toast.makeText(this, "Added to Answered ✦", Toast.LENGTH_LONG).show();
                showPrayerScreen();
            }).show();
    }

    private void confirmDelete(Prayer prayerItem) {
        new AlertDialog.Builder(this)
            .setTitle("Delete this item?")
            .setMessage("This permanently removes “" + prayerItem.name + "” from prayr.")
            .setNegativeButton("Keep", null)
            .setPositiveButton("Delete", (dialog, which) -> {
                PrayerStore.delete(this, prayerItem.id);
                NotificationScheduler.refresh(this);
                showPrayerScreen();
            }).show();
    }

    private void showPrayerEditor(Prayer existing) {
        LinearLayout form = vertical();
        form.setPadding(dp(22), dp(8), dp(22), dp(10));
        EditText name = formField(form, "PRAY FOR", "Name or situation", existing == null ? "" : existing.name, 1);
        EditText reason = formField(form, "REASON", "Short note shown in reminders", existing == null ? "" : existing.reason, 2);
        EditText specifics = formField(form, "SPECIFICS", "Anything you want to remember while praying…", existing == null ? "" : existing.specifics, 5);

        form.addView(sectionLabel("ITEM TYPE"), marginParams(-1, -2, 0, 2, 0, 4));
        RadioGroup typeGroup = new RadioGroup(this);
        typeGroup.setOrientation(RadioGroup.VERTICAL);
        RadioButton prayerType = radio("Prayer", Prayer.TYPE_PRAYER);
        RadioButton praiseType = radio("Praise", Prayer.TYPE_PRAISE);
        typeGroup.addView(prayerType);
        typeGroup.addView(praiseType);
        if (existing != null && existing.isPraise()) praiseType.setChecked(true); else prayerType.setChecked(true);
        form.addView(typeGroup, marginParams(-1, -2, 0, 0, 0, 12));

        CheckBox noReminder = new CheckBox(this);
        noReminder.setText("No reminder");
        noReminder.setTextColor(text);
        noReminder.setTextSize(15);
        noReminder.setButtonTintList(checkTint());
        noReminder.setChecked(existing != null && existing.noReminder);
        form.addView(noReminder, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        Switch custom = new Switch(this);
        custom.setText("Use a specific reminder");
        custom.setTextColor(text);
        custom.setTextSize(15);
        styleSwitch(custom);
        custom.setChecked(existing != null && existing.customReminder && !existing.noReminder);
        form.addView(custom, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        LinearLayout customArea = vertical();
        int[] selectedTime = new int[]{existing == null ? 9 * 60 : existing.reminderTimeMinutes};
        Button time = timeButton("Time  " + clock(selectedTime[0]));
        customArea.addView(time, marginParams(-1, dp(48), 0, 4, 0, 8));
        customArea.addView(makeText("Repeat", 12, muted, true));
        RadioGroup repeatGroup = new RadioGroup(this);
        RadioButton hourly = radio("Hourly at this minute", Prayer.REPEAT_HOURLY);
        RadioButton daily = radio("Daily", Prayer.REPEAT_DAILY);
        RadioButton weekly = radio("Weekly", Prayer.REPEAT_WEEKLY);
        repeatGroup.addView(hourly); repeatGroup.addView(daily); repeatGroup.addView(weekly);
        String currentRepeat = existing == null ? Prayer.REPEAT_DAILY : existing.repeatMode;
        if (Prayer.REPEAT_HOURLY.equals(currentRepeat)) hourly.setChecked(true);
        else if (Prayer.REPEAT_WEEKLY.equals(currentRepeat)) weekly.setChecked(true);
        else daily.setChecked(true);
        customArea.addView(repeatGroup);
        form.addView(customArea, marginParams(-1, -2, 0, 0, 0, 8));

        setSubtreeEnabled(customArea, custom.isChecked());
        customArea.setAlpha(custom.isChecked() ? 1f : 0.38f);
        noReminder.setOnCheckedChangeListener((button, checked) -> {
            custom.setEnabled(!checked);
            if (checked) custom.setChecked(false);
        });
        custom.setEnabled(!noReminder.isChecked());
        custom.setOnCheckedChangeListener((button, checked) -> {
            setSubtreeEnabled(customArea, checked);
            customArea.setAlpha(checked ? 1f : 0.38f);
        });
        time.setOnClickListener(v -> pickTime(selectedTime[0], minutes -> {
            selectedTime[0] = minutes;
            time.setText("Time  " + clock(minutes));
        }));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(existing == null ? "Add prayer or praise" : "Edit item")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(existing == null ? "Add" : "Save", null)
            .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String enteredName = name.getText().toString().trim();
            if (enteredName.isEmpty()) {
                name.setError("Who or what should you pray for?");
                return;
            }
            Prayer target = existing == null ? new Prayer(enteredName, reason.getText().toString().trim(), specifics.getText().toString().trim()) : existing;
            target.name = enteredName;
            target.reason = reason.getText().toString().trim();
            target.specifics = specifics.getText().toString().trim();
            RadioButton selectedType = typeGroup.findViewById(typeGroup.getCheckedRadioButtonId());
            target.type = selectedType == null ? Prayer.TYPE_PRAYER : String.valueOf(selectedType.getTag());
            target.noReminder = noReminder.isChecked();
            target.customReminder = custom.isChecked() && !target.noReminder;
            target.reminderTimeMinutes = selectedTime[0];
            RadioButton selectedRepeat = repeatGroup.findViewById(repeatGroup.getCheckedRadioButtonId());
            target.repeatMode = selectedRepeat == null ? Prayer.REPEAT_DAILY : String.valueOf(selectedRepeat.getTag());
            if (existing == null) target.repeatWeekday = LocalDate.now().getDayOfWeek().getValue();
            if (existing == null) PrayerStore.add(this, target); else PrayerStore.update(this, target);
            NotificationScheduler.refresh(this);
            dialog.dismiss();
            showingAnswered = false;
            showPrayerScreen();
        }));
        dialog.show();
    }

    private EditText formField(LinearLayout form, String label, String hint, String value, int lines) {
        form.addView(sectionLabel(label), marginParams(-1, -2, 0, 14, 0, 6));
        EditText input = new EditText(this);
        input.setText(value);
        input.setHint(hint);
        input.setTextColor(text);
        input.setHintTextColor(muted);
        input.setTextSize(15);
        input.setGravity(lines == 1 ? Gravity.CENTER_VERTICAL : Gravity.TOP);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setMinLines(lines);
        input.setMaxLines(lines);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | (lines > 1 ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        input.setBackground(round(surfaceAlt, 14, line, 1));
        form.addView(input, marginParams(-1, -2, 0, 0, 0, 18));
        return input;
    }

    private TextView sectionLabel(String value) {
        TextView label = makeText(value, 11, primary, true);
        label.setLetterSpacing(0.1f);
        return label;
    }

    private void showPrayerDetails(Prayer prayerItem) {
        LinearLayout body = vertical();
        body.setPadding(dp(24), dp(4), dp(24), dp(4));
        String kind = prayerItem.isPraise() ? "PRAISE" : "PRAYER";
        body.addView(makeText(kind, 11, prayerItem.isPraise() ? praise : primary, true));
        body.addView(makeText(prayerItem.reason.trim().isEmpty() ? "A quiet intention" : prayerItem.reason, 16, primary, true), marginParams(-1, -2, 0, 8, 0, 0));
        if (!prayerItem.specifics.trim().isEmpty()) {
            TextView specifics = makeText(prayerItem.specifics, 15, text, false);
            specifics.setLineSpacing(dp(3), 1f);
            body.addView(specifics, marginParams(-1, -2, 0, 16, 0, 0));
        }
        body.addView(makeText(reminderDescription(prayerItem), 13, muted, false), marginParams(-1, -2, 0, 14, 0, 0));
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle((prayerItem.isPraise() ? "Praise for " : "Pray for ") + prayerItem.name).setView(body).setNegativeButton("Close", null);
        if (!prayerItem.healed) {
            builder.setNeutralButton("Edit", (dialog, which) -> showPrayerEditor(prayerItem));
            builder.setPositiveButton(prayerItem.prayed ? "Pray again" : "Done", (dialog, which) -> {
                PrayerStore.markPrayed(this, prayerItem.id, true);
                showPrayerScreen();
            });
        }
        builder.show();
    }

    private String reminderDescription(Prayer prayerItem) {
        if (prayerItem.healed) return "Answered " + date(prayerItem.healedAt);
        if (prayerItem.noReminder) return "No reminder";
        if (!prayerItem.customReminder) return "Uses the main reminder rhythm";
        String repeat = Prayer.REPEAT_HOURLY.equals(prayerItem.repeatMode) ? "Hourly" : Prayer.REPEAT_WEEKLY.equals(prayerItem.repeatMode) ? "Weekly" : "Daily";
        return repeat + " reminder at " + clock(prayerItem.reminderTimeMinutes);
    }

    private void showSettingsScreen() {
        showingSettings = true;
        updateTabs();
        content.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = vertical();
        page.setPadding(dp(18), dp(8), dp(18), dp(24));
        scroll.addView(page);
        content.addView(scroll);

        page.addView(makeText("Settings", 26, text, true));
        page.addView(makeText("Shape a rhythm that feels gentle, not noisy.", 14, muted, false), marginParams(-1, -2, 0, 4, 0, 22));
        page.addView(buildReminderSettings(), marginParams(-1, -2, 0, 0, 0, 14));
        page.addView(buildDndSettings(), marginParams(-1, -2, 0, 0, 0, 14));
        page.addView(buildDrivingSettings(), marginParams(-1, -2, 0, 0, 0, 14));
        page.addView(buildResetSettings(), marginParams(-1, -2, 0, 0, 0, 14));
        page.addView(buildAppearanceSettings(), marginParams(-1, -2, 0, 0, 0, 14));
        page.addView(buildDataSettings(), marginParams(-1, -2, 0, 0, 0, 14));
        page.addView(buildNotificationSettings(), marginParams(-1, -2, 0, 0, 0, 14));

        TextView version = makeText("prayr  •  V1.2.0 (build 5)  •  Version 1 of 3\nPrivate and offline — app data survives signed updates.", 12, muted, false);
        version.setGravity(Gravity.CENTER);
        page.addView(version, marginParams(-1, -2, 18, 12, 18, 10));
    }

    private View buildReminderSettings() {
        LinearLayout card = settingsCard("Reminder controls", "Choose the order, timing and daily amount");

        TextView quantity = makeText(reminderQuantityText(AppSettings.remindersPerDay(this)), 15, text, true);
        card.addView(quantity, marginParams(-1, -2, 0, 4, 0, 2));
        SeekBar reminderSlider = styledSeekBar(AppSettings.MAX_REMINDERS - 1, AppSettings.remindersPerDay(this) - 1);
        card.addView(reminderSlider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        card.addView(rangeLabels("1", String.valueOf(AppSettings.MAX_REMINDERS)), marginParams(-1, -2, 8, -5, 8, 12));
        reminderSlider.setOnSeekBarChangeListener(seekListener(progress -> {
            int value = progress + 1;
            quantity.setText(reminderQuantityText(value));
            AppSettings.setRemindersPerDay(this, value);
            NotificationScheduler.refresh(this);
        }));

        card.addView(makeText("Choose items", 14, text, true), marginParams(-1, -2, 0, 4, 0, 2));
        RadioGroup selection = new RadioGroup(this);
        RadioButton random = radio("Random choice", AppSettings.SELECTION_RANDOM);
        RadioButton sequential = radio("Sequential down the list", AppSettings.SELECTION_SEQUENTIAL);
        selection.addView(random); selection.addView(sequential);
        if (AppSettings.SELECTION_SEQUENTIAL.equals(AppSettings.selectionMode(this))) sequential.setChecked(true); else random.setChecked(true);
        selection.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton chosen = group.findViewById(checkedId);
            if (chosen != null) {
                AppSettings.setSelectionMode(this, String.valueOf(chosen.getTag()));
                NotificationScheduler.refresh(this);
            }
        });
        card.addView(selection, marginParams(-1, -2, 0, 0, 0, 10));

        LinearLayout frequencyArea = vertical();
        TextView frequency = makeText(intervalText(AppSettings.intervalMinutes(this)), 15, text, true);
        frequencyArea.addView(frequency, marginParams(-1, -2, 0, 0, 0, 2));
        SeekBar interval = styledSeekBar(AppSettings.MAX_INTERVAL_MINUTES / 20, AppSettings.intervalMinutes(this) / 20);
        frequencyArea.addView(interval, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        frequencyArea.addView(rangeLabels("Random", "10 hours"), marginParams(-1, -2, 8, -5, 8, 8));
        interval.setOnSeekBarChangeListener(seekListener(progress -> {
            int minutes = progress * 20;
            frequency.setText(intervalText(minutes));
            AppSettings.setIntervalMinutes(this, minutes);
            NotificationScheduler.refresh(this);
        }));
        card.addView(frequencyArea);

        Switch batch = new Switch(this);
        batch.setText("Deliver selected items together");
        batch.setTextColor(text);
        batch.setTextSize(15);
        styleSwitch(batch);
        batch.setChecked(AppSettings.allAtOnce(this));
        card.addView(batch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        Button batchTime = timeButton("Together at  " + clock(AppSettings.allAtOnceMinutes(this)));
        card.addView(batchTime, marginParams(-1, dp(48), 0, 0, 0, 14));
        setSubtreeEnabled(frequencyArea, !batch.isChecked());
        frequencyArea.setAlpha(batch.isChecked() ? 0.35f : 1f);
        batchTime.setEnabled(batch.isChecked());
        batchTime.setAlpha(batch.isChecked() ? 1f : 0.38f);
        batch.setOnCheckedChangeListener((button, checked) -> {
            AppSettings.setAllAtOnce(this, checked);
            setSubtreeEnabled(frequencyArea, !checked);
            frequencyArea.setAlpha(checked ? 0.35f : 1f);
            batchTime.setEnabled(checked);
            batchTime.setAlpha(checked ? 1f : 0.38f);
            NotificationScheduler.refresh(this);
        });
        batchTime.setOnClickListener(v -> pickTime(AppSettings.allAtOnceMinutes(this), minutes -> {
            AppSettings.setAllAtOnceMinutes(this, minutes);
            batchTime.setText("Together at  " + clock(minutes));
            NotificationScheduler.refresh(this);
        }));

        TextView snooze = makeText("Snooze for " + AppSettings.snoozeMinutes(this) + " minutes", 15, text, true);
        card.addView(snooze, marginParams(-1, -2, 0, 2, 0, 2));
        SeekBar snoozeSlider = styledSeekBar(23, AppSettings.snoozeMinutes(this) / 5 - 1);
        card.addView(snoozeSlider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        snoozeSlider.setOnSeekBarChangeListener(seekListener(progress -> {
            int minutes = (progress + 1) * 5;
            snooze.setText("Snooze for " + minutes + " minutes");
            AppSettings.setSnoozeMinutes(this, minutes);
        }));
        return card;
    }

    private View buildDndSettings() {
        LinearLayout card = settingsCard("Do not disturb", "Reminders wait quietly during these hours");
        Switch enabled = new Switch(this);
        enabled.setText("Quiet hours enabled");
        enabled.setTextColor(text);
        enabled.setTextSize(15);
        styleSwitch(enabled);
        enabled.setChecked(AppSettings.dndEnabled(this));
        card.addView(enabled, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        Button start = timeButton("From  " + clock(AppSettings.dndStartMinutes(this)));
        Button end = timeButton("Until  " + clock(AppSettings.dndEndMinutes(this)));
        times.addView(start, marginParams(0, dp(48), 0, 0, 8, 0, 1f));
        times.addView(end, new LinearLayout.LayoutParams(0, dp(48), 1f));
        card.addView(times, marginParams(-1, dp(48), 0, 6, 0, 0));
        setSubtreeEnabled(times, enabled.isChecked());
        times.setAlpha(enabled.isChecked() ? 1f : 0.42f);

        enabled.setOnCheckedChangeListener((button, checked) -> {
            AppSettings.setDndEnabled(this, checked);
            setSubtreeEnabled(times, checked);
            times.setAlpha(checked ? 1f : 0.42f);
            NotificationScheduler.refresh(this);
        });
        start.setOnClickListener(v -> pickTime(AppSettings.dndStartMinutes(this), minutes -> {
            AppSettings.setDndStartMinutes(this, minutes);
            start.setText("From  " + clock(minutes));
            NotificationScheduler.refresh(this);
        }));
        end.setOnClickListener(v -> pickTime(AppSettings.dndEndMinutes(this), minutes -> {
            AppSettings.setDndEndMinutes(this, minutes);
            end.setText("Until  " + clock(minutes));
            NotificationScheduler.refresh(this);
        }));
        return card;
    }

    private View buildDrivingSettings() {
        LinearLayout card = settingsCard("Driving / Android Auto", "Browse and hear your prayer list safely in the car");
        Switch mode = new Switch(this);
        mode.setText("Use Mute instead of Snooze");
        mode.setTextColor(text);
        mode.setTextSize(15);
        styleSwitch(mode);
        mode.setChecked(AppSettings.drivingMode(this));
        card.addView(mode, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        TextView note = makeText("Open prayr under Media in Android Auto, choose an item, and its name and reason are read aloud. Finishing the reading ticks it as prayed. Auto also receives Prayed and Mute playback controls when space permits; Mute silences future reminders until resumed here. Private sideloads require Android Auto developer mode and Unknown sources.", 13, muted, false);
        note.setLineSpacing(dp(2), 1f);
        card.addView(note, marginParams(-1, -2, 0, 2, 0, 10));
        mode.setOnCheckedChangeListener((button, checked) -> AppSettings.setDrivingMode(this, checked));
        if (AppSettings.drivingMuted(this)) {
            TextView status = makeText("REMINDERS MUTED", 12, praise, true);
            card.addView(status, marginParams(-1, -2, 0, 4, 0, 8));
            Button resume = actionButton("Resume reminders", true);
            resume.setOnClickListener(v -> {
                AppSettings.setDrivingMuted(this, false);
                NotificationScheduler.refresh(this);
                Toast.makeText(this, "Reminders resumed", Toast.LENGTH_SHORT).show();
                showSettingsScreen();
            });
            card.addView(resume, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        }
        return card;
    }

    private View buildResetSettings() {
        LinearLayout card = settingsCard("Reset prayed ticks", "Answered items never reset; only temporary ticks do");
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        RadioButton cycle = radio("After every full cycle", AppSettings.RESET_CYCLE);
        RadioButton daily = radio("Daily", AppSettings.RESET_DAILY);
        RadioButton hourly = radio("Hourly", AppSettings.RESET_HOURLY);
        group.addView(cycle); group.addView(daily); group.addView(hourly);
        String current = AppSettings.resetMode(this);
        if (AppSettings.RESET_CYCLE.equals(current)) cycle.setChecked(true);
        else if (AppSettings.RESET_DAILY.equals(current)) daily.setChecked(true);
        else hourly.setChecked(true);
        group.setOnCheckedChangeListener((radioGroup, checkedId) -> {
            RadioButton selected = radioGroup.findViewById(checkedId);
            if (selected == null) return;
            AppSettings.setResetMode(this, String.valueOf(selected.getTag()));
            PrayerStore.resetAllActive(this);
            NotificationScheduler.refresh(this);
        });
        card.addView(group);
        return card;
    }

    private View buildAppearanceSettings() {
        LinearLayout card = settingsCard("Appearance", "Choose light, dark and your accent colour");
        RadioGroup themeGroup = new RadioGroup(this);
        themeGroup.setOrientation(RadioGroup.VERTICAL);
        RadioButton system = radio("Use device setting", AppSettings.THEME_SYSTEM);
        RadioButton light = radio("Light", AppSettings.THEME_LIGHT);
        RadioButton darkOption = radio("Dark", AppSettings.THEME_DARK);
        themeGroup.addView(system); themeGroup.addView(light); themeGroup.addView(darkOption);
        String current = AppSettings.theme(this);
        if (AppSettings.THEME_LIGHT.equals(current)) light.setChecked(true);
        else if (AppSettings.THEME_DARK.equals(current)) darkOption.setChecked(true);
        else system.setChecked(true);
        themeGroup.setOnCheckedChangeListener((radioGroup, checkedId) -> {
            RadioButton selected = radioGroup.findViewById(checkedId);
            if (selected == null) return;
            String chosen = String.valueOf(selected.getTag());
            if (!chosen.equals(AppSettings.theme(this))) {
                AppSettings.setTheme(this, chosen);
                recreate();
            }
        });
        card.addView(themeGroup, marginParams(-1, -2, 0, 0, 0, 12));

        card.addView(makeText("Theme colour", 14, text, true), marginParams(-1, -2, 0, 2, 0, 2));
        RadioGroup colors = new RadioGroup(this);
        String[][] choices = new String[][]{
            {"Purple", AppSettings.COLOR_PURPLE}, {"Blue", AppSettings.COLOR_BLUE},
            {"Teal", AppSettings.COLOR_TEAL}, {"Green", AppSettings.COLOR_GREEN},
            {"Orange", AppSettings.COLOR_ORANGE}, {"Rose", AppSettings.COLOR_ROSE}
        };
        for (String[] choice : choices) {
            RadioButton button = radio(choice[0], choice[1]);
            colors.addView(button);
            if (choice[1].equals(AppSettings.themeColor(this))) button.setChecked(true);
        }
        colors.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton selected = group.findViewById(checkedId);
            if (selected == null) return;
            String chosen = String.valueOf(selected.getTag());
            if (!chosen.equals(AppSettings.themeColor(this))) {
                AppSettings.setThemeColor(this, chosen);
                recreate();
            }
        });
        card.addView(colors);
        return card;
    }

    private View buildDataSettings() {
        LinearLayout card = settingsCard("Data, backup and sharing", "Stored privately on this phone and retained across app updates");
        TextView detail = makeText("Backups include every prayer, praise, Answered item and setting. Individual items can be shared from their ⋯ menu as a .pyr file.", 13, muted, false);
        detail.setLineSpacing(dp(2), 1f);
        card.addView(detail, marginParams(-1, -2, 0, 0, 0, 12));
        Button backup = actionButton("Back up to .pyr", true);
        backup.setOnClickListener(v -> createBackup());
        card.addView(backup, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        Button restore = actionButton("Restore from .pyr", false);
        restore.setOnClickListener(v -> chooseRestore());
        card.addView(restore, marginParams(-1, dp(46), 0, 10, 0, 0));
        return card;
    }

    private View buildNotificationSettings() {
        boolean granted = notificationsGranted();
        LinearLayout card = settingsCard("Notifications", granted ? "Allowed — reminders can reach you" : "Permission is currently disabled");
        Button button = actionButton(granted ? "Open notification settings" : "Enable notifications", !granted);
        button.setOnClickListener(v -> {
            if (!granted && Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION);
            else {
                Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(intent);
            }
        });
        card.addView(button, marginParams(-2, dp(46), 0, 8, 0, 0));
        return card;
    }

    private void createBackup() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(PyrFiles.MIME);
        intent.putExtra(Intent.EXTRA_TITLE, "prayr-backup-" + LocalDate.now() + ".pyr");
        startActivityForResult(intent, CREATE_BACKUP);
    }

    private void chooseRestore() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{PyrFiles.MIME, "application/octet-stream", "text/plain"});
        startActivityForResult(intent, RESTORE_BACKUP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == CREATE_BACKUP) {
                PyrFiles.write(this, uri, PyrFiles.createBackup(this));
                Toast.makeText(this, "Backup saved ✓", Toast.LENGTH_LONG).show();
            } else if (requestCode == RESTORE_BACKUP) {
                confirmRestore(PyrFiles.read(this, uri));
            }
        } catch (IOException | JSONException exception) {
            showFileError(exception);
        }
    }

    private void sharePrayer(Prayer prayerItem) {
        try {
            Uri uri = PyrFiles.prepareSharedPrayer(this, prayerItem);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType(PyrFiles.MIME);
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.putExtra(Intent.EXTRA_SUBJECT, "prayr: " + prayerItem.name);
            share.setClipData(ClipData.newUri(getContentResolver(), prayerItem.name, uri));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Share .pyr item"));
        } catch (IOException | JSONException exception) {
            showFileError(exception);
        }
    }

    private void confirmRestore(JSONObject root) {
        if (!PyrFiles.KIND_BACKUP.equals(root.optString("kind"))) {
            try {
                PrayerStore.add(this, PyrFiles.importedPrayer(root));
                NotificationScheduler.refresh(this);
                showingAnswered = false;
                showPrayerScreen();
                Toast.makeText(this, "Item added from .pyr ✓", Toast.LENGTH_LONG).show();
            } catch (JSONException exception) {
                showFileError(exception);
            }
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Restore this backup?")
            .setMessage("This replaces the current prayer list and settings with the contents of the .pyr backup.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Restore", (dialog, which) -> {
                try {
                    PyrFiles.restoreBackup(this, root);
                    NotificationScheduler.refresh(this);
                    Toast.makeText(this, "Backup restored ✓", Toast.LENGTH_LONG).show();
                    recreate();
                } catch (JSONException exception) {
                    showFileError(exception);
                }
            }).show();
    }

    private void handleAppIntent(Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            Uri uri = intent.getData();
            intent.setData(null);
            intent.setAction(null);
            try {
                JSONObject root = PyrFiles.read(this, uri);
                if (PyrFiles.KIND_PRAYER.equals(root.optString("kind"))) {
                    PrayerStore.add(this, PyrFiles.importedPrayer(root));
                    NotificationScheduler.refresh(this);
                    showingAnswered = false;
                    showPrayerScreen();
                    Toast.makeText(this, "Shared item added ✓", Toast.LENGTH_LONG).show();
                } else confirmRestore(root);
            } catch (IOException | JSONException exception) {
                showFileError(exception);
            }
            return;
        }
        String id = intent.getStringExtra(NotificationScheduler.EXTRA_PRAYER_ID);
        if (id == null) return;
        Prayer prayerItem = PrayerStore.find(this, id);
        intent.removeExtra(NotificationScheduler.EXTRA_PRAYER_ID);
        if (prayerItem != null) content.postDelayed(() -> showPrayerDetails(prayerItem), 250);
    }

    private void showFileError(Exception exception) {
        new AlertDialog.Builder(this)
            .setTitle("Could not open .pyr file")
            .setMessage(exception.getMessage() == null ? "The file is not a valid prayr file." : exception.getMessage())
            .setPositiveButton("OK", null)
            .show();
    }

    private LinearLayout settingsCard(String titleValue, String subtitle) {
        LinearLayout card = vertical();
        card.setPadding(dp(18), dp(17), dp(18), dp(17));
        card.setBackground(round(surface, 21, line, 1));
        card.addView(makeText(titleValue, 18, text, true));
        card.addView(makeText(subtitle, 13, muted, false), marginParams(-1, -2, 0, 3, 0, 14));
        return card;
    }

    private RadioButton radio(String value, String tag) {
        RadioButton button = new RadioButton(this);
        button.setId(View.generateViewId());
        button.setText(value);
        button.setTag(tag);
        button.setTextColor(text);
        button.setTextSize(15);
        button.setPadding(dp(2), dp(2), 0, dp(2));
        button.setButtonTintList(checkTint());
        button.setLayoutParams(new RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        return button;
    }

    private ColorStateList checkTint() {
        return new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{primary, muted});
    }

    private void styleSwitch(Switch control) {
        int[][] states = new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}};
        control.setThumbTintList(new ColorStateList(states, new int[]{primary, muted}));
        control.setTrackTintList(new ColorStateList(states, new int[]{primarySoft, line}));
    }

    private Button timeButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(primary);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(round(primarySoft, 14, primary, 1));
        return button;
    }

    private Button actionButton(String value, boolean filled) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(filled ? Color.WHITE : primary);
        button.setPadding(dp(18), 0, dp(18), 0);
        button.setBackground(round(filled ? primary : primarySoft, 14, filled ? 0 : primary, filled ? 0 : 1));
        return button;
    }

    private SeekBar styledSeekBar(int max, int progress) {
        SeekBar bar = new SeekBar(this);
        bar.setMax(max);
        bar.setProgress(Math.max(0, Math.min(max, progress)));
        bar.setProgressTintList(ColorStateList.valueOf(primary));
        bar.setThumbTintList(ColorStateList.valueOf(primary));
        bar.setKeyProgressIncrement(1);
        return bar;
    }

    private LinearLayout rangeLabels(String start, String end) {
        LinearLayout range = new LinearLayout(this);
        range.setOrientation(LinearLayout.HORIZONTAL);
        TextView minimum = makeText(start, 12, muted, true);
        TextView maximum = makeText(end, 12, primary, true);
        minimum.setGravity(Gravity.START);
        maximum.setGravity(Gravity.END);
        range.addView(minimum, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        range.addView(maximum, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return range;
    }

    private void pickTime(int currentMinutes, IntCallback callback) {
        TimePickerDialog picker = new TimePickerDialog(this, (view, hour, minute) -> callback.run(hour * 60 + minute), currentMinutes / 60, currentMinutes % 60, false);
        picker.show();
    }

    private SeekBar.OnSeekBarChangeListener seekListener(IntCallback callback) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { if (fromUser) callback.run(progress); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
    }

    private void setSubtreeEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) setSubtreeEnabled(group.getChildAt(i), enabled);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            content.postDelayed(() -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION), 650);
        }
    }

    private boolean notificationsGranted() {
        if (Build.VERSION.SDK_INT >= 33) return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        return manager.areNotificationsEnabled();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION && showingSettings) showSettingsScreen();
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView makeText(String value, float size, int textColor, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(textColor);
        view.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        return view;
    }

    private GradientDrawable round(int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams marginParams(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private LinearLayout.LayoutParams marginParams(int width, int height, int left, int top, int right, int bottom, float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height, weight);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int color(String hex) { return Color.parseColor(hex); }

    private int blend(int foreground, int backgroundColor, float foregroundWeight) {
        float weight = Math.max(0f, Math.min(1f, foregroundWeight));
        int red = Math.round(Color.red(foreground) * weight + Color.red(backgroundColor) * (1f - weight));
        int green = Math.round(Color.green(foreground) * weight + Color.green(backgroundColor) * (1f - weight));
        int blue = Math.round(Color.blue(foreground) * weight + Color.blue(backgroundColor) * (1f - weight));
        return Color.rgb(red, green, blue);
    }

    private int systemBarHeight(String resourceName, int fallbackDp) {
        int resourceId = getResources().getIdentifier(resourceName, "dimen", "android");
        return resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : dp(fallbackDp);
    }

    private String reminderQuantityText(int value) {
        return value + (value == 1 ? " reminder per day" : " reminders per day") + "  •  choose 1–" + AppSettings.MAX_REMINDERS;
    }

    private String intervalText(int minutes) {
        if (minutes == 0) return "Timing: random through the day";
        if (minutes < 60) return "Timing: every " + minutes + " minutes";
        if (minutes % 60 == 0) return "Timing: every " + (minutes / 60) + ((minutes / 60) == 1 ? " hour" : " hours");
        return "Timing: every " + (minutes / 60) + " h " + (minutes % 60) + " min";
    }

    private String clock(int minutes) {
        int clean = ((minutes % 1440) + 1440) % 1440;
        return LocalTime.of(clean / 60, clean % 60).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()));
    }

    private String date(long millis) {
        if (millis <= 0) return "Today";
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()));
    }

    private interface IntCallback { void run(int value); }
}
