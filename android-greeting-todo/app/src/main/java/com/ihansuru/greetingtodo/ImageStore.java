package com.ihansuru.greetingtodo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

final class ImageStore {
    private static final String FILE_NAME = "selected_image.bin";
    private static final long MAX_BYTES = 25L * 1024L * 1024L;
    private ImageStore() {}

    static File file(Context c) { return new File(c.getFilesDir(), FILE_NAME); }
    static boolean has(Context c) { return file(c).isFile() && file(c).length() > 0; }

    static boolean importUri(Context c, Uri uri) {
        File temp = new File(c.getFilesDir(), FILE_NAME + ".tmp");
        long count = 0;
        try (InputStream in = c.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(temp)) {
            if (in == null) return false;
            byte[] buffer = new byte[32768];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                count += read;
                if (count > MAX_BYTES) return false;
                out.write(buffer, 0, read);
            }
            out.flush();
        } catch (Exception e) {
            temp.delete();
            return false;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(temp.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 16000 || bounds.outHeight > 16000) {
            temp.delete();
            return false;
        }
        File target = file(c);
        if (target.exists()) target.delete();
        if (!temp.renameTo(target)) {
            temp.delete();
            return false;
        }
        return true;
    }

    static Bitmap load(Context c, int maxEdge) {
        File f = file(c);
        if (!f.isFile()) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            int sample = 1;
            while (longest / sample > maxEdge && sample < 128) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
        } catch (OutOfMemoryError | RuntimeException e) {
            return null;
        }
    }
}
