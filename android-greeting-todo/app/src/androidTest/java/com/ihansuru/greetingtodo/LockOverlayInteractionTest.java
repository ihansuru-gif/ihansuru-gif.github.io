package com.ihansuru.greetingtodo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileOutputStream;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LockOverlayInteractionTest {
    private Context context;

    @Before
    public void prepare() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("greeting_todo_settings", Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences("greeting_memo_store", Context.MODE_PRIVATE)
                .edit().clear().commit();

        Prefs.resetLayout(context);
        Prefs.setDuration(context, 10_000L);
        Prefs.setEnabled(context, false);
        Prefs.setTodoTabEnabled(context, true);
        Prefs.setCalendarEnabled(context, true);
        Prefs.setMemoEnabled(context, true);
        Prefs.setImageTabEnabled(context, true);
        Prefs.setTodoExpanded(context, true);
        Prefs.setCalendarExpanded(context, true);
        Prefs.setMemoExpanded(context, true);
        Prefs.setImageExpanded(context, true);

        Bitmap bitmap = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.rgb(220, 230, 240));
        try (FileOutputStream out = new FileOutputStream(ImageStore.file(context))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        bitmap.recycle();
    }

    @Test
    public void allCardsMoveAndResizeWithIndependentHandles() {
        try (ActivityScenario<LockOverlayActivity> scenario =
                     ActivityScenario.launch(LockOverlayActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> {
                View root = activity.getWindow().getDecorView();
                for (String label : new String[]{"투두", "일정", "메모", "이미지"}) {
                    View move = findByDescription(root, label + " 카드 이동 손잡이");
                    View resize = findByDescription(root, label + " 카드 크기 조절 손잡이");
                    assertNotNull(label + " move handle", move);
                    assertNotNull(label + " resize handle", resize);

                    View frame = (View) move.getParent();
                    float beforeX = frame.getX();
                    float beforeY = frame.getY();
                    float dx = beforeX > 45 ? -36 : 36;
                    float dy = beforeY > 45 ? -32 : 32;
                    drag(move, dx, dy);
                    assertTrue(label + " should move horizontally",
                            Math.abs(frame.getX() - beforeX) > 2f);
                    assertTrue(label + " should move vertically",
                            Math.abs(frame.getY() - beforeY) > 2f);

                    int beforeW = frame.getWidth();
                    int beforeH = frame.getHeight();
                    drag(resize, 24, 28);
                    assertTrue(label + " should resize",
                            frame.getWidth() != beforeW || frame.getHeight() != beforeH);

                    float movedX = frame.getX();
                    float movedY = frame.getY();
                    drag(move, -18, -18);
                    assertTrue(label + " should still move after resize",
                            Math.abs(frame.getX() - movedX) > 1f
                                    || Math.abs(frame.getY() - movedY) > 1f);
                }
            });
        }
    }

    @Test
    public void bodyInteractionsDoNotMoveMemoOrCalendarCards() {
        try (ActivityScenario<LockOverlayActivity> scenario =
                     ActivityScenario.launch(LockOverlayActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> {
                View root = activity.getWindow().getDecorView();

                View memoMove = findByDescription(root, "메모 카드 이동 손잡이");
                assertNotNull(memoMove);
                View memoFrame = (View) memoMove.getParent();
                float memoX = memoFrame.getX();
                float memoY = memoFrame.getY();

                View textTab = findText(root, "T 텍스트");
                assertNotNull(textTab);
                textTab.performClick();

                EditText memoTitle = (EditText) findEditByHint(root, "메모 제목");
                assertNotNull(memoTitle);
                tap(memoTitle);
                assertEquals(memoX, memoFrame.getX(), 1f);
                assertEquals(memoY, memoFrame.getY(), 1f);

                View drawTab = findText(root, "✎ 낙서");
                assertNotNull(drawTab);
                drawTab.performClick();
                View doodle = findByClass(root, DoodleView.class);
                assertNotNull(doodle);
                drag(doodle, 40, 32);
                assertEquals(memoX, memoFrame.getX(), 1f);
                assertEquals(memoY, memoFrame.getY(), 1f);

                View calendarMove = findByDescription(root, "일정 카드 이동 손잡이");
                assertNotNull(calendarMove);
                View calendarFrame = (View) calendarMove.getParent();
                float calX = calendarFrame.getX();
                float calY = calendarFrame.getY();

                View grid = findByClass(root, CalendarGridView.class);
                assertNotNull(grid);
                cancelDrag(grid, 90, 24);
                assertEquals(calX, calendarFrame.getX(), 1f);
                assertEquals(calY, calendarFrame.getY(), 1f);
            });
        }
    }

    @Test
    public void fourTabsExistAndWindowIsTransparentWithoutDim() {
        try (ActivityScenario<LockOverlayActivity> scenario =
                     ActivityScenario.launch(LockOverlayActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> {
                View root = activity.getWindow().getDecorView();
                assertNotNull(findTextContaining(root, "투두"));
                assertNotNull(findTextContaining(root, "일정"));
                assertNotNull(findTextContaining(root, "메모"));
                assertNotNull(findTextContaining(root, "이미지"));

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
    }

    @Test
    public void collapseOnlyCollapsesItsOwnCard() throws Exception {
        try (ActivityScenario<LockOverlayActivity> scenario =
                     ActivityScenario.launch(LockOverlayActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            final View[] todoFrame = new View[1];
            final View[] memoFrame = new View[1];
            scenario.onActivity(activity -> {
                View root = activity.getWindow().getDecorView();
                View todoCollapse = findByDescription(root, "투두 카드 접기");
                View memoMove = findByDescription(root, "메모 카드 이동 손잡이");
                assertNotNull(todoCollapse);
                assertNotNull(memoMove);
                todoFrame[0] = (View) todoCollapse.getParent();
                memoFrame[0] = (View) memoMove.getParent();
                todoCollapse.performClick();
            });

            Thread.sleep(320L);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> {
                assertEquals(View.GONE, todoFrame[0].getVisibility());
                assertEquals(View.VISIBLE, memoFrame[0].getVisibility());
            });
        }
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

    private static View findTextContaining(View root, String text) {
        if (root instanceof TextView
                && ((TextView) root).getText() != null
                && ((TextView) root).getText().toString().contains(text)) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findTextContaining(group.getChildAt(i), text);
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
