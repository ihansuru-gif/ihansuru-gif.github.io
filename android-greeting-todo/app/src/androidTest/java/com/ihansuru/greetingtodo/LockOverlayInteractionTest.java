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
import android.widget.ImageView;
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
        context.getSharedPreferences("greeting_memo_store", Context.MODE_PRIVATE)
                .edit().clear().commit();
        Prefs.resetLayout(context);
        Prefs.setEnabled(context, false);

        Intent intent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        Activity started = instrumentation.startActivitySync(intent);
        assertTrue(started instanceof MainActivity);
        activity = (MainActivity) started;
        instrumentation.waitForIdleSync();

        onMain(this::installGestureHarness);
    }

    @After
    public void cleanup() {
        if (activity != null) {
            instrumentation.runOnMainSync(() -> {
                if (!activity.isFinishing()) activity.finish();
            });
            instrumentation.waitForIdleSync();
        }
    }

    @Test
    public void allCardsMoveAndResizeWithIndependentHandles() {
        for (String label : new String[]{"투두", "일정", "메모", "이미지"}) {
            AtomicReference<View> frameRef = new AtomicReference<>();
            final float[] start = new float[2];

            onMain(() -> {
                View root = activity.getWindow().getDecorView();
                View move = findByDescription(root, label + " 카드 이동 손잡이");
                assertNotNull(label + " move handle", move);
                View frame = (View) move.getParent();
                frameRef.set(frame);
                start[0] = frame.getX();
                start[1] = frame.getY();
                float dx = start[0] > 45 ? -36 : 36;
                float dy = start[1] > 45 ? -32 : 32;
                drag(move, dx, dy);
            });

            onMain(() -> {
                View frame = frameRef.get();
                assertTrue(label + " should move horizontally",
                        Math.abs(frame.getX() - start[0]) > 2f);
                assertTrue(label + " should move vertically",
                        Math.abs(frame.getY() - start[1]) > 2f);
            });

            final int[] beforeSize = new int[2];
            onMain(() -> {
                View root = activity.getWindow().getDecorView();
                View resize = findByDescription(root, label + " 카드 크기 조절 손잡이");
                assertNotNull(label + " resize handle", resize);
                View frame = (View) resize.getParent();
                beforeSize[0] = frame.getLayoutParams().width;
                beforeSize[1] = frame.getLayoutParams().height;
                drag(resize, 42, 46);
            });

            onMain(() -> {
                View frame = frameRef.get();
                assertTrue(label + " should resize",
                        frame.getLayoutParams().width != beforeSize[0]
                                || frame.getLayoutParams().height != beforeSize[1]);
            });

            final float[] afterResizePos = new float[2];
            onMain(() -> {
                View frame = frameRef.get();
                afterResizePos[0] = frame.getX();
                afterResizePos[1] = frame.getY();
                View move = findByDescription(
                        activity.getWindow().getDecorView(), label + " 카드 이동 손잡이");
                drag(move, -22, -20);
            });

            onMain(() -> {
                View frame = frameRef.get();
                assertTrue(label + " should still move after resize",
                        Math.abs(frame.getX() - afterResizePos[0]) > 1f
                                || Math.abs(frame.getY() - afterResizePos[1]) > 1f);
            });
        }
    }

    @Test
    public void bodyInteractionsDoNotMoveMemoOrCalendarCards() {
        final float[] memoPos = new float[2];
        onMain(() -> {
            View root = activity.getWindow().getDecorView();
            View memoMove = findByDescription(root, "메모 카드 이동 손잡이");
            assertNotNull(memoMove);
            View memoFrame = (View) memoMove.getParent();
            memoPos[0] = memoFrame.getX();
            memoPos[1] = memoFrame.getY();

            View textTab = findText(root, "T 텍스트");
            assertNotNull(textTab);
            textTab.performClick();

            EditText memoTitle = (EditText) findEditByHint(root, "메모 제목");
            assertNotNull(memoTitle);
            tap(memoTitle);
        });

        onMain(() -> {
            View memoFrame = (View) findByDescription(
                    activity.getWindow().getDecorView(), "메모 카드 이동 손잡이").getParent();
            assertEquals(memoPos[0], memoFrame.getX(), 1f);
            assertEquals(memoPos[1], memoFrame.getY(), 1f);

            View drawTab = findText(activity.getWindow().getDecorView(), "✎ 낙서");
            assertNotNull(drawTab);
            drawTab.performClick();
            View doodle = findByClass(activity.getWindow().getDecorView(), DoodleView.class);
            assertNotNull(doodle);
            drag(doodle, 40, 32);
        });

        onMain(() -> {
            View memoFrame = (View) findByDescription(
                    activity.getWindow().getDecorView(), "메모 카드 이동 손잡이").getParent();
            assertEquals(memoPos[0], memoFrame.getX(), 1f);
            assertEquals(memoPos[1], memoFrame.getY(), 1f);
        });

        final float[] calPos = new float[2];
        onMain(() -> {
            View root = activity.getWindow().getDecorView();
            View calendarMove = findByDescription(root, "일정 카드 이동 손잡이");
            assertNotNull(calendarMove);
            View calendarFrame = (View) calendarMove.getParent();
            calPos[0] = calendarFrame.getX();
            calPos[1] = calendarFrame.getY();

            View grid = findByClass(root, CalendarGridView.class);
            assertNotNull(grid);
            cancelDrag(grid, 90, 24);
        });

        onMain(() -> {
            View calendarFrame = (View) findByDescription(
                    activity.getWindow().getDecorView(), "일정 카드 이동 손잡이").getParent();
            assertEquals(calPos[0], calendarFrame.getX(), 1f);
            assertEquals(calPos[1], calendarFrame.getY(), 1f);
        });
    }

    @Test
    public void sharedWindowStyleIsTransparentWithoutDim() {
        onMain(() -> {
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

    @Test
    public void collapseOnlyCollapsesItsOwnCard() {
        final AtomicReference<View> todoFrame = new AtomicReference<>();
        final AtomicReference<View> memoFrame = new AtomicReference<>();

        onMain(() -> {
            View root = activity.getWindow().getDecorView();
            View todoCollapse = findByDescription(root, "투두 카드 접기");
            View memoMove = findByDescription(root, "메모 카드 이동 손잡이");
            assertNotNull(todoCollapse);
            assertNotNull(memoMove);
            todoFrame.set((View) todoCollapse.getParent());
            memoFrame.set((View) memoMove.getParent());
            todoCollapse.performClick();
        });

        onMain(() -> {
            assertEquals(View.GONE, todoFrame.get().getVisibility());
            assertEquals(View.VISIBLE, memoFrame.get().getVisibility());
        });
    }

    private void installGestureHarness() {
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Color.TRANSPARENT);
        activity.setContentView(root);

        OverlayCardFrame todo = new OverlayCardFrame(activity, OverlayCardFrame.KIND_TODO);
        LockTodoWidget todoWidget = new LockTodoWidget(activity);
        todoWidget.setCallback(new LockTodoWidget.Callback() {
            @Override public void onComplete(int index) {}
            @Override public void onGear() {}
            @Override public void onAdd(String text) {}
            @Override public void onInteractionChanged(boolean active) {}
        });
        todo.addView(todoWidget, match());
        installCard(root, todo, 760, 700, 50, 70);

        OverlayCardFrame calendar =
                new OverlayCardFrame(activity, OverlayCardFrame.KIND_CALENDAR);
        CalendarBoardView calendarBoard = new CalendarBoardView(activity);
        calendarBoard.setCallback(new CalendarBoardView.Callback() {
            @Override public void onGear() {}
            @Override public void onInteractionChanged(boolean active) {}
            @Override public void onReminderPermissionNeeded() {}
        });
        calendar.addView(calendarBoard, match());
        installCard(root, calendar, 900, 1120, 100, 180);

        OverlayCardFrame memo = new OverlayCardFrame(activity, OverlayCardFrame.KIND_MEMO);
        MemoBoardView memoBoard = new MemoBoardView(activity);
        memoBoard.setCallback(new MemoBoardView.Callback() {
            @Override public void onGear() {}
            @Override public void onInteractionChanged(boolean active) {}
            @Override public void onPickImage() {}
            @Override public void onRequestAudioPermission() {}
        });
        memo.addView(memoBoard, match());
        installCard(root, memo, 850, 950, 120, 260);

        OverlayCardFrame image = new OverlayCardFrame(activity, OverlayCardFrame.KIND_IMAGE);
        ImageView imageView = new ImageView(activity);
        imageView.setBackgroundColor(Color.rgb(232, 238, 246));
        image.addView(imageView, match());
        installCard(root, image, 500, 360, 180, 360);
    }

    private void installCard(FrameLayout root, OverlayCardFrame frame,
                             int width, int height, float x, float y) {
        frame.setGestureListener(new OverlayCardFrame.GestureListener() {
            @Override public void onGestureStart(int kind) {}
            @Override public void onGestureEnd(int kind, int startW, int startH) {}
        });
        frame.enableCollapse(() -> frame.setVisibility(View.GONE));
        root.addView(frame, new FrameLayout.LayoutParams(width, height));
        frame.setX(x);
        frame.setY(y);
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void onMain(Runnable runnable) {
        AtomicReference<Throwable> error = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                error.set(t);
            }
        });
        if (error.get() != null) {
            if (error.get() instanceof AssertionError) throw (AssertionError) error.get();
            throw new RuntimeException(error.get());
        }
        instrumentation.waitForIdleSync();
    }

    private static void drag(View view, float dx, float dy) {
        long now = SystemClock.uptimeMillis();
        float sx = Math.max(4f, view.getWidth() / 2f);
        float sy = Math.max(4f, view.getHeight() / 2f);
        view.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, sx, sy, 0));
        view.dispatchTouchEvent(MotionEvent.obtain(now, now + 16, MotionEvent.ACTION_MOVE,
                sx + dx, sy + dy, 0));
        view.dispatchTouchEvent(MotionEvent.obtain(now, now + 32, MotionEvent.ACTION_UP,
                sx + dx, sy + dy, 0));
    }

    private static void cancelDrag(View view, float dx, float dy) {
        long now = SystemClock.uptimeMillis();
        float sx = Math.max(6f, view.getWidth() * .2f);
        float sy = Math.max(34f, view.getHeight() * .3f);
        view.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, sx, sy, 0));
        view.dispatchTouchEvent(MotionEvent.obtain(now, now + 16, MotionEvent.ACTION_MOVE,
                sx + dx, sy + dy, 0));
        view.dispatchTouchEvent(MotionEvent.obtain(now, now + 32, MotionEvent.ACTION_CANCEL,
                sx + dx, sy + dy, 0));
    }

    private static void tap(View view) {
        long now = SystemClock.uptimeMillis();
        float x = Math.max(4f, view.getWidth() / 2f);
        float y = Math.max(4f, view.getHeight() / 2f);
        view.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0));
        view.dispatchTouchEvent(MotionEvent.obtain(now, now + 16, MotionEvent.ACTION_UP, x, y, 0));
    }

    private static View findByDescription(View root, String description) {
        if (description.contentEquals(root.getContentDescription())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findByDescription(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static View findText(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static View findEditByHint(View root, String hint) {
        if (root instanceof EditText
                && ((EditText) root).getHint() != null
                && hint.contentEquals(((EditText) root).getHint())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findEditByHint(group.getChildAt(i), hint);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static View findByClass(View root, Class<?> cls) {
        if (cls.isInstance(root) && root.getVisibility() == View.VISIBLE) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findByClass(group.getChildAt(i), cls);
                if (found != null) return found;
            }
        }
        return null;
    }
}
