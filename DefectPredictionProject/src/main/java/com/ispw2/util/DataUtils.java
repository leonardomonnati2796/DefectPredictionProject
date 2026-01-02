package com.ispw2.util;

import org.slf4j.Logger;

import java.util.Collection;
import java.util.Map;

/**
 * Utility class for common data operations to reduce code duplication.
 * Provides standardized data handling methods used across the application.
 */
public final class DataUtils {

    private DataUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Checks if a collection is null or empty.
     * 
     * @param collection The collection to check
     * @return true if collection is null or empty
     */
    public static boolean isEmpty(final Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * Checks if a map is null or empty.
     * 
     * @param map The map to check
     * @return true if map is null or empty
     */
    public static boolean isEmpty(final Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    /**
     * Safely gets a value from a map with a default fallback.
     * 
     * @param map The map to get the value from
     * @param key The key to look for
     * @param defaultValue The default value if key is not found
     * @param <K> The type of the key
     * @param <V> The type of the value
     * @return The value from the map or the default value
     */
    public static <K, V> V getOrDefault(final Map<K, V> map, final K key, final V defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        return map.getOrDefault(key, defaultValue);
    }

    /**
     * Safely gets a number value from a map with a default fallback.
     * 
     * @param map The map to get the value from
     * @param key The key to look for
     * @param defaultValue The default number value if key is not found
     * @return The number value from the map or the default value
     */
    public static Number getNumberOrDefault(final Map<String, Number> map, final String key, final Number defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        final Number value = map.get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Validates that an object is not null.
     * 
     * @param obj The object to validate
     * @param name The name of the object for error reporting
     * @throws IllegalArgumentException if object is null
     */
    public static void requireNonNull(final Object obj, final String name) {
        if (obj == null) {
            throw new IllegalArgumentException(name + " cannot be null");
        }
    }

    /**
     * Validates that a string is not null or empty.
     * 
     * @param str The string to validate
     * @param name The name of the string for error reporting
     * @throws IllegalArgumentException if string is null or empty
     */
    public static void requireNonEmpty(final String str, final String name) {
        if (str == null || str.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be null or empty");
        }
    }

    /**
     * Logs the size of a collection for debugging purposes.
     * 
     * @param logger The logger instance
     * @param collectionName The name of the collection for logging
     * @param collection The collection to log the size of
     */
    public static void logCollectionSize(final Logger logger, final String collectionName, final Collection<?> collection) {
        if (logger.isDebugEnabled()) {
            final int size = collection != null ? collection.size() : 0;
            logger.debug("{} size: {}", collectionName, size);
        }
    }

    /**
     * Logs the size of a map for debugging purposes.
     * 
     * @param logger The logger instance
     * @param mapName The name of the map for logging
     * @param map The map to log the size of
     */
    public static void logMapSize(final Logger logger, final String mapName, final Map<?, ?> map) {
        if (logger.isDebugEnabled()) {
            final int size = map != null ? map.size() : 0;
            logger.debug("{} size: {}", mapName, size);
        }
    }
}
