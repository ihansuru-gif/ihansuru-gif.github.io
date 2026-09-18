package com.ihansuru.greetingtodo;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LockOverlayInteractionTest {
    private Instrumentation instrumentation;
    private Context context;
    private MainActivity activity;

    @Before
    public void prepare() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context = instrumentation.getTargetContext();

        context.getSharedPreferences("greeting_todo_settings", Context.MODE_PRIVATE)
                .edit().clear().commit();
        Prefs.resetLayout(context);
        Prefs.setEnabled(context, false);

        Intent intent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        Activity started = instrumentation.startActivitySync(intent);
        assertTrue(started instanceof MainActivity);
        activity = (MainActivity) started;

        instrumentation.runOnMainSync(this::installHarness);
    }

    @After
    public void cleanup() {
        if (activity != null) {
            instrumentation.runOnMainSync(() -> {
                if (!activity.isFinishing()) activity.finish();
            });
        }
    }

    @Test
    public void allCardsMoveResizeAndThenMoveAgain() {
        for (String label : new String[]{"투두", "일정", "메모", "이미지"}) {
            final AtomicReference<View> frameRef = new AtomicReference<>();
            final float[] beforePos = new float[2];

            runMain(() -> {
                View root = activity.getWindow().getDecorView();
                View move = findByDescription(root, label + " 카드 이동 손잡이");
                assertNotNull(label + " move handle", move);
                View frame = (View) move.getParent();
                frameRef.set(frame);
                beforePos[0] = frame.getX();
                beforePos[1] = frame.getY();
                drag(move, 36, 28);
            });

            runMain(() -> {
                View frame = frameRef.get();
                assertTrue(label + " x moved", frame.getX() > beforePos[0] + 2f);
                assertTrue(label + " y moved", frame.getY() > beforePos[1] + 2f);
            });

            final int[] beforeSize = new int[2];
            runMain(() -> {
                View resize = findByDescription(
                        activity.getWindow().getDecorView(),
                        label + " 카드 크기 조절 손잡이");
                assertNotNull(label + " resize handle", resize);
                View frame = (View) resize.getParent();
                beforeSize[0] = frame.getLayoutParams().width;
                beforeSize[1] = frame.getLayoutParams().height;
                drag(resize, 48, 44);
            });

            runMain(() -> {
                View frame = frameRef.get();
                assertTrue(label + " resized",
                        frame.getLayoutParams().width != beforeSize[0]
                                || frame.getLayoutParams().height != beforeSize[1]);
            });

            final float[] secondPos = new float[2];
            runMain(() -> {
                View frame = frameRef.get();
                secondPos[0] = frame.getX();
                secondPos[1] = frame.getY();
                View move = findByDescription(
                        activity.getWindow().getDecorView(),
                        label + " 카드 이동 손잡이");
                drag(move, -20, -18);
            });

            runMain(() -> {
                View frame = frameRef.get();
                assertTrue(label + " moves after resize",
                        Math.abs(frame.getX() - secondPos[0]) > 1f
                                || Math.abs(frame.getY() - secondPos[1]) > 1f);
            });
        }
    }

    @Test
    public void bodyTouchDoesNotMoveCard() {
        final float[] memoPos = new float[2];

        runMain(() -> {
            View root = activity.getWindow().getDecorView();
            View move = findByDescription(root, "메모 카드 이동 손잡이");
            assertNotNull(move);
            View frame = (View) move.getParent();
            memoPos[0] = frame.getX();
            memoPos[1] = frame.getY();

            EditText body = (EditText) findByDescription(root, "메모 테스트 입력");
            assertNotNull(body);
            tap(body);
        });

        runMain(() -> {
            View frame = (View) findByDescription(
                    activity.getWindow().getDecorView(),
                    "메모 카드 이동 손잡이").getParent();
            assertEquals(memoPos[0], frame.getX(), 1f);
            assertEquals(memoPos[1], frame.getY(), 1f);
        });
    }

    @Test
    public void collapseOnlyAffectsSelectedCard() {
        final AtomicReference<View> todo = new AtomicReference<>();
        final AtomicReference<View> memo = new AtomicReference<>();

        runMain(() -> {
            View root = activity.getWindow().getDecorView();
            View todoCollapse = findByDescription(root, "투두 카드 접기");
            View memoMove = findByDescription(root, "메모 카드 이동 손잡이");
            assertNotNull(todoCollapse);
            assertNotNull(memoMove);
            todo.set((View) todoCollapse.getParent());
            memo.set((View) memoMove.getParent());
            todoCollapse.performClick();
        });

        runMain(() -> {
            assertEquals(View.GONE, todo.get().getVisibility());
            assertEquals(View.VISIBLE, memo.get().getVisibility());
        });
    }

    @Test
    public void transparentWindowStyleHasNoDim() {
        runMain(() -> {
            OverlayWindowStyle.apply(activity);
            int flags = activity.getWindow().getAttributes().flags;
            assertEquals(0, flags & WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            assertEquals(0f, activity.getWindow().getAttributes().dimAmount, .001f);

            if (activity.getWindow().getDecorView().getBackground() instanceof ColorDrawable) {
                ColorDrawable bg =
                        (ColorDrawable) activity.getWindow().getDecorView().getBackground();
                assertEquals(0, Color.alpha(bg.getColor()));
            }
        });
    }

    private void installHarness() {
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Color.TRANSPARENT);
        activity.setContentView(root);

        addCard(root, "투두", OverlayCardFrame.KIND_TODO, 720, 620, 40, 60, false);
        addCard(root, "일정", OverlayCardFrame.KIND_CALENDAR, 850, 1000, 90, 180, false);
        addCard(root, "메모", OverlayCardFrame.KIND_MEMO, 800, 820, 130, 300, true);
        addCard(root, "이미지", OverlayCardFrame.KIND_IMAGE, 470, 330, 180, 440, false);
    }

    private void addCard(FrameLayout root, String label, int kind,
                         int width, int height, float x, float y, boolean editBody) {
        OverlayCardFrame frame = new OverlayCardFrame(activity, kind);
        frame.setGestureListener(new OverlayCardFrame.GestureListener() {
            @Override public void onGestureStart(int ignored) {}
            @Override public void onGestureEnd(int ignored, int startW, int startH) {}
        });
        frame.enableCollapse(() -> frame.setVisibility(View.GONE));

        if (editBody) {
            EditText body = new EditText(activity);
            body.setHint("본문");
            body.setContentDescription(label + " 테스트 입력");
            frame.addView(body, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            TextView body = new TextView(activity);
            body.setText(label + " 본문");
            body.setGravity(android.view.Gravity.CENTER);
            body.setBackgroundColor(Color.argb(235, 250, 250, 252));
            frame.addView(body, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }

        root.addView(frame, new FrameLayout.LayoutParams(width, height));
        frame.setX(x);
        frame.setY(y);
    }

    private void runMain(Runnable action) {
        final AtomicReference<Throwable> error = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            }
        });
        if (error.get() != null) {
            if (error.get() instanceof AssertionError) throw (AssertionError) error.get();
            throw new RuntimeException(error.get());
        }
    }

    private static void drag(View view, float dx, float dy) {
        long now = SystemClock.uptimeMillis();
        float sx = Math.max(5f, view.getWidth() / 2f);
        float sy = Math.max(5f, view.getHeight() / 2f);
        view.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, sx, sy, 0));
        view.dispatchTouchEvent(MotionEvent.obtain(now, now + 16, MotionEvent.ACTION_MOVE,
                sx + dx, sy + dy, 0));
        view.dispatchTouchEvent(MotionEvent.obtain(now, now + 32, MotionEvent.ACTION_UP,
                sx + dx, sy + dy, 0));
    }

    private static void tap(View view) {
        long now = SystemClock.uptimeMillis();
        float x = Math.max(5f, view.getWidth() / 2f);
        float y = Math.max(5f, view.getHeight() / 2f);
        view.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0));
        view.dispatchTouchEvent(MotionEvent.obtain(now, now + 16, MotionEvent.ACTION_UP, x, y, 0));
    }

    private static View findByDescription(View root, String description) {
        CharSequence contentDescription = root.getContentDescription();
        if (contentDescription != null && description.contentEquals(contentDescription)) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findByDescription(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }
}
