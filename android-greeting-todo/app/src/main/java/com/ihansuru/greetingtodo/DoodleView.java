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
    interface ChangeListener { void onChanged(String serialized); }\n    interface InteractionListener { void onInteraction(boolean active); }

    private static final class Stroke {
        int color;
        float width;
        final ArrayList<Float> points = new ArrayList<>();
    }

    private final ArrayList<Stroke> strokes = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ChangeListener listener;\n    private InteractionListener interactionListener;
    private Stroke active;
    private int penColor = Color.rgb(48, 54, 67);
    private float penWidth = 5f;
    private boolean erasing;

    DoodleView(Context context) { super(context); init(); }
    DoodleView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        setBackgroundColor(Color.WHITE);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setFocusable(true);
    }

    void setChangeListener(ChangeListener value) { listener = value; }\n    void setInteractionListener(InteractionListener value) { interactionListener = value; }

    void setPenColor(int color) {
        penColor = color;
        erasing = false;
    }

    void setPenWidth(float dp) {
        penWidth = Math.max(2f, Math.min(18f, dp));
        erasing = false;
    }

    void setEraser(boolean value) { erasing = value; }

    void undo() {
        if (!strokes.isEmpty()) {
            strokes.remove(strokes.size() - 1);
            invalidate();
            notifyChange();
        }
    }

    void clearAll() {
        strokes.clear();
        invalidate();
        notifyChange();
    }

    void setSerialized(String raw) {
        strokes.clear();
        if (raw == null || raw.isEmpty()) raw = "[]";
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Stroke s = new Stroke();
                s.color = o.optInt("c", Color.rgb(48, 54, 67));
                s.width = (float) o.optDouble("w", 5.0);
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
        for (Stroke s : strokes) drawStroke(canvas, s);
        if (active != null) drawStroke(canvas, active);
    }

    private void drawStroke(Canvas canvas, Stroke s) {
        if (s.points.size() < 2) return;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(dp(s.width));
        paint.setColor(s.color);

        Path path = new Path();
        float x0 = s.points.get(0) * getWidth();
        float y0 = s.points.get(1) * getHeight();
        path.moveTo(x0, y0);
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
                getParent().requestDisallowInterceptTouchEvent(true);\n                if (interactionListener != null) interactionListener.onInteraction(true);
                active = new Stroke();
                active.color = erasing ? Color.WHITE : penColor;
                active.width = erasing ? Math.max(18f, penWidth * 3f) : penWidth;
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
                getParent().requestDisallowInterceptTouchEvent(false);\n                if (interactionListener != null) interactionListener.onInteraction(false);
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

    private float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
