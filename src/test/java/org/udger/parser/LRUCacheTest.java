package org.udger.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class LRUCacheTest {

    private UdgerUaResult result(String ua) {
        return new UdgerUaResult(ua);
    }

    @Test
    public void setCapacityToZeroEvictsAllWithoutNpe() {
        LRUCache cache = new LRUCache(3);
        cache.put("a", result("a"));
        cache.put("b", result("b"));
        cache.put("c", result("c"));
        cache.setCapacity(0);
        assertEquals(0, cache.getCapacity());
        assertNull(cache.get("a"));
        assertNull(cache.get("b"));
        assertNull(cache.get("c"));
    }

    @Test
    public void setCapacityShrinksToSubset() {
        LRUCache cache = new LRUCache(3);
        cache.put("a", result("a"));
        cache.put("b", result("b"));
        cache.put("c", result("c"));
        cache.setCapacity(1);
        assertEquals(1, cache.getCapacity());
        assertNull(cache.get("a"));
        assertNull(cache.get("b"));
        assertEquals("c", cache.get("c").getUaString());
    }

    @Test
    public void setCapacityToOneWhenOneEntryNoNpe() {
        LRUCache cache = new LRUCache(2);
        cache.put("a", result("a"));
        cache.setCapacity(0);
        assertEquals(0, cache.getCapacity());
        assertNull(cache.get("a"));
    }

    @Test
    public void putWithCapacityOneDoesNotNpeOnSecondInsert() {
        LRUCache cache = new LRUCache(1);
        cache.put("a", result("a"));
        cache.put("b", result("b"));
        assertNull(cache.get("a"));
        assertEquals("b", cache.get("b").getUaString());
    }

    @Test
    public void putWithCapacityZeroEvictsImmediately() {
        LRUCache cache = new LRUCache(0);
        cache.put("a", result("a"));
        assertNull(cache.get("a"));
    }

    @Test
    public void lruOrderPreservedAfterSetCapacity() {
        LRUCache cache = new LRUCache(3);
        cache.put("a", result("a"));
        cache.put("b", result("b"));
        cache.put("c", result("c"));
        cache.get("a");
        cache.setCapacity(2);
        assertNull(cache.get("b"));
        assertEquals("a", cache.get("a").getUaString());
        assertEquals("c", cache.get("c").getUaString());
    }
}
