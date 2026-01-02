package com.ispw2.util;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for common collection operations to reduce code duplication.
 * Provides standardized collection handling methods used across the application.
 */
public final class CollectionUtils {

    private CollectionUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Creates a new empty HashMap.
     * 
     * @param <K> The type of keys
     * @param <V> The type of values
     * @return A new empty HashMap
     */
    public static <K, V> Map<K, V> createHashMap() {
        return new HashMap<>();
    }

    /**
     * Creates a new empty ArrayList.
     * 
     * @param <T> The type of elements
     * @return A new empty ArrayList
     */
    public static <T> List<T> createArrayList() {
        return new ArrayList<>();
    }

    /**
     * Creates a new empty HashSet.
     * 
     * @param <T> The type of elements
     * @return A new empty HashSet
     */
    public static <T> Set<T> createHashSet() {
        return new HashSet<>();
    }

    /**
     * Creates a new HashMap with initial capacity.
     * 
     * @param initialCapacity The initial capacity
     * @param <K> The type of keys
     * @param <V> The type of values
     * @return A new HashMap with specified initial capacity
     */
    public static <K, V> Map<K, V> createHashMap(final int initialCapacity) {
        return new HashMap<>(initialCapacity);
    }

    /**
     * Creates a new ArrayList with initial capacity.
     * 
     * @param initialCapacity The initial capacity
     * @param <T> The type of elements
     * @return A new ArrayList with specified initial capacity
     */
    public static <T> List<T> createArrayList(final int initialCapacity) {
        return new ArrayList<>(initialCapacity);
    }

    /**
     * Creates a new HashSet with initial capacity.
     * 
     * @param initialCapacity The initial capacity
     * @param <T> The type of elements
     * @return A new HashSet with specified initial capacity
     */
    public static <T> Set<T> createHashSet(final int initialCapacity) {
        return new HashSet<>(initialCapacity);
    }

    /**
     * Creates a defensive copy of a map.
     * 
     * @param original The original map
     * @param <K> The type of keys
     * @param <V> The type of values
     * @return A new map containing all elements from the original
     */
    public static <K, V> Map<K, V> defensiveCopy(final Map<K, V> original) {
        if (original == null) {
            return createHashMap();
        }
        return new HashMap<>(original);
    }

    /**
     * Creates a defensive copy of a list.
     * 
     * @param original The original list
     * @param <T> The type of elements
     * @return A new list containing all elements from the original
     */
    public static <T> List<T> defensiveCopy(final List<T> original) {
        if (original == null) {
            return createArrayList();
        }
        return new ArrayList<>(original);
    }

    /**
     * Creates a defensive copy of a set.
     * 
     * @param original The original set
     * @param <T> The type of elements
     * @return A new set containing all elements from the original
     */
    public static <T> Set<T> defensiveCopy(final Set<T> original) {
        if (original == null) {
            return createHashSet();
        }
        return new HashSet<>(original);
    }

    /**
     * Safely adds an element to a list if the element is not null.
     * 
     * @param list The list to add to
     * @param element The element to add
     * @param <T> The type of elements
     * @return true if element was added, false if element was null
     */
    public static <T> boolean addIfNotNull(final List<T> list, final T element) {
        if (element != null) {
            list.add(element);
            return true;
        }
        return false;
    }

    /**
     * Safely adds an element to a set if the element is not null.
     * 
     * @param set The set to add to
     * @param element The element to add
     * @param <T> The type of elements
     * @return true if element was added, false if element was null
     */
    public static <T> boolean addIfNotNull(final Set<T> set, final T element) {
        if (element != null) {
            set.add(element);
            return true;
        }
        return false;
    }

    /**
     * Safely puts a key-value pair in a map if both key and value are not null.
     * 
     * @param map The map to put into
     * @param key The key
     * @param value The value
     * @param <K> The type of keys
     * @param <V> The type of values
     * @return true if key-value pair was added, false if either was null
     */
    public static <K, V> boolean putIfNotNull(final Map<K, V> map, final K key, final V value) {
        if (key != null && value != null) {
            map.put(key, value);
            return true;
        }
        return false;
    }

    /**
     * Filters a collection and collects to a list.
     * 
     * @param collection The collection to filter
     * @param predicate The filter predicate
     * @param <T> The type of elements
     * @return A new list containing filtered elements
     */
    public static <T> List<T> filterToList(final Collection<T> collection, final java.util.function.Predicate<T> predicate) {
        if (collection == null) {
            return createArrayList();
        }
        return collection.stream()
                .filter(predicate)
                .collect(Collectors.toList());
    }

    /**
     * Maps a collection and collects to a list.
     * 
     * @param collection The collection to map
     * @param mapper The mapping function
     * @param <T> The input type
     * @param <R> The output type
     * @return A new list containing mapped elements
     */
    public static <T, R> List<R> mapToList(final Collection<T> collection, final java.util.function.Function<T, R> mapper) {
        if (collection == null) {
            return createArrayList();
        }
        return collection.stream()
                .map(mapper)
                .collect(Collectors.toList());
    }

    /**
     * Maps a collection and collects to a set.
     * 
     * @param collection The collection to map
     * @param mapper The mapping function
     * @param <T> The input type
     * @param <R> The output type
     * @return A new set containing mapped elements
     */
    public static <T, R> Set<R> mapToSet(final Collection<T> collection, final java.util.function.Function<T, R> mapper) {
        if (collection == null) {
            return createHashSet();
        }
        return collection.stream()
                .map(mapper)
                .collect(Collectors.toSet());
    }

    /**
     * Finds the maximum element in a collection using a comparator.
     * 
     * @param collection The collection to search
     * @param comparator The comparator to use
     * @param <T> The type of elements
     * @return Optional containing the maximum element, or empty if collection is empty
     */
    public static <T> Optional<T> findMax(final Collection<T> collection, final Comparator<T> comparator) {
        if (collection == null || collection.isEmpty()) {
            return Optional.empty();
        }
        return collection.stream().max(comparator);
    }

    /**
     * Finds the minimum element in a collection using a comparator.
     * 
     * @param collection The collection to search
     * @param comparator The comparator to use
     * @param <T> The type of elements
     * @return Optional containing the minimum element, or empty if collection is empty
     */
    public static <T> Optional<T> findMin(final Collection<T> collection, final Comparator<T> comparator) {
        if (collection == null || collection.isEmpty()) {
            return Optional.empty();
        }
        return collection.stream().min(comparator);
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
     * Gets the size of a collection, returning 0 for null collections.
     * 
     * @param collection The collection to get size of
     * @return The size of the collection, or 0 if null
     */
    public static int size(final Collection<?> collection) {
        return collection == null ? 0 : collection.size();
    }

    /**
     * Gets the size of a map, returning 0 for null maps.
     * 
     * @param map The map to get size of
     * @return The size of the map, or 0 if null
     */
    public static int size(final Map<?, ?> map) {
        return map == null ? 0 : map.size();
    }
}
