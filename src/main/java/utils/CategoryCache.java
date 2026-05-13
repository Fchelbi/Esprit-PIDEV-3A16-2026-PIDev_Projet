package utils;

import entities.Category;
import services.CategoryService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CategoryCache {

    private static final Map<Integer, String> cache = new HashMap<>();
    private static boolean loaded = false;

    public static void refresh() {
        try {
            Map<Integer, String> fresh = new HashMap<>();
            for (Category c : new CategoryService().getAll()) {
                fresh.put(c.getId(), c.getName());
            }
            synchronized (cache) {
                cache.clear();
                cache.putAll(fresh);
                loaded = true;
            }
        } catch (Exception e) {
            System.err.println("[CategoryCache] Failed to load categories: " + e.getMessage());
        }
    }

    public static String nameOf(int id) {
        ensureLoaded();
        synchronized (cache) {
            return cache.getOrDefault(id, "Uncategorized");
        }
    }

    public static Map<Integer, String> all() {
        ensureLoaded();
        synchronized (cache) {
            return Collections.unmodifiableMap(new HashMap<>(cache));
        }
    }

    private static void ensureLoaded() {
        if (!loaded) refresh();
    }
}