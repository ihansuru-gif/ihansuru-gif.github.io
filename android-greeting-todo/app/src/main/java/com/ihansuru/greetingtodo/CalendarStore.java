package com.ihansuru.greetingtodo;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class CalendarStore {
    private static final String PREF = "greeting_calendar_store";
    private static final String KEY_EVENTS = "events";

    static final String REPEAT_NONE = "없음";
    static final String REPEAT_DAILY = "매일";
    static final String REPEAT_WEEKLY = "매주";
    static final String REPEAT_MONTHLY = "매월";
    static final String REPEAT_YEARLY = "매년";

    static final class Event {
        long id;
        String title = "";
        int startDay;
        int endDay;
        int startMinute = 9 * 60;
        int endMinute = 10 * 60;
        boolean allDay = true;
        int color = Color.rgb(127, 112, 232);
        String note = "";
        String link = "";
        String repeat = REPEAT_NONE;
        int reminderMinutes = -1;

        Event copy() {
            Event e = new Event();
            e.id = id;
            e.title = title;
            e.startDay = startDay;
            e.endDay = endDay;
            e.startMinute = startMinute;
            e.endMinute = endMinute;
            e.allDay = allDay;
            e.color = color;
            e.note = note;
            e.link = link;
            e.repeat = repeat;
            e.reminderMinutes = reminderMinutes;
            return e;
        }
    }

    static final class Occurrence {
        final Event event;
        final int startDay;
        final int endDay;
        Occurrence(Event event, int startDay, int endDay) {
            this.event = event;
            this.startDay = startDay;
            this.endDay = endDay;
        }
    }

    private CalendarStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    static ArrayList<Event> load(Context c) {
        ArrayList<Event> out = new ArrayList<>();
        String raw = prefs(c).getString(KEY_EVENTS, "");
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Event e = new Event();
                e.id = o.optLong("id", System.currentTimeMillis() + i);
                e.title = o.optString("title", "");
                e.startDay = o.optInt("startDay", today());
                e.endDay = o.optInt("endDay", e.startDay);
                if (compare(e.endDay, e.startDay) < 0) e.endDay = e.startDay;
                e.startMinute = clamp(o.optInt("startMinute", 9 * 60), 0, 1439);
                e.endMinute = clamp(o.optInt("endMinute", 10 * 60), 0, 1439);
                e.allDay = o.optBoolean("allDay", true);
                e.color = o.optInt("color", Color.rgb(127, 112, 232));
                e.note = o.optString("note", "");
                e.link = o.optString("link", "");
                e.repeat = normalizeRepeat(o.optString("repeat", REPEAT_NONE));
                e.reminderMinutes = o.optInt("reminderMinutes", -1);
                out.add(e);
            }
        } catch (JSONException ignored) {}
        sort(out);
        return out;
    }

    static void save(Context c, List<Event> events) {
        JSONArray arr = new JSONArray();
        for (Event e : events) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", e.id);
                o.put("title", safe(e.title));
                o.put("startDay", e.startDay);
                o.put("endDay", e.endDay);
                o.put("startMinute", clamp(e.startMinute, 0, 1439));
                o.put("endMinute", clamp(e.endMinute, 0, 1439));
                o.put("allDay", e.allDay);
                o.put("color", e.color);
                o.put("note", safe(e.note));
                o.put("link", safe(e.link));
                o.put("repeat", normalizeRepeat(e.repeat));
                o.put("reminderMinutes", e.reminderMinutes);
                arr.put(o);
            } catch (JSONException ignored) {}
        }
        prefs(c).edit().putString(KEY_EVENTS, arr.toString()).apply();
    }

    static Event get(Context c, long id) {
        for (Event e : load(c)) if (e.id == id) return e;
        return null;
    }

    static Event upsert(Context c, Event incoming) {
        ArrayList<Event> list = load(c);
        Event e = incoming.copy();
        if (e.id <= 0) e.id = System.currentTimeMillis();
        if (compare(e.endDay, e.startDay) < 0) {
            int t = e.startDay; e.startDay = e.endDay; e.endDay = t;
        }
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == e.id) {
                list.set(i, e);
                found = true;
                break;
            }
        }
        if (!found) list.add(e);
        sort(list);
        save(c, list);
        return e;
    }

    static void delete(Context c, long id) {
        ArrayList<Event> list = load(c);
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id == id) list.remove(i);
        save(c, list);
        CalendarReminderManager.cancel(c, id);
    }

    static ArrayList<Occurrence> occurrences(Context c, int rangeStart, int rangeEnd, String query, String filter) {
        ArrayList<Occurrence> out = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.KOREAN);
        for (Event e : load(c)) {
            if (!q.isEmpty()) {
                String hay = (safe(e.title) + " " + safe(e.note) + " " + safe(e.link)).toLowerCase(Locale.KOREAN);
                if (!hay.contains(q)) continue;
            }
            addOccurrences(out, e, rangeStart, rangeEnd);
        }
        if ("진행중".equals(filter)) {
            int today = today();
            for (int i = out.size() - 1; i >= 0; i--) {
                Occurrence o = out.get(i);
                if (compare(o.startDay, today) > 0 || compare(o.endDay, today) < 0) out.remove(i);
            }
        } else if ("이번주".equals(filter)) {
            int ws = startOfWeek(today());
            int we = addDays(ws, 6);
            for (int i = out.size() - 1; i >= 0; i--) {
                Occurrence o = out.get(i);
                if (!intersects(o.startDay, o.endDay, ws, we)) out.remove(i);
            }
        }
        Collections.sort(out, (a,b) -> {
            int d = compare(a.startDay, b.startDay);
            if (d != 0) return d;
            d = compare(b.endDay, a.endDay);
            if (d != 0) return d;
            return Long.compare(a.event.id, b.event.id);
        });
        return out;
    }

    private static void addOccurrences(List<Occurrence> out, Event e, int rs, int re) {
        int span = Math.max(0, daysBetween(e.startDay, e.endDay));
        if (REPEAT_NONE.equals(e.repeat)) {
            if (intersects(e.startDay, e.endDay, rs, re)) out.add(new Occurrence(e, e.startDay, e.endDay));
            return;
        }

        int cur = e.startDay;
        int guard = 0;
        while (compare(addDays(cur, span), rs) < 0 && guard++ < 5000) cur = nextRepeat(cur, e.repeat);
        guard = 0;
        while (compare(cur, re) <= 0 && guard++ < 5000) {
            int end = addDays(cur, span);
            if (intersects(cur, end, rs, re)) out.add(new Occurrence(e, cur, end));
            int next = nextRepeat(cur, e.repeat);
            if (next == cur) break;
            cur = next;
        }
    }

    static long nextReminderMillis(Event e, long now) {
        if (e == null || e.reminderMinutes < 0) return -1L;
        int cur = e.startDay;
        int guard = 0;
        while (guard++ < 5000) {
            Calendar c = toCalendar(cur);
            int minute = e.allDay ? 9 * 60 : e.startMinute;
            c.set(Calendar.HOUR_OF_DAY, minute / 60);
            c.set(Calendar.MINUTE, minute % 60);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            long at = c.getTimeInMillis() - e.reminderMinutes * 60_000L;
            if (at > now + 1000L) return at;
            if (REPEAT_NONE.equals(e.repeat)) return -1L;
            cur = nextRepeat(cur, e.repeat);
        }
        return -1L;
    }

    static int today() {
        return fromCalendar(Calendar.getInstance());
    }

    static int startOfWeek(int day) {
        Calendar c = toCalendar(day);
        c.setFirstDayOfWeek(Calendar.SUNDAY);
        int dow = c.get(Calendar.DAY_OF_WEEK);
        c.add(Calendar.DAY_OF_MONTH, -(dow - Calendar.SUNDAY));
        return fromCalendar(c);
    }

    static int monthGridStart(int anchor) {
        Calendar c = toCalendar(anchor);
        c.set(Calendar.DAY_OF_MONTH, 1);
        return startOfWeek(fromCalendar(c));
    }

    static int addDays(int day, int delta) {
        Calendar c = toCalendar(day);
        c.add(Calendar.DAY_OF_MONTH, delta);
        return fromCalendar(c);
    }

    static int addMonths(int day, int delta) {
        Calendar c = toCalendar(day);
        int dom = c.get(Calendar.DAY_OF_MONTH);
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.add(Calendar.MONTH, delta);
        int max = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        c.set(Calendar.DAY_OF_MONTH, Math.min(dom, max));
        return fromCalendar(c);
    }

    static int addYears(int day, int delta) {
        Calendar c = toCalendar(day);
        c.add(Calendar.YEAR, delta);
        return fromCalendar(c);
    }

    static int daysBetween(int a, int b) {
        Calendar ca = toCalendar(a);
        Calendar cb = toCalendar(b);
        long diff = cb.getTimeInMillis() - ca.getTimeInMillis();
        return (int) Math.round(diff / 86400000d);
    }

    static int compare(int a, int b) {
        return Integer.compare(a, b);
    }

    static Calendar toCalendar(int day) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(Calendar.YEAR, day / 10000);
        c.set(Calendar.MONTH, (day / 100 % 100) - 1);
        c.set(Calendar.DAY_OF_MONTH, day % 100);
        c.set(Calendar.HOUR_OF_DAY, 12);
        return c;
    }

    static int fromCalendar(Calendar c) {
        return c.get(Calendar.YEAR) * 10000
                + (c.get(Calendar.MONTH) + 1) * 100
                + c.get(Calendar.DAY_OF_MONTH);
    }

    static String formatDay(int day) {
        Calendar c = toCalendar(day);
        return new SimpleDateFormat("yyyy.MM.dd", Locale.KOREAN).format(c.getTime());
    }

    static String formatMonth(int day) {
        Calendar c = toCalendar(day);
        return new SimpleDateFormat("yyyy년 M월", Locale.KOREAN).format(c.getTime());
    }

    static String minuteLabel(int minute) {
        int m = clamp(minute, 0, 1439);
        return String.format(Locale.KOREAN, "%02d:%02d", m / 60, m % 60);
    }

    static boolean intersects(int s1, int e1, int s2, int e2) {
        return compare(s1, e2) <= 0 && compare(e1, s2) >= 0;
    }

    private static int nextRepeat(int day, String repeat) {
        if (REPEAT_DAILY.equals(repeat)) return addDays(day, 1);
        if (REPEAT_WEEKLY.equals(repeat)) return addDays(day, 7);
        if (REPEAT_MONTHLY.equals(repeat)) return addMonths(day, 1);
        if (REPEAT_YEARLY.equals(repeat)) return addYears(day, 1);
        return day;
    }

    private static String normalizeRepeat(String v) {
        if (REPEAT_DAILY.equals(v) || REPEAT_WEEKLY.equals(v)
                || REPEAT_MONTHLY.equals(v) || REPEAT_YEARLY.equals(v)) return v;
        return REPEAT_NONE;
    }

    private static void sort(ArrayList<Event> list) {
        Collections.sort(list, (a, b) -> Integer.compare(a.startDay, b.startDay));
    }

    private static String safe(String v) { return v == null ? "" : v; }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
