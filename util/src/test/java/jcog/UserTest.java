package jcog;

import jcog.data.list.FasterList;
import jcog.math.Longerval;
import org.apache.lucene.document.DoubleRange;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Maps;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.lang.Float.NEGATIVE_INFINITY;
import static java.lang.Float.POSITIVE_INFINITY;
import static jcog.User.BOUNDS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UserTest {

    private static User testPutGet(Object obj) {
        User u = new User();
        u.put("key", obj);
        List l = new FasterList();
        u.get("key", l::add);
        assertEquals(1, l.size());
        if (obj instanceof byte[]) {
            assertArrayEquals((byte[]) obj, (byte[]) l.get(0));
        } else {
            assertEquals(obj, l.get(0));
        }
        return u;
    }

    @Test
    void testPutGetString() {
        testPutGet("value");
    }

    @Test
    void testPutGetByteArray() {
        testPutGet(new byte[]{1, 2, 3, 4, 5});
    }

    @Test
    void testPutGetSerialized() {
        testPutGet(Maps.mutable.of("x", 6, "y", Lists.mutable.of("z", "z")));
    }

    @Test
    void testTimeIndex() {
        User u = new User();
        u.put("x", new MyEvent("x", 0, 4));
        u.put("y", new MyEvent("y", 4, 8));


        u.get(DoubleRange.newIntersectsQuery(BOUNDS,
                new double[]{-1, NEGATIVE_INFINITY, NEGATIVE_INFINITY, NEGATIVE_INFINITY},
                new double[]{3, POSITIVE_INFINITY, POSITIVE_INFINITY, POSITIVE_INFINITY}), 8, (d) -> {
            System.out.println(d);
            return true;
        });
    }

    static class MyEvent extends Longerval {

        private final String name;

        MyEvent(String name, long a, long b) {
            super(a, b);
            this.name = name;
        }
    }
}