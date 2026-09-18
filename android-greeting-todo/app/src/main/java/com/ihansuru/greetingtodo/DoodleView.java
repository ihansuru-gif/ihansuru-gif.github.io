package com.ihansuru.greetingtodo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

final class DoodleView extends View {
    interface ChangeListener { void onChanged(String serialized); }
    interface InteractionListener { void onInteraction(boolean active); }

    static final int TOOL_PEN = 0;
    static final int TOOL_PENCIL = 1;
    static final int TOOL_HIGHLIGHTER = 2;
    static final int TOOL_ERASER = 3;

    static final int PAPER_PLAIN = 0;
    static final int PAPER_LINE = 1;
    static final int PAPER_GRID = 2;

    private static final class Stroke {
        int color;
        float width;
        int tool;
        final ArrayList<Float> points = new ArrayList<>();
    }

    private final ArrayList<Stroke> strokes = new ArrayList<>();
    private final ArrayList<Stroke> redo = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paperPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ChangeListener listener;
    private InteractionListener interactionListener;
    private Stroke active;
    private int penColor = Color.rgb(48, 54, 67);
    private int canvasColor = Color.WHITE;
    private float penWidth = 5f;
    private int tool = TOOL_PEN;
    private int paperPattern = PAPER_PLAIN;

    DoodleView(Context context) { super(context); init(); }
    DoodleView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setFocusable(true);
    }

    void setChangeListener(ChangeListener value) { listener = value; }
    void setInteractionListener(InteractionListener value) { interactionListener = value; }

    void setPenColor(int color) {
        penColor = color;
        if (tool == TOOL_ERASER) tool = TOOL_PEN;
    }

    void setPenWidth(float dp) {
        penWidth = Math.max(2f, Math.min(18f, dp));
        if (tool == TOOL_ERASER) tool = TOOL_PEN;
    }

    void setTool(int value) {
        tool = Math.max(TOOL_PEN, Math.min(TOOL_ERASER, value));
    }

    void setEraser(boolean value) {
        tool = value ? TOOL_ERASER : TOOL_PEN;
    }

    void setCanvasColor(int color) {
        canvasColor = color;
        invalidate();
    }

    void setPaperPattern(int pattern) {
        paperPattern = Math.max(PAPER_PLAIN, Math.min(PAPER_GRID, pattern));
        invalidate();
    }

    void undo() {
        if (!strokes.isEmpty()) {
            redo.add(strokes.remove(strokes.size() - 1));
            invalidate();
            notifyChange();
        }
    }

    void redo() {
        if (!redo.isEmpty()) {
            strokes.add(redo.remove(redo.size() - 1));
            invalidate();
            notifyChange();
        }
    }

    void clearAll() {
        if (!strokes.isEmpty()) {
            redo.addAll(strokes);
            strokes.clear();
            invalidate();
            notifyChange();
        }
    }

    void setSerialized(String raw) {
        strokes.clear();
        redo.clear();
        if (raw == null || raw.isEmpty()) raw = "[]";
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Stroke s = new Stroke();
                s.color = o.optInt("c", Color.rgb(48, 54, 67));
                s.width = (float) o.optDouble("w", 5.0);
                s.tool = o.has("t") ? o.optInt("t", TOOL_PEN)
                        : (o.optBoolean("e", false) ? TOOL_ERASER : TOOL_PEN);
                JSONArray p = o.optJSONArray("p");
                if (p == null) continue;
                for (int j = 0; j < p.length(); j++) {
                    s.points.add((float) p.optDouble(j, 0.0));
                }
                if (s.points.size() >= 2) strokes.add(s);
            }
        } catch (JSONException ignored) {}
        invalidate();
    }

    String serialize() {
        JSONArray arr = new JSONArray();
        for (Stroke s : strokes) {
            JSONObject o = new JSONObject();
            try {
                o.put("c", s.color);
                o.put("w", s.width);
                o.put("t", s.tool);
                JSONArray p = new JSONArray();
                for (Float v : s.points) p.put(v);
                o.put("p", p);
                arr.put(o);
            } catch (JSONException ignored) {}
        }
        return arr.toString();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(canvasColor);
        drawPaper(canvas);
        for (Stroke s : strokes) drawStroke(canvas, s);
        if (active != null) drawStroke(canvas, active);
    }

    private void drawPaper(Canvas canvas) {
        if (paperPattern == PAPER_PLAIN) return;
        paperPaint.setStyle(Paint.Style.STROKE);
        paperPaint.setStrokeWidth(dp(.8f));
        paperPaint.setColor(Color.argb(45, 90, 100, 120));
        float step = dp(24);
        if (paperPattern == PAPER_LINE || paperPattern == PAPER_GRID) {
            for (float y = step; y < getHeight(); y += step) {
                canvas.drawLine(0, y, getWidth(), y, paperPaint);
            }
        }
        if (paperPattern == PAPER_GRID) {
            for (float x = step; x < getWidth(); x += step) {
                canvas.drawLine(x, 0, x, getHeight(), paperPaint);
            }
        }
    }

    private void drawStroke(Canvas canvas, Stroke s) {
        if (s.points.size() < 2) return;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        float width = s.width;
        int color = s.color;
        if (s.tool == TOOL_PENCIL) {
            width = Math.max(1.4f, s.width * .72f);
            color = withAlpha(s.color, 185);
        } else if (s.tool == TOOL_HIGHLIGHTER) {
            width = Math.max(10f, s.width * 2.8f);
            color = withAlpha(s.color, 82);
        } else if (s.tool == TOOL_ERASER) {
            width = Math.max(18f, s.width * 3.2f);
            color = canvasColor;
        }
        paint.setStrokeWidth(dp(width));
        paint.setColor(color);

        Path path = new Path();
        path.moveTo(s.points.get(0) * getWidth(), s.points.get(1) * getHeight());
        for (int i = 2; i + 1 < s.points.size(); i += 2) {
            path.lineTo(s.points.get(i) * getWidth(), s.points.get(i + 1) * getHeight());
        }
        canvas.drawPath(path, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (getWidth() <= 0 || getHeight() <= 0) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                if (interactionListener != null) interactionListener.onInteraction(true);
                redo.clear();
                active = new Stroke();
                active.color = penColor;
                active.tool = tool;
                active.width = penWidth;
                addPoint(active, event.getX(), event.getY());
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (active != null) {
                    for (int i = 0; i < event.getHistorySize(); i++) {
                        addPoint(active, event.getHistoricalX(i), event.getHistoricalY(i));
                    }
                    addPoint(active, event.getX(), event.getY());
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (active != null) {
                    addPoint(active, event.getX(), event.getY());
                    strokes.add(active);
                    active = null;
                    invalidate();
                    notifyChange();
                }
                getParent().requestDisallowInterceptTouchEvent(false);
                if (interactionListener != null) interactionListener.onInteraction(false);
                return true;
            default:
                return true;
        }
    }

    private void addPoint(Stroke stroke, float x, float y) {
        stroke.points.add(clamp(x / Math.max(1f, getWidth())));
        stroke.points.add(clamp(y / Math.max(1f, getHeight())));
    }

    private void notifyChange() {
        if (listener != null) listener.onChanged(serialize());
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
