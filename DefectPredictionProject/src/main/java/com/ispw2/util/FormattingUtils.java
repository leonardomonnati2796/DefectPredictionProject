package com.ispw2.util;

import java.util.Locale;

/**
 * Utility class for common formatting patterns to reduce code duplication.
 * Provides standardized formatting methods used across the application.
 */
public final class FormattingUtils {

    private FormattingUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Formats a number with 2 decimal places using US locale.
     * 
     * @param number The number to format
     * @return The formatted string
     */
    public static String formatDecimal(final Number number) {
        if (number == null) {
            return "0.00";
        }
        return String.format(Locale.US, "%.2f", number.doubleValue());
    }

    /**
     * Formats a number with specified decimal places using US locale.
     * 
     * @param number The number to format
     * @param decimalPlaces The number of decimal places
     * @return The formatted string
     */
    public static String formatDecimal(final Number number, final int decimalPlaces) {
        if (number == null) {
            return "0." + "0".repeat(decimalPlaces);
        }
        return String.format(Locale.US, "%." + decimalPlaces + "f", number.doubleValue());
    }

    /**
     * Formats a percentage with 2 decimal places.
     * 
     * @param number The number to format as percentage
     * @return The formatted percentage string
     */
    public static String formatPercentage(final Number number) {
        if (number == null) {
            return "0.00%";
        }
        return String.format(Locale.US, "%.2f%%", number.doubleValue() * 100);
    }

    /**
     * Formats a percentage with specified decimal places.
     * 
     * @param number The number to format as percentage
     * @param decimalPlaces The number of decimal places
     * @return The formatted percentage string
     */
    public static String formatPercentage(final Number number, final int decimalPlaces) {
        if (number == null) {
            return "0." + "0".repeat(decimalPlaces) + "%";
        }
        return String.format(Locale.US, "%." + decimalPlaces + "f%%", number.doubleValue() * 100);
    }

    /**
     * Formats a number as a count (integer).
     * 
     * @param number The number to format
     * @return The formatted count string
     */
    public static String formatCount(final Number number) {
        if (number == null) {
            return "0";
        }
        return String.valueOf(number.intValue());
    }

    /**
     * Formats a file size in bytes with appropriate units.
     * 
     * @param bytes The size in bytes
     * @return The formatted file size string
     */
    public static String formatFileSize(final long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * Formats a duration in milliseconds to a readable format.
     * 
     * @param milliseconds The duration in milliseconds
     * @return The formatted duration string
     */
    public static String formatDuration(final long milliseconds) {
        if (milliseconds < 1000) {
            return milliseconds + " ms";
        } else if (milliseconds < 60000) {
            return String.format(Locale.US, "%.2f s", milliseconds / 1000.0);
        } else if (milliseconds < 3600000) {
            return String.format(Locale.US, "%.2f min", milliseconds / 60000.0);
        } else {
            return String.format(Locale.US, "%.2f h", milliseconds / 3600000.0);
        }
    }

    /**
     * Formats a method identifier for display.
     * 
     * @param filePath The file path
     * @param signature The method signature
     * @return The formatted method identifier
     */
    public static String formatMethodIdentifier(final String filePath, final String signature) {
        if (filePath == null || signature == null) {
            return "Unknown";
        }
        return filePath + "/" + signature;
    }

    /**
     * Formats a project name for display.
     * 
     * @param projectName The project name
     * @return The formatted project name
     */
    public static String formatProjectName(final String projectName) {
        if (projectName == null || projectName.trim().isEmpty()) {
            return "Unknown Project";
        }
        return projectName.trim().toUpperCase();
    }

    /**
     * Formats a release name for display.
     * 
     * @param releaseName The release name
     * @return The formatted release name
     */
    public static String formatReleaseName(final String releaseName) {
        if (releaseName == null || releaseName.trim().isEmpty()) {
            return "Unknown Release";
        }
        return releaseName.trim();
    }

    /**
     * Formats a feature name for display.
     * 
     * @param featureName The feature name
     * @return The formatted feature name
     */
    public static String formatFeatureName(final String featureName) {
        if (featureName == null || featureName.trim().isEmpty()) {
            return "Unknown Feature";
        }
        return featureName.trim();
    }
}
