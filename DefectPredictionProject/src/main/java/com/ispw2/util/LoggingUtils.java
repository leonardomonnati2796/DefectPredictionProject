package com.ispw2.util;

import org.slf4j.Logger;

/**
 * Utility class for common logging patterns to reduce code duplication.
 * Provides standardized logging methods used across the application.
 */
public final class LoggingUtils {

    private LoggingUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Logs a debug message only if debug logging is enabled.
     * Reduces boilerplate code for debug logging checks.
     * 
     * @param logger The logger instance
     * @param message The debug message to log
     * @param args Optional arguments for message formatting
     */
    public static void debugIfEnabled(final Logger logger, final String message, final Object... args) {
        if (logger.isDebugEnabled()) {
            logger.debug(message, args);
        }
    }

    /**
     * Logs a debug message with a single argument only if debug logging is enabled.
     * 
     * @param logger The logger instance
     * @param message The debug message to log
     * @param arg The argument for message formatting
     */
    public static void debugIfEnabled(final Logger logger, final String message, final Object arg) {
        if (logger.isDebugEnabled()) {
            logger.debug(message, arg);
        }
    }

    /**
     * Logs method entry with debug level.
     * 
     * @param logger The logger instance
     * @param methodName The name of the method being entered
     * @param args Optional arguments for the method
     */
    public static void logMethodEntry(final Logger logger, final String methodName, final Object... args) {
        debugIfEnabled(logger, "Entering method: {} with args: {}", methodName, args);
    }

    /**
     * Logs method exit with debug level.
     * 
     * @param logger The logger instance
     * @param methodName The name of the method being exited
     * @param result Optional result value
     */
    public static void logMethodExit(final Logger logger, final String methodName, final Object result) {
        debugIfEnabled(logger, "Exiting method: {} with result: {}", methodName, result);
    }

    /**
     * Logs method exit with debug level (no result).
     * 
     * @param logger The logger instance
     * @param methodName The name of the method being exited
     */
    public static void logMethodExit(final Logger logger, final String methodName) {
        debugIfEnabled(logger, "Exiting method: {}", methodName);
    }
}
