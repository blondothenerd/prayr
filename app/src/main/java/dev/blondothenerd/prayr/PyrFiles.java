package dev.blondothenerd.prayr;

import android.content.Context;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Creates, reads and restores prayr's portable .pyr files. */
public final class PyrFiles {
    public static final String MIME = "application/x-prayr";
    public static final String KIND_BACKUP = "backup";
    public static final String KIND_PRAYER = "prayer";
    private static final int MAX_FILE_BYTES = 5 * 1024 * 1024;

    private PyrFiles() {}

    public static JSONObject createBackup(Context context) throws JSONException {
        JSONObject root = envelope(KIND_BACKUP);
        root.put("data", PrayerStore.toJson(context));
        root.put("settings", AppSettings.toJson(context));
        root.put("exportedAt", System.currentTimeMillis());
        return root;
    }

    public static JSONObject createPrayer(Prayer prayer) throws JSONException {
        JSONObject root = envelope(KIND_PRAYER);
        root.put("item", prayer.toJson());
        return root;
    }

    private static JSONObject envelope(String kind) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("format", "prayr");
        root.put("formatVersion", 1);
        root.put("kind", kind);
        return root;
    }

    public static void write(Context context, Uri uri, JSONObject object) throws IOException {
        try (OutputStream output = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (output == null) throw new IOException("Cannot open destination");
            output.write(object.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (JSONException exception) {
            throw new IOException("Could not format backup", exception);
        }
    }

    public static JSONObject read(Context context, Uri uri) throws IOException, JSONException {
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IOException("Cannot open .pyr file");
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_FILE_BYTES) throw new IOException(".pyr file is too large");
                output.write(buffer, 0, read);
            }
            JSONObject root = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
            if (!"prayr".equals(root.optString("format"))) throw new JSONException("Not a prayr .pyr file");
            return root;
        }
    }

    public static void restoreBackup(Context context, JSONObject root) throws JSONException {
        if (!KIND_BACKUP.equals(root.optString("kind"))) throw new JSONException("This .pyr file is a shared item, not a full backup");
        PrayerStore.restoreFromJson(context, root.optJSONObject("data"));
        AppSettings.restoreFromJson(context, root.optJSONObject("settings"));
    }

    public static Prayer importedPrayer(JSONObject root) throws JSONException {
        if (!KIND_PRAYER.equals(root.optString("kind"))) throw new JSONException("This .pyr file is a full backup, not a single item");
        JSONObject item = root.optJSONObject("item");
        if (item == null) throw new JSONException("Shared prayer is missing");
        return Prayer.fromJson(item).importedCopy();
    }

    public static Uri prepareSharedPrayer(Context context, Prayer prayer) throws IOException, JSONException {
        File directory = new File(context.getCacheDir(), "pyr-share");
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("Cannot prepare share folder");
        String safe = prayer.name.replaceAll("[^A-Za-z0-9._-]", "-").replaceAll("-+", "-");
        if (safe.isEmpty()) safe = "prayer";
        if (safe.length() > 60) safe = safe.substring(0, 60);
        File file = new File(directory, "prayr-" + safe + ".pyr");
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(createPrayer(prayer).toString(2).getBytes(StandardCharsets.UTF_8));
        }
        return new Uri.Builder().scheme("content").authority(PyrFileProvider.AUTHORITY).appendPath(file.getName()).build();
    }
}
