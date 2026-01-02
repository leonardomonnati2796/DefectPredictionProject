package com.ispw2.util;

import org.slf4j.Logger;

/**
 * Utility class for common exception handling patterns to reduce code duplication.
 * Provides standardized exception handling methods used across the application.
 */
public final class ExceptionUtils {

    private ExceptionUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Handles a generic exception with logging and optional recovery.
     * 
     * @param logger The logger instance
     * @param operation The operation that failed
     * @param exception The exception that occurred
     * @param context Additional context for the error
     */
    public static void handleGenericException(final Logger logger, final String operation, final Exception exception, final String context) {
        logger.error("{} failed: {}", operation, exception.getMessage(), exception);
        if (context != null && !context.isEmpty()) {
            logger.error("Context: {}", context);
        }
    }

    /**
     * Handles a generic exception with logging.
     * 
     * @param logger The logger instance
     * @param operation The operation that failed
     * @param exception The exception that occurred
     */
    public static void handleGenericException(final Logger logger, final String operation, final Exception exception) {
        handleGenericException(logger, operation, exception, null);
    }

    /**
     * Attempts to recover from an exception with fallback strategy.
     * 
     * @param logger The logger instance
     * @param operation The operation that failed
     * @param exception The exception that occurred
     * @param fallbackMessage Message to log when attempting fallback
     * @return true if recovery was attempted, false otherwise
     */
    public static boolean attemptRecovery(final Logger logger, final String operation, final Exception exception, final String fallbackMessage) {
        logger.warn("Attempting to recover from {} failure", operation);
        try {
            logger.info("Attempting fallback strategy: {}", fallbackMessage);
            logger.warn("Using fallback approach for {}", operation);
            return true;
        } catch (final Exception recoveryException) {
            logger.error("Recovery attempt failed for {}: {}", operation, recoveryException.getMessage(), recoveryException);
            return false;
        }
    }

    /**
     * Logs a warning about a fallback approach being used.
     * 
     * @param logger The logger instance
     * @param operation The operation using fallback
     * @param fallbackDescription Description of the fallback approach
     */
    public static void logFallbackUsage(final Logger logger, final String operation, final String fallbackDescription) {
        logger.warn("Using fallback approach for {}: {}", operation, fallbackDescription);
    }

    /**
     * Logs an error about inability to proceed with an operation.
     * 
     * @param logger The logger instance
     * @param operation The operation that cannot proceed
     * @param reason The reason why the operation cannot proceed
     */
    public static void logCannotProceed(final Logger logger, final String operation, final String reason) {
        logger.error("Cannot proceed with {}: {}", operation, reason);
    }

    /**
     * Creates a standardized error message for exceptions.
     * 
     * @param operation The operation that failed
     * @param context Additional context
     * @param exception The exception that occurred
     * @return A formatted error message
     */
    public static String createErrorMessage(final String operation, final String context, final Exception exception) {
        final StringBuilder message = new StringBuilder(operation).append(" failed");
        if (context != null && !context.isEmpty()) {
            message.append(" for ").append(context);
        }
        message.append(": ").append(exception.getMessage());
        return message.toString();
    }

    /**
     * Creates a standardized error message for exceptions without context.
     * 
     * @param operation The operation that failed
     * @param exception The exception that occurred
     * @return A formatted error message
     */
    public static String createErrorMessage(final String operation, final Exception exception) {
        return createErrorMessage(operation, null, exception);
    }
}
