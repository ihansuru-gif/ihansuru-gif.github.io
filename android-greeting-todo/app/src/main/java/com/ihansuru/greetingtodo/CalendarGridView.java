package com.ihansuru.greetingtodo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class CalendarGridView extends View {
    interface Listener {
        void onRangeSelected(int startDay, int endDay);
        void onEventClicked(long eventId);
        void onInteraction(boolean active);
    }

    static final int VIEW_MONTH = 0;
    static final int VIEW_WEEK = 1;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<Hit> hits = new ArrayList<>();

    private Listener listener;
    private int anchorDay = CalendarStore.today();
    private int viewMode = VIEW_MONTH;
    private String query = "";
    private String filter = "전체";

    private float downX;
    private float downY;
    private boolean dragging;
    private int dragStartDay;
    private int dragEndDay;
    private long downEventId = -1L;

    CalendarGridView(Context context) {
        super(context);
        setClickable(true);
        textPaint.setTypeface(android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL));
    }

    void setListener(Listener value) { listener = value; }
    void setAnchorDay(int value) { anchorDay = value; invalidate(); }
    int getAnchorDay() { return anchorDay; }
    void setViewMode(int value) { viewMode = value == VIEW_WEEK ? VIEW_WEEK : VIEW_MONTH; invalidate(); }
    int getViewMode() { return viewMode; }
    void setQuery(String value) { query = value == null ? "" : value; invalidate(); }
    void setFilter(String value) { filter = value == null ? "전체" : value; invalidate(); }

    int visibleStartDay() {
        return viewMode == VIEW_WEEK
                ? CalendarStore.startOfWeek(anchorDay)
                : CalendarStore.monthGridStart(anchorDay);
    }

    int visibleEndDay() {
        return CalendarStore.addDays(visibleStartDay(), viewMode == VIEW_WEEK ? 6 : 41);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        hits.clear();

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        float headerH = dp(28);
        float cellW = width / 7f;
        int rows = viewMode == VIEW_WEEK ? 1 : 6;
        float cellH = Math.max(dp(78), (height - headerH) / rows);

        drawWeekdayHeader(canvas, cellW, headerH);

        int start = visibleStartDay();
        int today = CalendarStore.today();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 7; col++) {
                int index = row * 7 + col;
                int day = CalendarStore.addDays(start, index);
                float l = col * cellW;
                float t = headerH + row * cellH;
                float r = l + cellW;
                float b = Math.min(height, t + cellH);

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.rgb(255, 255, 255));
                canvas.drawRect(l, t, r, b, paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(.7f));
                paint.setColor(Color.rgb(235, 232, 227));
                canvas.drawRect(l, t, r, b, paint);

                int dayNum = day % 100;
                int textColor = DesignTokens.INK;
                if (col == 0) textColor = Color.rgb(190, 103, 106);
                if (col == 6) textColor = DesignTokens.TODO;

                if (viewMode == VIEW_MONTH) {
                    int anchorMonth = anchorDay / 100 % 100;
                    int dayMonth = day / 100 % 100;
                    if (anchorMonth != dayMonth) textColor = DesignTokens.MUTED;
                }

                if (day == today) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(DesignTokens.CALENDAR);
                    canvas.drawCircle(l + dp(18), t + dp(18), dp(13), paint);
                    textColor = Color.WHITE;
                }
                textPaint.setTextSize(sp(12));
                textPaint.setColor(textColor);
                textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                canvas.drawText(String.valueOf(dayNum), l + dp(10), t + dp(22), textPaint);
            }
        }

        drawEvents(canvas, start, rows, headerH, cellW, cellH, height);
        if (dragging) drawSelection(canvas, start, rows, headerH, cellW, cellH, height);
    }

    private void drawWeekdayHeader(Canvas canvas, float cellW, float headerH) {
        String[] names = {"일", "월", "화", "수", "목", "금", "토"};
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(DesignTokens.SURFACE_SOFT);
        canvas.drawRect(0, 0, getWidth(), headerH, paint);

        textPaint.setTextSize(sp(11.5f));
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        for (int i = 0; i < 7; i++) {
            textPaint.setColor(i == 0 ? Color.rgb(190, 103, 106)
                    : i == 6 ? DesignTokens.TODO : DesignTokens.SECONDARY);
            float tw = textPaint.measureText(names[i]);
            canvas.drawText(names[i], i * cellW + (cellW - tw) / 2f, headerH - dp(8), textPaint);
        }
    }

    private void drawEvents(Canvas canvas, int visibleStart, int rows, float headerH,
                            float cellW, float cellH, int fullHeight) {
        int maxLane = viewMode == VIEW_WEEK ? 7 : 4;
        for (int row = 0; row < rows; row++) {
            int weekStart = CalendarStore.addDays(visibleStart, row * 7);
            int weekEnd = CalendarStore.addDays(weekStart, 6);
            ArrayList<CalendarStore.Occurrence> occurrences =
                    CalendarStore.occurrences(getContext(), weekStart, weekEnd, query, filter);

            int[] laneEnds = new int[maxLane];
            for (int i = 0; i < maxLane; i++) laneEnds[i] = 0;

            for (CalendarStore.Occurrence occurrence : occurrences) {
                int segmentStart = CalendarStore.compare(occurrence.startDay, weekStart) < 0
                        ? weekStart : occurrence.startDay;
                int segmentEnd = CalendarStore.compare(occurrence.endDay, weekEnd) > 0
                        ? weekEnd : occurrence.endDay;

                int lane = -1;
                for (int i = 0; i < maxLane; i++) {
                    if (laneEnds[i] == 0 || CalendarStore.compare(segmentStart, laneEnds[i]) > 0) {
                        lane = i;
                        laneEnds[i] = segmentEnd;
                        break;
                    }
                }
                if (lane < 0) continue;

                int startCol = Math.max(0, CalendarStore.daysBetween(weekStart, segmentStart));
                int endCol = Math.min(6, CalendarStore.daysBetween(weekStart, segmentEnd));
                float left = startCol * cellW + dp(3);
                float right = (endCol + 1) * cellW - dp(3);
                float top = headerH + row * cellH + dp(29) + lane * dp(19);
                float bottom = Math.min(fullHeight - dp(2), top + dp(16));
                if (bottom <= top) continue;

                RectF rect = new RectF(left, top, right, bottom);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(occurrence.event.color);
                canvas.drawRoundRect(rect, dp(7), dp(7), paint);

                hits.add(new Hit(rect, occurrence.event.id));

                if (segmentStart == occurrence.startDay || startCol == 0) {
                    textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    textPaint.setTextSize(sp(viewMode == VIEW_WEEK ? 11.5f : 9.5f));
                    textPaint.setColor(bestTextColor(occurrence.event.color));
                    String name = occurrence.event.title == null || occurrence.event.title.trim().isEmpty()
                            ? "일정" : occurrence.event.title.trim();
                    float available = Math.max(0, rect.width() - dp(8));
                    String clipped = ellipsize(name, available);
                    canvas.drawText(clipped, rect.left + dp(4), rect.bottom - dp(3.5f), textPaint);
                }
            }
        }
    }

    private void drawSelection(Canvas canvas, int visibleStart, int rows, float headerH,
                               float cellW, float cellH, int fullHeight) {
        int s = dragStartDay;
        int e = dragEndDay;
        if (CalendarStore.compare(s, e) > 0) { int t = s; s = e; e = t; }

        int visibleEnd = CalendarStore.addDays(visibleStart, rows * 7 - 1);
        if (!CalendarStore.intersects(s, e, visibleStart, visibleEnd)) return;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(DesignTokens.alpha(DesignTokens.CALENDAR, 68));

        for (int row = 0; row < rows; row++) {
            int weekStart = CalendarStore.addDays(visibleStart, row * 7);
            int weekEnd = CalendarStore.addDays(weekStart, 6);
            if (!CalendarStore.intersects(s, e, weekStart, weekEnd)) continue;
            int ss = CalendarStore.compare(s, weekStart) < 0 ? weekStart : s;
            int ee = CalendarStore.compare(e, weekEnd) > 0 ? weekEnd : e;
            int c1 = CalendarStore.daysBetween(weekStart, ss);
            int c2 = CalendarStore.daysBetween(weekStart, ee);
            float top = headerH + row * cellH + dp(4);
            float bottom = Math.min(fullHeight - dp(1), headerH + (row + 1) * cellH - dp(4));
            RectF r = new RectF(c1 * cellW + dp(2), top, (c2 + 1) * cellW - dp(2), bottom);
            canvas.drawRoundRect(r, dp(9), dp(9), paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (getWidth() <= 0 || getHeight() <= 0) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                downEventId = hitEvent(downX, downY);
                dragStartDay = dateFromPoint(downX, downY);
                dragEndDay = dragStartDay;
                dragging = dragStartDay != 0;
                if (listener != null) listener.onInteraction(true);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    int d = dateFromPoint(event.getX(), event.getY());
                    if (d != 0 && d != dragEndDay) {
                        dragEndDay = d;
                        invalidate();
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                boolean tap = Math.hypot(dx, dy) < dp(12);
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    if (tap && downEventId > 0) {
                        if (listener != null) listener.onEventClicked(downEventId);
                    } else if (dragging && dragStartDay != 0 && dragEndDay != 0) {
                        int s = dragStartDay;
                        int e = dragEndDay;
                        if (CalendarStore.compare(s, e) > 0) { int t = s; s = e; e = t; }
                        if (listener != null) listener.onRangeSelected(s, e);
                    }
                }
                dragging = false;
                downEventId = -1L;
                if (listener != null) listener.onInteraction(false);
                invalidate();
                performClick();
                return true;
            default:
                return true;
        }
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    private long hitEvent(float x, float y) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.rect.contains(x, y)) return h.eventId;
        }
        return -1L;
    }

    private int dateFromPoint(float x, float y) {
        float headerH = dp(28);
        if (y < headerH) return 0;
        int rows = viewMode == VIEW_WEEK ? 1 : 6;
        float cellW = getWidth() / 7f;
        float cellH = Math.max(dp(78), (getHeight() - headerH) / rows);
        int col = Math.max(0, Math.min(6, (int) (x / Math.max(1f, cellW))));
        int row = Math.max(0, Math.min(rows - 1, (int) ((y - headerH) / Math.max(1f, cellH))));
        return CalendarStore.addDays(visibleStartDay(), row * 7 + col);
    }

    private String ellipsize(String value, float available) {
        if (textPaint.measureText(value) <= available) return value;
        String ell = "…";
        for (int i = value.length() - 1; i > 0; i--) {
            String candidate = value.substring(0, i) + ell;
            if (textPaint.measureText(candidate) <= available) return candidate;
        }
        return ell;
    }

    private int bestTextColor(int background) {
        double y = (0.299 * Color.red(background) + 0.587 * Color.green(background)
                + 0.114 * Color.blue(background)) / 255d;
        return y > .7 ? DesignTokens.INK : Color.WHITE;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }

    private static final class Hit {
        final RectF rect;
        final long eventId;
        Hit(RectF rect, long eventId) {
            this.rect = new RectF(rect);
            this.eventId = eventId;
        }
    }
}
