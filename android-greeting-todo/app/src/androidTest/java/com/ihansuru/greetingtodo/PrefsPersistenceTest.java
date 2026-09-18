package com.ihansuru.greetingtodo;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PrefsPersistenceTest {
    private Context context;

    @Before
    public void prepare() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("greeting_todo_settings", Context.MODE_PRIVATE)
                .edit().clear().commit();
        Prefs.resetLayout(context);
    }

    @Test
    public void tabVisibilityAndOrderPersist() {
        Prefs.setTodoTabEnabled(context, true);
        Prefs.setCalendarEnabled(context, false);
        Prefs.setMemoEnabled(context, true);
        Prefs.setImageTabEnabled(context, false);

        Prefs.moveTab(context, "image", -1);
        Prefs.moveTab(context, "todo", 1);

        assertTrue(Prefs.todoTabEnabled(context));
        assertFalse(Prefs.calendarEnabled(context));
        assertTrue(Prefs.memoEnabled(context));
        assertFalse(Prefs.imageTabEnabled(context));
        assertEquals(Arrays.asList("calendar", "todo", "image", "memo"), Prefs.tabOrder(context));
    }

    @Test
    public void cardPositionsAndSizesPersist() {
        Prefs.setTodoPosition(context, .18f, .29f);
        Prefs.setTodoSize(context, 73, 44);
        Prefs.setCalendarPosition(context, .41f, .36f);
        Prefs.setCalendarSize(context, 84, 61);
        Prefs.setMemoPosition(context, .62f, .55f);
        Prefs.setMemoSize(context, 76, 52);
        Prefs.setImagePosition(context, .71f, .68f);
        Prefs.setImageSize(context, 126);

        assertEquals(.18f, Prefs.todoX(context), .001f);
        assertEquals(.29f, Prefs.todoY(context), .001f);
        assertEquals(73, Prefs.todoWidth(context));
        assertEquals(44, Prefs.todoHeight(context));

        assertEquals(.41f, Prefs.calendarX(context), .001f);
        assertEquals(.36f, Prefs.calendarY(context), .001f);
        assertEquals(84, Prefs.calendarWidth(context));
        assertEquals(61, Prefs.calendarHeight(context));

        assertEquals(.62f, Prefs.memoX(context), .001f);
        assertEquals(.55f, Prefs.memoY(context), .001f);
        assertEquals(76, Prefs.memoWidth(context));
        assertEquals(52, Prefs.memoHeight(context));

        assertEquals(.71f, Prefs.imageX(context), .001f);
        assertEquals(.68f, Prefs.imageY(context), .001f);
        assertEquals(126, Prefs.imageSize(context));
    }

    @Test
    public void displayDurationRemainsWithinUserRange() {
        Prefs.setDuration(context, 250L);
        assertEquals(500L, Prefs.duration(context));

        Prefs.setDuration(context, 20_000L);
        assertEquals(10_000L, Prefs.duration(context));

        Prefs.setDuration(context, 4300L);
        assertEquals(4300L, Prefs.duration(context));
    }
}
