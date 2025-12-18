package com.ispw2.util;

import org.slf4j.Logger;

import java.io.File;
import java.util.Collection;
import java.util.Map;

/**
 * Utility class for common validation patterns to reduce code duplication.
 * Provides standardized validation methods used across the application.
 */
public final class ValidationUtils {

    private ValidationUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Validates that a file exists and is readable.
     * 
     * @param filePath The path of the file to validate
     * @param logger The logger instance for error reporting
     * @return true if file is valid, false otherwise
     */
    public static boolean isValidFile(final String filePath, final Logger logger) {
        if (filePath == null || filePath.trim().isEmpty()) {
            logger.warn("File path is null or empty");
            return false;
        }
        
        final File file = new File(filePath);
        if (!file.exists()) {
            logger.warn("File does not exist: {}", filePath);
            return false;
        }
        
        if (!file.canRead()) {
            logger.warn("File is not readable: {}", filePath);
            return false;
        }
        
        if (file.length() == 0) {
            logger.warn("File is empty: {}", filePath);
            return false;
        }
        
        return true;
    }

    /**
     * Validates that a file exists and is readable (without size check).
     * 
     * @param filePath The path of the file to validate
     * @param logger The logger instance for error reporting
     * @return true if file is valid, false otherwise
     */
    public static boolean isValidFileForReading(final String filePath, final Logger logger) {
        if (filePath == null || filePath.trim().isEmpty()) {
            logger.warn("File path is null or empty");
            return false;
        }
        
        final File file = new File(filePath);
        if (!file.exists()) {
            logger.warn("File does not exist: {}", filePath);
            return false;
        }
        
        if (!file.canRead()) {
            logger.warn("File is not readable: {}", filePath);
            return false;
        }
        
        return true;
    }

    /**
     * Validates that a collection is not null and not empty.
     * 
     * @param collection The collection to validate
     * @param collectionName The name of the collection for error reporting
     * @param logger The logger instance for error reporting
     * @return true if collection is valid, false otherwise
     */
    public static boolean isValidCollection(final Collection<?> collection, final String collectionName, final Logger logger) {
        if (collection == null) {
            logger.warn("Collection '{}' is null", collectionName);
            return false;
        }
        
        if (collection.isEmpty()) {
            logger.warn("Collection '{}' is empty", collectionName);
            return false;
        }
        
        return true;
    }

    /**
     * Validates that a map is not null and not empty.
     * 
     * @param map The map to validate
     * @param mapName The name of the map for error reporting
     * @param logger The logger instance for error reporting
     * @return true if map is valid, false otherwise
     */
    public static boolean isValidMap(final Map<?, ?> map, final String mapName, final Logger logger) {
        if (map == null) {
            logger.warn("Map '{}' is null", mapName);
            return false;
        }
        
        if (map.isEmpty()) {
            logger.warn("Map '{}' is empty", mapName);
            return false;
        }
        
        return true;
    }

    /**
     * Validates that a string is not null, not empty, and not blank.
     * 
     * @param value The string to validate
     * @param fieldName The name of the field for error reporting
     * @param logger The logger instance for error reporting
     * @return true if string is valid, false otherwise
     */
    public static boolean isValidString(final String value, final String fieldName, final Logger logger) {
        if (value == null) {
            logger.warn("String '{}' is null", fieldName);
            return false;
        }
        
        if (value.trim().isEmpty()) {
            logger.warn("String '{}' is empty or blank", fieldName);
            return false;
        }
        
        return true;
    }

    /**
     * Validates that a number is not null and within a valid range.
     * 
     * @param number The number to validate
     * @param fieldName The name of the field for error reporting
     * @param minValue The minimum allowed value (inclusive)
     * @param maxValue The maximum allowed value (inclusive)
     * @param logger The logger instance for error reporting
     * @return true if number is valid, false otherwise
     */
    public static boolean isValidNumber(final Number number, final String fieldName, final double minValue, final double maxValue, final Logger logger) {
        if (number == null) {
            logger.warn("Number '{}' is null", fieldName);
            return false;
        }
        
        final double value = number.doubleValue();
        if (value < minValue || value > maxValue) {
            logger.warn("Number '{}' value {} is outside valid range [{}, {}]", fieldName, value, minValue, maxValue);
            return false;
        }
        
        return true;
    }

    /**
     * Validates that an object is not null.
     * 
     * @param obj The object to validate
     * @param fieldName The name of the field for error reporting
     * @param logger The logger instance for error reporting
     * @return true if object is valid, false otherwise
     */
    public static boolean isValidObject(final Object obj, final String fieldName, final Logger logger) {
        if (obj == null) {
            logger.warn("Object '{}' is null", fieldName);
            return false;
        }
        
        return true;
    }

    /**
     * Validates that a number is positive.
     * 
     * @param number The number to validate
     * @param fieldName The name of the field for error reporting
     * @param logger The logger instance for error reporting
     * @return true if number is valid, false otherwise
     */
    public static boolean isValidPositiveNumber(final Number number, final String fieldName, final Logger logger) {
        if (number == null) {
            logger.warn("Number '{}' is null", fieldName);
            return false;
        }
        
        if (number.doubleValue() <= 0) {
            logger.warn("Number '{}' is not positive: {}", fieldName, number);
            return false;
        }
        
        return true;
    }

    /**
     * Validates that a number is non-negative.
     * 
     * @param number The number to validate
     * @param fieldName The name of the field for error reporting
     * @param logger The logger instance for error reporting
     * @return true if number is valid, false otherwise
     */
    public static boolean isValidNonNegativeNumber(final Number number, final String fieldName, final Logger logger) {
        if (number == null) {
            logger.warn("Number '{}' is null", fieldName);
            return false;
        }
        
        if (number.doubleValue() < 0) {
            logger.warn("Number '{}' is negative: {}", fieldName, number);
            return false;
        }
        
        return true;
    }
}