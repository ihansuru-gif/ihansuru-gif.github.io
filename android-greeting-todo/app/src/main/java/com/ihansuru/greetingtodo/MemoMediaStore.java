package com.ihansuru.greetingtodo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

final class MemoMediaStore {
    private MemoMediaStore() {}

    static String importImage(Context c, Uri uri) {
        if (uri == null) return "";
        File dir = new File(c.getFilesDir(), "memo_images");
        if (!dir.exists() && !dir.mkdirs()) return "";
        File out = new File(dir, "img_" + System.currentTimeMillis() + ".bin");
        try (InputStream in = c.getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(out)) {
            if (in == null) return "";
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
            fos.flush();
            Bitmap test = BitmapFactory.decodeFile(out.getAbsolutePath());
            if (test == null) {
                out.delete();
                return "";
            }
            test.recycle();
            return out.getAbsolutePath();
        } catch (Exception e) {
            out.delete();
            return "";
        }
    }

    static Bitmap loadBitmap(String path, int maxSide) {
        if (path == null || path.isEmpty()) return null;
        File f = new File(path);
        if (!f.exists()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        int sample = 1;
        int max = Math.max(bounds.outWidth, bounds.outHeight);
        while (max / sample > Math.max(256, maxSide)) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        return BitmapFactory.decodeFile(path, opts);
    }

    static File newVoiceFile(Context c) {
        File dir = new File(c.getFilesDir(), "memo_voice");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "voice_" + System.currentTimeMillis() + ".m4a");
    }

    static void deletePath(String path) {
        if (path == null || path.isEmpty()) return;
        try { new File(path).delete(); } catch (RuntimeException ignored) {}
    }

    static String durationLabel(String path) {
        if (path == null || path.isEmpty()) return "0:00";
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(path);
            String raw = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long ms = raw == null ? 0L : Long.parseLong(raw);
            long sec = Math.max(0L, ms / 1000L);
            return String.format(Locale.KOREAN, "%d:%02d", sec / 60L, sec % 60L);
        } catch (Exception e) {
            return "0:00";
        } finally {
            try { r.release(); } catch (Exception ignored) {}
        }
    }
}
