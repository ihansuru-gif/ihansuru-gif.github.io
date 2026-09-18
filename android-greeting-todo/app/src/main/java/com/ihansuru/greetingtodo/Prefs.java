package com.ihansuru.greetingtodo;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class Prefs {
    static final String MODE_IMAGE = "image";
    static final String MODE_TODO = "todo";
    static final String MODE_BOTH = "both";

    private static final String NAME = "greeting_todo_settings";
    private static final String DIRECT_NAME = "greeting_todo_direct";
    private static final String SEP = "__GT_SEP__";
    private static final String KEY_TODO_INITIALIZED = "todo_initialized";

    private Prefs() {}

    private static SharedPreferences normal(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    private static SharedPreferences direct(Context c) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return c.createDeviceProtectedStorageContext().getSharedPreferences(DIRECT_NAME, Context.MODE_PRIVATE);
        }
        return normal(c);
    }

    static boolean enabled(Context c) { return direct(c).getBoolean("enabled", false); }

    static void setEnabled(Context c, boolean v) {
        direct(c).edit().putBoolean("enabled", v).apply();
        normal(c).edit().putBoolean("enabled", v).apply();
    }

    static long duration(Context c) {
        return Math.max(500L, Math.min(10000L, normal(c).getLong("duration", 4000L)));
    }

    static void setDuration(Context c, long v) {
        normal(c).edit().putLong("duration", Math.max(500L, Math.min(10000L, v))).apply();
    }

    static boolean tapDismiss(Context c) { return false; }
    static void setTapDismiss(Context c, boolean ignored) {
        normal(c).edit().putBoolean("tap_dismiss", false).apply();
    }

    static String mode(Context c) {
        String v = normal(c).getString("mode", MODE_IMAGE);
        return MODE_TODO.equals(v) || MODE_BOTH.equals(v) ? v : MODE_IMAGE;
    }

    static void setMode(Context c, String v) {
        if (!MODE_IMAGE.equals(v) && !MODE_TODO.equals(v) && !MODE_BOTH.equals(v)) v = MODE_IMAGE;
        boolean todo = MODE_TODO.equals(v) || MODE_BOTH.equals(v);
        boolean image = MODE_IMAGE.equals(v) || MODE_BOTH.equals(v);
        normal(c).edit()
                .putString("mode", v)
                .putBoolean("todo_expanded", todo)
                .putBoolean("image_expanded", image)
                .apply();
    }

    static boolean todoTabEnabled(Context c) {
        SharedPreferences p = normal(c);
        return p.contains("tab_todo") ? p.getBoolean("tab_todo", true) : true;
    }
    static void setTodoTabEnabled(Context c, boolean v) {
        normal(c).edit().putBoolean("tab_todo", v).apply();
    }

    static boolean imageTabEnabled(Context c) {
        SharedPreferences p = normal(c);
        if (p.contains("tab_image")) return p.getBoolean("tab_image", true);
        String m = mode(c);
        return MODE_IMAGE.equals(m) || MODE_BOTH.equals(m);
    }
    static void setImageTabEnabled(Context c, boolean v) {
        normal(c).edit().putBoolean("tab_image", v).apply();
    }

    static boolean calendarEnabled(Context c) { return normal(c).getBoolean("calendar_enabled", true); }
    static void setCalendarEnabled(Context c, boolean v) { normal(c).edit().putBoolean("calendar_enabled", v).apply(); }
    static boolean calendarExpanded(Context c) { return normal(c).getBoolean("calendar_expanded", false); }
    static void setCalendarExpanded(Context c, boolean v) { normal(c).edit().putBoolean("calendar_expanded", v).apply(); }

    static boolean showImage(Context c) { return imageTabEnabled(c); }
    static boolean showTodo(Context c) { return todoTabEnabled(c); }

    static boolean todoExpanded(Context c) {
        SharedPreferences p = normal(c);
        return p.contains("todo_expanded") ? p.getBoolean("todo_expanded", true) : showTodo(c);
    }
    static void setTodoExpanded(Context c, boolean v) { normal(c).edit().putBoolean("todo_expanded", v).apply(); }

    static boolean imageExpanded(Context c) {
        SharedPreferences p = normal(c);
        return p.contains("image_expanded") ? p.getBoolean("image_expanded", false) : showImage(c);
    }
    static void setImageExpanded(Context c, boolean v) { normal(c).edit().putBoolean("image_expanded", v).apply(); }

    static boolean memoEnabled(Context c) { return normal(c).getBoolean("memo_enabled", true); }
    static void setMemoEnabled(Context c, boolean v) { normal(c).edit().putBoolean("memo_enabled", v).apply(); }
    static boolean memoExpanded(Context c) { return normal(c).getBoolean("memo_expanded", false); }
    static void setMemoExpanded(Context c, boolean v) { normal(c).edit().putBoolean("memo_expanded", v).apply(); }

    static int tabSize(Context c) { return clamp(normal(c).getInt("tab_size", 100), 75, 135); }
    static void setTabSize(Context c, int v) { normal(c).edit().putInt("tab_size", clamp(v, 75, 135)).apply(); }

    static List<String> tabOrder(Context c) {
        String raw = normal(c).getString("tab_order", "todo,calendar,memo,image");
        ArrayList<String> out = new ArrayList<>();
        if (raw != null) {
            for (String key : raw.split(",")) {
                if (("todo".equals(key) || "calendar".equals(key)
                        || "memo".equals(key) || "image".equals(key))
                        && !out.contains(key)) out.add(key);
            }
        }
        for (String key : Arrays.asList("todo", "calendar", "memo", "image")) {
            if (!out.contains(key)) out.add(key);
        }
        return out;
    }

    static void moveTab(Context c, String key, int direction) {
        ArrayList<String> order = new ArrayList<>(tabOrder(c));
        int from = order.indexOf(key);
        if (from < 0) return;
        int to = clamp(from + direction, 0, order.size() - 1);
        if (from == to) return;
        order.remove(from);
        order.add(to, key);
        StringBuilder joined = new StringBuilder();
        for (String item : order) {
            if (joined.length() > 0) joined.append(',');
            joined.append(item);
        }
        normal(c).edit().putString("tab_order", joined.toString()).apply();
    }

    static int calendarWidth(Context c) { return clamp(normal(c).getInt("calendar_width", 91), 52, 96); }
    static int calendarHeight(Context c) { return clamp(normal(c).getInt("calendar_height", 62), 32, 90); }
    static void setCalendarSize(Context c, int widthPct, int heightPct) {
        normal(c).edit()
                .putInt("calendar_width", clamp(widthPct, 52, 96))
                .putInt("calendar_height", clamp(heightPct, 32, 90))
                .apply();
    }
    static float calendarX(Context c) { return unit(normal(c).getFloat("calendar_x", .48f)); }
    static float calendarY(Context c) { return unit(normal(c).getFloat("calendar_y", .47f)); }
    static void setCalendarPosition(Context c, float x, float y) {
        normal(c).edit().putFloat("calendar_x", unit(x)).putFloat("calendar_y", unit(y)).apply();
    }
    static String calendarView(Context c) {
        return "week".equals(normal(c).getString("calendar_view", "month")) ? "week" : "month";
    }
    static void setCalendarView(Context c, String value) {
        normal(c).edit().putString("calendar_view", "week".equals(value) ? "week" : "month").apply();
    }

    static int imageSize(Context c) { return clamp(normal(c).getInt("image_size", 100), 30, 220); }
    static void setImageSize(Context c, int v) { normal(c).edit().putInt("image_size", clamp(v, 30, 220)).apply(); }
    static float imageX(Context c) { return unit(normal(c).getFloat("image_x", .68f)); }
    static float imageY(Context c) { return unit(normal(c).getFloat("image_y", .70f)); }
    static void setImagePosition(Context c, float x, float y) {
        normal(c).edit().putFloat("image_x", unit(x)).putFloat("image_y", unit(y)).apply();
    }

    static int todoWidth(Context c) { return clamp(normal(c).getInt("todo_width", 82), 42, 96); }
    static void setTodoWidth(Context c, int v) { normal(c).edit().putInt("todo_width", clamp(v, 42, 96)).apply(); }

    static int todoHeight(Context c) { return clamp(normal(c).getInt("todo_height", 0), 0, 90); }
    static void setTodoSize(Context c, int widthPct, int heightPct) {
        normal(c).edit()
                .putInt("todo_width", clamp(widthPct, 42, 96))
                .putInt("todo_height", clamp(heightPct, 20, 90))
                .apply();
    }

    static float todoScale(Context c) { return clampFloat(normal(c).getFloat("todo_scale", 1f), .72f, 1.55f); }
    static void setTodoScale(Context c, float v) { normal(c).edit().putFloat("todo_scale", clampFloat(v, .72f, 1.55f)).apply(); }
    static float todoX(Context c) { return unit(normal(c).getFloat("todo_x", .50f)); }
    static float todoY(Context c) { return unit(normal(c).getFloat("todo_y", .34f)); }
    static void setTodoPosition(Context c, float x, float y) {
        normal(c).edit().putFloat("todo_x", unit(x)).putFloat("todo_y", unit(y)).apply();
    }

    static int memoWidth(Context c) { return clamp(normal(c).getInt("memo_width", 78), 42, 96); }
    static int memoHeight(Context c) { return clamp(normal(c).getInt("memo_height", 48), 24, 88); }
    static void setMemoSize(Context c, int widthPct, int heightPct) {
        normal(c).edit()
                .putInt("memo_width", clamp(widthPct, 42, 96))
                .putInt("memo_height", clamp(heightPct, 24, 88))
                .apply();
    }
    static float memoX(Context c) { return unit(normal(c).getFloat("memo_x", .48f)); }
    static float memoY(Context c) { return unit(normal(c).getFloat("memo_y", .55f)); }
    static void setMemoPosition(Context c, float x, float y) {
        normal(c).edit().putFloat("memo_x", unit(x)).putFloat("memo_y", unit(y)).apply();
    }
    static float memoTextScale(Context c) {
        return clampFloat(normal(c).getFloat("memo_text_scale", 1f), .82f, 1.65f);
    }
    static void setMemoTextScale(Context c, float v) {
        normal(c).edit().putFloat("memo_text_scale", clampFloat(v, .82f, 1.65f)).apply();
    }

    static float memoBrushHue(Context c) {
        float h = normal(c).getFloat("memo_brush_hue", 220f) % 360f;
        return h < 0 ? h + 360f : h;
    }
    static void setMemoBrushHue(Context c, float h) {
        normal(c).edit().putFloat("memo_brush_hue", ((h % 360f) + 360f) % 360f).apply();
    }
    static int memoBrushSat(Context c) { return clamp(normal(c).getInt("memo_brush_sat", 82), 0, 100); }
    static void setMemoBrushSat(Context c, int v) { normal(c).edit().putInt("memo_brush_sat", clamp(v, 0, 100)).apply(); }
    static int memoBrushValue(Context c) { return clamp(normal(c).getInt("memo_brush_value", 92), 8, 100); }
    static void setMemoBrushValue(Context c, int v) { normal(c).edit().putInt("memo_brush_value", clamp(v, 8, 100)).apply(); }
    static float memoBrushWidth(Context c) { return clampFloat(normal(c).getFloat("memo_brush_width", 5f), 2f, 18f); }
    static void setMemoBrushWidth(Context c, float v) { normal(c).edit().putFloat("memo_brush_width", clampFloat(v, 2f, 18f)).apply(); }
    static int memoBrushColor(Context c) {
        return Color.HSVToColor(new float[]{
                memoBrushHue(c),
                memoBrushSat(c) / 100f,
                memoBrushValue(c) / 100f});
    }

    static float textScale(Context c) { return clampFloat(normal(c).getFloat("text_scale", 1f), .82f, 1.65f); }
    static void setTextScale(Context c, float v) { normal(c).edit().putFloat("text_scale", clampFloat(v, .82f, 1.65f)).apply(); }

    static float hue(Context c) {
        float h = normal(c).getFloat("todo_hue", 232f) % 360f;
        return h < 0 ? h + 360f : h;
    }

    static void setHue(Context c, float h) {
        normal(c).edit().putFloat("todo_hue", ((h % 360f) + 360f) % 360f).apply();
    }

    static int saturation(Context c) { return clamp(normal(c).getInt("todo_sat", 34), 12, 76); }
    static void setSaturation(Context c, int v) { normal(c).edit().putInt("todo_sat", clamp(v, 12, 76)).apply(); }
    static int todoColor(Context c) {
        return Color.HSVToColor(new float[]{hue(c), saturation(c) / 100f, .98f});
    }

    static String weatherSummary(Context c) {
        String value = normal(c).getString("weather_summary", "");
        return value == null ? "" : value.trim();
    }

    static void setWeatherSummary(Context c, String value) {
        normal(c).edit().putString("weather_summary", value == null ? "" : value.trim()).apply();
    }

    static List<String> items(Context c) {
        SharedPreferences p = normal(c);
        String raw = p.getString("todo_items", null);
        boolean initialized = p.getBoolean(KEY_TODO_INITIALIZED, false);
        if (!initialized && (raw == null || raw.isEmpty())) {
            return new ArrayList<>(Arrays.asList("자료 조사하기", "메일 답장하기", "운동하기", "저녁 약속 준비"));
        }
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        return split(raw);
    }

    static List<String> categories(Context c) {
        SharedPreferences p = normal(c);
        String raw = p.getString("todo_categories", null);
        boolean initialized = p.getBoolean(KEY_TODO_INITIALIZED, false);
        List<String> out;
        if (!initialized && (raw == null || raw.isEmpty())) {
            out = new ArrayList<>(Arrays.asList("업무", "업무", "개인", "개인"));
        } else {
            out = raw == null || raw.isEmpty() ? new ArrayList<>() : split(raw);
        }
        int size = items(c).size();
        while (out.size() < size) out.add("업무");
        while (out.size() > size) out.remove(out.size() - 1);
        return out;
    }

    static void setItems(Context c, List<String> items, List<String> categories) {
        ArrayList<String> cleanItems = new ArrayList<>();
        ArrayList<String> cleanCategories = new ArrayList<>();
        for (int i = 0; i < items.size() && cleanItems.size() < 12; i++) {
            String text = items.get(i) == null ? "" : items.get(i).trim().replace(SEP, " ");
            if (text.isEmpty()) continue;
            cleanItems.add(text);
            String category = i < categories.size() ? categories.get(i) : "업무";
            cleanCategories.add(normalizeCategory(category));
        }
        normal(c).edit()
                .putBoolean(KEY_TODO_INITIALIZED, true)
                .putString("todo_items", join(cleanItems))
                .putString("todo_categories", join(cleanCategories))
                .putInt("checked", 0)
                .apply();
    }

    static void addItem(Context c, String text, String category) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return;
        ArrayList<String> currentItems = new ArrayList<>(items(c));
        ArrayList<String> currentCategories = new ArrayList<>(categories(c));
        if (currentItems.size() >= 12) return;
        currentItems.add(value);
        currentCategories.add(normalizeCategory(category));
        setItems(c, currentItems, currentCategories);
    }

    static void updateItem(Context c, int index, String text, String category) {
        ArrayList<String> currentItems = new ArrayList<>(items(c));
        ArrayList<String> currentCategories = new ArrayList<>(categories(c));
        if (index < 0 || index >= currentItems.size()) return;
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            completeItem(c, index);
            return;
        }
        currentItems.set(index, value);
        while (currentCategories.size() < currentItems.size()) currentCategories.add("업무");
        currentCategories.set(index, normalizeCategory(category));
        setItems(c, currentItems, currentCategories);
    }

    static void cycleCategory(Context c, int index) {
        ArrayList<String> currentItems = new ArrayList<>(items(c));
        ArrayList<String> currentCategories = new ArrayList<>(categories(c));
        if (index < 0 || index >= currentItems.size()) return;
        while (currentCategories.size() < currentItems.size()) currentCategories.add("업무");
        String current = normalizeCategory(currentCategories.get(index));
        String next = "업무".equals(current) ? "개인" : "개인".equals(current) ? "기타" : "업무";
        currentCategories.set(index, next);
        setItems(c, currentItems, currentCategories);
    }

    static void moveItem(Context c, int fromIndex, int toIndex) {
        ArrayList<String> currentItems = new ArrayList<>(items(c));
        ArrayList<String> currentCategories = new ArrayList<>(categories(c));
        if (fromIndex < 0 || fromIndex >= currentItems.size()) return;
        if (toIndex < 0 || toIndex >= currentItems.size() || fromIndex == toIndex) return;
        while (currentCategories.size() < currentItems.size()) currentCategories.add("업무");
        String item = currentItems.remove(fromIndex);
        String category = currentCategories.remove(fromIndex);
        currentItems.add(toIndex, item);
        currentCategories.add(toIndex, category);
        setItems(c, currentItems, currentCategories);
    }

    static void completeItem(Context c, int index) {
        ArrayList<String> currentItems = new ArrayList<>(items(c));
        ArrayList<String> currentCategories = new ArrayList<>(categories(c));
        if (index < 0 || index >= currentItems.size()) return;
        currentItems.remove(index);
        if (index < currentCategories.size()) currentCategories.remove(index);
        setItems(c, currentItems, currentCategories);
    }

    static int checkedMask(Context c) { return 0; }
    static boolean checked(Context c, int i) { return false; }
    static void toggleChecked(Context c, int i) { completeItem(c, i); }

    static void resetLayout(Context c) {
        normal(c).edit()
                .putInt("image_size", 100)
                .putFloat("image_x", .68f)
                .putFloat("image_y", .70f)
                .putInt("todo_width", 82)
                .putInt("todo_height", 0)
                .putFloat("todo_scale", 1f)
                .putFloat("todo_x", .50f)
                .putFloat("todo_y", .34f)
                .putInt("calendar_width", 91)
                .putInt("calendar_height", 62)
                .putFloat("calendar_x", .48f)
                .putFloat("calendar_y", .47f)
                .putString("calendar_view", "month")
                .putInt("memo_width", 78)
                .putInt("memo_height", 48)
                .putFloat("memo_x", .48f)
                .putFloat("memo_y", .55f)
                .putFloat("memo_text_scale", 1f)
                .putFloat("memo_brush_hue", 220f)
                .putInt("memo_brush_sat", 82)
                .putInt("memo_brush_value", 92)
                .putFloat("memo_brush_width", 5f)
                .putFloat("text_scale", 1f)
                .putFloat("todo_hue", 232f)
                .putInt("todo_sat", 34)
                .putInt("tab_size", 100)
                .putBoolean("tap_dismiss", false)
                .apply();
    }

    private static String normalizeCategory(String category) {
        if ("개인".equals(category) || "기타".equals(category)) return category;
        return "업무";
    }

    private static List<String> split(String raw) {
        ArrayList<String> out = new ArrayList<>();
        for (String s : raw.split(SEP, -1)) if (!s.isEmpty()) out.add(s);
        return out;
    }

    private static String join(List<String> values) {
        StringBuilder b = new StringBuilder();
        for (String s : values) {
            if (b.length() > 0) b.append(SEP);
            b.append(s == null ? "" : s.replace(SEP, " "));
        }
        return b.toString();
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static float clampFloat(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    private static float unit(float v) { return Math.max(.04f, Math.min(.96f, v)); }
}
