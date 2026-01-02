package com.ispw2.util;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class for common stream operations to reduce code duplication.
 * Provides standardized stream handling methods used across the application.
 */
public final class StreamUtils {

    private StreamUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Safely creates a stream from a collection.
     * 
     * @param collection The collection to create stream from
     * @param <T> The type of elements
     * @return A stream of elements, or empty stream if collection is null
     */
    public static <T> Stream<T> safeStream(final Collection<T> collection) {
        return collection == null ? Stream.empty() : collection.stream();
    }

    /**
     * Safely creates a stream from an array.
     * 
     * @param array The array to create stream from
     * @param <T> The type of elements
     * @return A stream of elements, or empty stream if array is null
     */
    public static <T> Stream<T> safeStream(final T[] array) {
        return array == null ? Stream.empty() : Arrays.stream(array);
    }

    /**
     * Filters a collection and collects to a list.
     * 
     * @param collection The collection to filter
     * @param predicate The filter predicate
     * @param <T> The type of elements
     * @return A new list containing filtered elements
     */
    public static <T> List<T> filterToList(final Collection<T> collection, final Predicate<T> predicate) {
        return safeStream(collection)
                .filter(predicate)
                .collect(Collectors.toList());
    }

    /**
     * Filters a collection and collects to a set.
     * 
     * @param collection The collection to filter
     * @param predicate The filter predicate
     * @param <T> The type of elements
     * @return A new set containing filtered elements
     */
    public static <T> Set<T> filterToSet(final Collection<T> collection, final Predicate<T> predicate) {
        return safeStream(collection)
                .filter(predicate)
                .collect(Collectors.toSet());
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
    public static <T, R> List<R> mapToList(final Collection<T> collection, final Function<T, R> mapper) {
        return safeStream(collection)
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
    public static <T, R> Set<R> mapToSet(final Collection<T> collection, final Function<T, R> mapper) {
        return safeStream(collection)
                .map(mapper)
                .collect(Collectors.toSet());
    }

    /**
     * Maps a collection and collects to a map.
     * 
     * @param collection The collection to map
     * @param keyMapper The key mapping function
     * @param valueMapper The value mapping function
     * @param <T> The input type
     * @param <K> The key type
     * @param <V> The value type
     * @return A new map containing mapped elements
     */
    public static <T, K, V> Map<K, V> mapToMap(final Collection<T> collection, 
                                              final Function<T, K> keyMapper, 
                                              final Function<T, V> valueMapper) {
        return safeStream(collection)
                .collect(Collectors.toMap(keyMapper, valueMapper));
    }

    /**
     * Maps a collection and collects to a map with duplicate key handler.
     * 
     * @param collection The collection to map
     * @param keyMapper The key mapping function
     * @param valueMapper The value mapping function
     * @param duplicateKeyHandler The handler for duplicate keys
     * @param <T> The input type
     * @param <K> The key type
     * @param <V> The value type
     * @return A new map containing mapped elements
     */
    public static <T, K, V> Map<K, V> mapToMap(final Collection<T> collection, 
                                              final Function<T, K> keyMapper, 
                                              final Function<T, V> valueMapper,
                                              final java.util.function.BinaryOperator<V> duplicateKeyHandler) {
        return safeStream(collection)
                .collect(Collectors.toMap(keyMapper, valueMapper, duplicateKeyHandler));
    }

    /**
     * Groups a collection by a key function.
     * 
     * @param collection The collection to group
     * @param keyMapper The key mapping function
     * @param <T> The input type
     * @param <K> The key type
     * @return A new map with grouped elements
     */
    public static <T, K> Map<K, List<T>> groupBy(final Collection<T> collection, final Function<T, K> keyMapper) {
        return safeStream(collection)
                .collect(Collectors.groupingBy(keyMapper));
    }

    /**
     * Groups a collection by a key function and maps values.
     * 
     * @param collection The collection to group
     * @param keyMapper The key mapping function
     * @param valueMapper The value mapping function
     * @param <T> The input type
     * @param <K> The key type
     * @param <V> The value type
     * @return A new map with grouped and mapped elements
     */
    public static <T, K, V> Map<K, List<V>> groupBy(final Collection<T> collection, 
                                                     final Function<T, K> keyMapper, 
                                                     final Function<T, V> valueMapper) {
        return safeStream(collection)
                .collect(Collectors.groupingBy(keyMapper, Collectors.mapping(valueMapper, Collectors.toList())));
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
        return safeStream(collection).max(comparator);
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
        return safeStream(collection).min(comparator);
    }

    /**
     * Finds the maximum element in a collection using a key extractor.
     * 
     * @param collection The collection to search
     * @param keyExtractor The key extractor function
     * @param <T> The type of elements
     * @param <U> The type of the key
     * @return Optional containing the maximum element, or empty if collection is empty
     */
    public static <T, U extends Comparable<? super U>> Optional<T> findMaxBy(final Collection<T> collection, 
                                                                             final Function<T, U> keyExtractor) {
        return safeStream(collection).max(Comparator.comparing(keyExtractor));
    }

    /**
     * Finds the minimum element in a collection using a key extractor.
     * 
     * @param collection The collection to search
     * @param keyExtractor The key extractor function
     * @param <T> The type of elements
     * @param <U> The type of the key
     * @return Optional containing the minimum element, or empty if collection is empty
     */
    public static <T, U extends Comparable<? super U>> Optional<T> findMinBy(final Collection<T> collection, 
                                                                             final Function<T, U> keyExtractor) {
        return safeStream(collection).min(Comparator.comparing(keyExtractor));
    }

    /**
     * Counts elements in a collection that match a predicate.
     * 
     * @param collection The collection to count
     * @param predicate The predicate to test
     * @param <T> The type of elements
     * @return The count of matching elements
     */
    public static <T> long countMatching(final Collection<T> collection, final Predicate<T> predicate) {
        return safeStream(collection).filter(predicate).count();
    }

    /**
     * Checks if any element in a collection matches a predicate.
     * 
     * @param collection The collection to check
     * @param predicate The predicate to test
     * @param <T> The type of elements
     * @return true if any element matches the predicate
     */
    public static <T> boolean anyMatch(final Collection<T> collection, final Predicate<T> predicate) {
        return safeStream(collection).anyMatch(predicate);
    }

    /**
     * Checks if all elements in a collection match a predicate.
     * 
     * @param collection The collection to check
     * @param predicate The predicate to test
     * @param <T> The type of elements
     * @return true if all elements match the predicate
     */
    public static <T> boolean allMatch(final Collection<T> collection, final Predicate<T> predicate) {
        return safeStream(collection).allMatch(predicate);
    }

    /**
     * Checks if no elements in a collection match a predicate.
     * 
     * @param collection The collection to check
     * @param predicate The predicate to test
     * @param <T> The type of elements
     * @return true if no elements match the predicate
     */
    public static <T> boolean noneMatch(final Collection<T> collection, final Predicate<T> predicate) {
        return safeStream(collection).noneMatch(predicate);
    }

    /**
     * Finds the first element in a collection that matches a predicate.
     * 
     * @param collection The collection to search
     * @param predicate The predicate to test
     * @param <T> The type of elements
     * @return Optional containing the first matching element, or empty if none found
     */
    public static <T> Optional<T> findFirst(final Collection<T> collection, final Predicate<T> predicate) {
        return safeStream(collection).filter(predicate).findFirst();
    }

    /**
     * Finds any element in a collection that matches a predicate.
     * 
     * @param collection The collection to search
     * @param predicate The predicate to test
     * @param <T> The type of elements
     * @return Optional containing any matching element, or empty if none found
     */
    public static <T> Optional<T> findAny(final Collection<T> collection, final Predicate<T> predicate) {
        return safeStream(collection).filter(predicate).findAny();
    }

    /**
     * Collects a collection to a list with a limit.
     * 
     * @param collection The collection to collect
     * @param limit The maximum number of elements to collect
     * @param <T> The type of elements
     * @return A new list containing up to limit elements
     */
    public static <T> List<T> collectWithLimit(final Collection<T> collection, final long limit) {
        return safeStream(collection)
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Collects a collection to a list with a limit and skip.
     * 
     * @param collection The collection to collect
     * @param skip The number of elements to skip
     * @param limit The maximum number of elements to collect
     * @param <T> The type of elements
     * @return A new list containing up to limit elements after skipping
     */
    public static <T> List<T> collectWithSkipAndLimit(final Collection<T> collection, final long skip, final long limit) {
        return safeStream(collection)
                .skip(skip)
                .limit(limit)
                .collect(Collectors.toList());
    }
}
