package com.ihansuru.greetingtodo;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class TabOrderTest {
    @Test public void normalizeAlwaysContainsFourUniqueTabs() {
        List<String> order = TabOrder.normalize("memo,image,memo,unknown");
        assertEquals(4, order.size());
        assertTrue(order.containsAll(Arrays.asList("todo","calendar","memo","image")));
    }

    @Test public void todoCanMoveDownAndBackUp() {
        List<String> order = TabOrder.normalize("todo,calendar,memo,image");
        order = TabOrder.move(order, "todo", 1);
        assertEquals("calendar", order.get(0));
        assertEquals("todo", order.get(1));

        order = TabOrder.move(order, "todo", -1);
        assertEquals("todo", order.get(0));
    }

    @Test public void requestedPermutationsAreRepresentable() {
        assertEquals(Arrays.asList("todo","calendar","memo","image"),
                TabOrder.normalize("todo,calendar,memo,image"));
        assertEquals(Arrays.asList("image","todo","memo","calendar"),
                TabOrder.normalize("image,todo,memo,calendar"));
        assertEquals(Arrays.asList("memo","image","calendar","todo"),
                TabOrder.normalize("memo,image,calendar,todo"));
    }
}
