package com.example.ragkb.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LruCacheTest {

    @Test
    void evicts_least_recently_used_entry() {
        LruCache<String, Integer> cache = new LruCache<>(2);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.get("a");            // a 变成最近使用，b 成了最久未用
        cache.put("c", 3);         // 挤掉 b

        assertEquals(2, cache.size());
        assertNull(cache.get("b"));
        assertEquals(1, cache.get("a"));
        assertEquals(3, cache.get("c"));
    }

    @Test
    void clear_empties_cache() {
        LruCache<String, Integer> cache = new LruCache<>(4);
        cache.put("a", 1);

        cache.clear();

        assertEquals(0, cache.size());
        assertNull(cache.get("a"));
    }

    @Test
    void rejects_non_positive_capacity() {
        assertThrows(IllegalArgumentException.class, () -> new LruCache<>(0));
    }
}