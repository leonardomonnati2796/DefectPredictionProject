package com.ispw2.util;

import org.slf4j.Logger;

/**
 * Utility class for advanced logging patterns to further reduce code duplication.
 * Provides standardized logging methods for common patterns used across the application.
 */
public final class LoggingPatterns {

    private LoggingPatterns() {
        // Utility class - prevent instantiation
    }

    /**
     * Logs an info message with standardized formatting.
     * 
     * @param logger The logger instance
     * @param message The info message to log
     * @param args Optional arguments for message formatting
     */
    public static void info(final Logger logger, final String message, final Object... args) {
        logger.info(message, args);
    }

    /**
     * Logs a warning message with standardized formatting.
     * 
     * @param logger The logger instance
     * @param message The warning message to log
     * @param args Optional arguments for message formatting
     */
    public static void warn(final Logger logger, final String message, final Object... args) {
        logger.warn(message, args);
    }

    /**
     * Logs an error message with standardized formatting.
     * 
     * @param logger The logger instance
     * @param message The error message to log
     * @param args Optional arguments for message formatting
     */
    public static void error(final Logger logger, final String message, final Object... args) {
        logger.error(message, args);
    }

    /**
     * Logs an error message with exception details.
     * 
     * @param logger The logger instance
     * @param message The error message to log
     * @param throwable The throwable to log
     */
    public static void errorWithException(final Logger logger, final String message, final Throwable throwable) {
        logger.error(message, throwable);
    }

    /**
     * Logs a milestone message with standardized formatting.
     * 
     * @param logger The logger instance
     * @param milestone The milestone number
     * @param step The step number
     * @param description The milestone description
     */
    public static void logMilestone(final Logger logger, final int milestone, final int step, final String description) {
        logger.info("[Milestone {}, Step {}] {}", milestone, step, description);
    }

    /**
     * Logs a milestone message with standardized formatting (step 1).
     * 
     * @param logger The logger instance
     * @param milestone The milestone number
     * @param description The milestone description
     */
    public static void logMilestone(final Logger logger, final int milestone, final String description) {
        logMilestone(logger, milestone, 1, description);
    }

    /**
     * Logs a pipeline start message.
     * 
     * @param logger The logger instance
     * @param projectName The name of the project
     */
    public static void logPipelineStart(final Logger logger, final String projectName) {
        logger.info("--- STARTING PIPELINE FOR: {} ---", projectName);
    }

    /**
     * Logs a pipeline finish message.
     * 
     * @param logger The logger instance
     * @param projectName The name of the project
     */
    public static void logPipelineFinish(final Logger logger, final String projectName) {
        logger.info("--- FINISHED PIPELINE FOR: {} ---", projectName);
    }

    /**
     * Logs a processing start message.
     * 
     * @param logger The logger instance
     * @param itemName The name of the item being processed
     */
    public static void logProcessingStart(final Logger logger, final String itemName) {
        logger.info("Processing: {}", itemName);
    }

    /**
     * Logs a processing completion message.
     * 
     * @param logger The logger instance
     * @param itemName The name of the item that was processed
     * @param result Optional result information
     */
    public static void logProcessingComplete(final Logger logger, final String itemName, final String result) {
        if (result != null && !result.isEmpty()) {
            logger.info("Completed processing {}: {}", itemName, result);
        } else {
            logger.info("Completed processing: {}", itemName);
        }
    }

    /**
     * Logs a processing completion message without result.
     * 
     * @param logger The logger instance
     * @param itemName The name of the item that was processed
     */
    public static void logProcessingComplete(final Logger logger, final String itemName) {
        logProcessingComplete(logger, itemName, null);
    }

    /**
     * Logs a file operation message.
     * 
     * @param logger The logger instance
     * @param operation The file operation (created, updated, deleted, etc.)
     * @param filePath The path of the file
     */
    public static void logFileOperation(final Logger logger, final String operation, final String filePath) {
        logger.info("File {}: {}", operation, filePath);
    }

    /**
     * Logs a dataset operation message.
     * 
     * @param logger The logger instance
     * @param operation The dataset operation
     * @param datasetName The name of the dataset
     * @param details Optional details about the operation
     */
    public static void logDatasetOperation(final Logger logger, final String operation, final String datasetName, final String details) {
        if (details != null && !details.isEmpty()) {
            logger.info("Dataset {}: {} - {}", operation, datasetName, details);
        } else {
            logger.info("Dataset {}: {}", operation, datasetName);
        }
    }

    /**
     * Logs a dataset operation message without details.
     * 
     * @param logger The logger instance
     * @param operation The dataset operation
     * @param datasetName The name of the dataset
     */
    public static void logDatasetOperation(final Logger logger, final String operation, final String datasetName) {
        logDatasetOperation(logger, operation, datasetName, null);
    }
}
