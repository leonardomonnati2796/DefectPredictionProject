package com.ispw2.util;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Utility class for common Optional operations to reduce code duplication.
 * Provides standardized Optional handling methods used across the application.
 */
public final class OptionalUtils {

    private OptionalUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Safely gets a value from an Optional, returning a default if empty.
     * 
     * @param optional The Optional to get value from
     * @param defaultValue The default value to return if Optional is empty
     * @param <T> The type of the value
     * @return The value from Optional or the default value
     */
    public static <T> T getOrDefault(final Optional<T> optional, final T defaultValue) {
        return optional.orElse(defaultValue);
    }

    /**
     * Safely gets a value from an Optional, returning a default from a supplier if empty.
     * 
     * @param optional The Optional to get value from
     * @param defaultValueSupplier The supplier for default value if Optional is empty
     * @param <T> The type of the value
     * @return The value from Optional or the default value from supplier
     */
    public static <T> T getOrDefault(final Optional<T> optional, final Supplier<T> defaultValueSupplier) {
        return optional.orElseGet(defaultValueSupplier);
    }

    /**
     * Safely maps an Optional value, returning empty if the Optional is empty.
     * 
     * @param optional The Optional to map
     * @param mapper The mapping function
     * @param <T> The input type
     * @param <R> The output type
     * @return A new Optional with mapped value, or empty if input is empty
     */
    public static <T, R> Optional<R> mapSafely(final Optional<T> optional, final Function<T, R> mapper) {
        return optional.map(mapper);
    }

    /**
     * Safely filters an Optional value, returning empty if the Optional is empty or doesn't match predicate.
     * 
     * @param optional The Optional to filter
     * @param predicate The filter predicate
     * @param <T> The type of the value
     * @return A new Optional with filtered value, or empty if input is empty or doesn't match
     */
    public static <T> Optional<T> filterSafely(final Optional<T> optional, final Predicate<T> predicate) {
        return optional.filter(predicate);
    }

    /**
     * Safely executes an action if Optional is present.
     * 
     * @param optional The Optional to check
     * @param action The action to execute if present
     * @param <T> The type of the value
     */
    public static <T> void ifPresentSafely(final Optional<T> optional, final java.util.function.Consumer<T> action) {
        optional.ifPresent(action);
    }

    /**
     * Safely executes an action if Optional is present, or another action if empty.
     * 
     * @param optional The Optional to check
     * @param presentAction The action to execute if present
     * @param emptyAction The action to execute if empty
     * @param <T> The type of the value
     */
    public static <T> void ifPresentOrElseSafely(final Optional<T> optional, 
                                                 final java.util.function.Consumer<T> presentAction, 
                                                 final Runnable emptyAction) {
        optional.ifPresentOrElse(presentAction, emptyAction);
    }

    /**
     * Creates an Optional from a nullable value.
     * 
     * @param value The value to wrap
     * @param <T> The type of the value
     * @return An Optional containing the value, or empty if null
     */
    public static <T> Optional<T> ofNullable(final T value) {
        return Optional.ofNullable(value);
    }

    /**
     * Creates an Optional from a value, throwing exception if null.
     * 
     * @param value The value to wrap
     * @param <T> The type of the value
     * @return An Optional containing the value
     * @throws NullPointerException if value is null
     */
    public static <T> Optional<T> of(final T value) {
        return Optional.of(value);
    }

    /**
     * Creates an empty Optional.
     * 
     * @param <T> The type of the value
     * @return An empty Optional
     */
    public static <T> Optional<T> empty() {
        return Optional.empty();
    }

    /**
     * Checks if an Optional is present and matches a predicate.
     * 
     * @param optional The Optional to check
     * @param predicate The predicate to test
     * @param <T> The type of the value
     * @return true if Optional is present and matches predicate
     */
    public static <T> boolean isPresentAndMatches(final Optional<T> optional, final Predicate<T> predicate) {
        return optional.isPresent() && predicate.test(optional.get());
    }

    /**
     * Checks if an Optional is present and equals a specific value.
     * 
     * @param optional The Optional to check
     * @param value The value to compare with
     * @param <T> The type of the value
     * @return true if Optional is present and equals the value
     */
    public static <T> boolean isPresentAndEquals(final Optional<T> optional, final T value) {
        return optional.isPresent() && java.util.Objects.equals(optional.get(), value);
    }

    /**
     * Safely gets a value from an Optional, throwing a custom exception if empty.
     * 
     * @param optional The Optional to get value from
     * @param exceptionSupplier The supplier for exception if Optional is empty
     * @param <T> The type of the value
     * @param <E> The type of the exception
     * @return The value from Optional
     * @throws E if Optional is empty
     */
    public static <T, E extends Exception> T getOrThrow(final Optional<T> optional, final Supplier<E> exceptionSupplier) throws E {
        return optional.orElseThrow(exceptionSupplier);
    }

    /**
     * Safely gets a value from an Optional, throwing a RuntimeException with custom message if empty.
     * 
     * @param optional The Optional to get value from
     * @param message The message for exception if Optional is empty
     * @param <T> The type of the value
     * @return The value from Optional
     * @throws RuntimeException if Optional is empty
     */
    public static <T> T getOrThrow(final Optional<T> optional, final String message) {
        return optional.orElseThrow(() -> new RuntimeException(message));
    }

    /**
     * Safely gets a value from an Optional, throwing a RuntimeException with custom message and cause if empty.
     * 
     * @param optional The Optional to get value from
     * @param message The message for exception if Optional is empty
     * @param cause The cause for exception if Optional is empty
     * @param <T> The type of the value
     * @return The value from Optional
     * @throws RuntimeException if Optional is empty
     */
    public static <T> T getOrThrow(final Optional<T> optional, final String message, final Throwable cause) {
        return optional.orElseThrow(() -> new RuntimeException(message, cause));
    }
}
