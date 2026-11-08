package com.example.ragkb.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 极简 LRU 缓存。
 *
 * <p>用 {@link LinkedHashMap} 的 accessOrder 模式实现：读一次就把条目移到队尾，
 * 队首永远是最久未用的那个。所有方法都加锁，可以直接被多个请求线程共用。
 *
 * <p>为什么不用 Caffeine：本项目只需要几十行，多一个依赖不值得。
 */
public final class LruCache<K, V> {

    private final int capacity;
    private final Map<K, V> store;

    public LruCache(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("缓存容量必须 >= 1");
        }
        this.capacity = capacity;
        // accessOrder = true 才是 LRU，false 就退化成 FIFO
        this.store = new LinkedHashMap<>(Math.max(16, capacity * 2), 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > LruCache.this.capacity;
            }
        };
    }

    public synchronized V get(K key) {
        return store.get(key);
    }

    public synchronized void put(K key, V value) {
        store.put(key, value);
    }

    public synchronized int size() {
        return store.size();
    }

    public synchronized void clear() {
        store.clear();
    }

    public int capacity() {
        return capacity;
    }
}