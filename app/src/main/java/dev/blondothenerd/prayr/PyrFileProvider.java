package dev.blondothenerd.prayr;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Minimal read-only provider for sharing generated .pyr files safely. */
public final class PyrFileProvider extends ContentProvider {
    public static final String AUTHORITY = "dev.blondothenerd.prayr.pyrfiles";

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return PyrFiles.MIME; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Shared .pyr files are read-only");
        File file = resolve(uri);
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File file;
        try { file = resolve(uri); } catch (FileNotFoundException exception) { return null; }
        String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) row.add(file.getName());
            else if (OpenableColumns.SIZE.equals(column)) row.add(file.length());
            else row.add(null);
        }
        return cursor;
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("/") || name.contains("\\") || !name.endsWith(".pyr")) throw new FileNotFoundException("Invalid file");
        File directory = new File(getContext().getCacheDir(), "pyr-share");
        File file = new File(directory, name);
        try {
            if (!file.getCanonicalPath().startsWith(directory.getCanonicalPath() + File.separator)) throw new FileNotFoundException("Invalid path");
        } catch (IOException exception) {
            throw new FileNotFoundException("Invalid path");
        }
        if (!file.isFile()) throw new FileNotFoundException("Shared file expired");
        return file;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Read only"); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
