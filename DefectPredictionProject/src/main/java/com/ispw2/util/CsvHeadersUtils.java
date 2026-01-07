package com.ispw2.util;

import com.ispw2.analysis.CodeQualityMetrics;

/**
 * Utility class for centralized CSV header definitions to reduce code duplication.
 * Provides standardized CSV header arrays used across the application.
 */
public final class CsvHeadersUtils {

    private CsvHeadersUtils() {
        // Utility class - prevent instantiation
    }

    // Header field names
    private static final String PROJECT_HEADER = "Project";
    private static final String METHOD_NAME_HEADER = "MethodName";
    private static final String RELEASE_HEADER = "Release";

    /**
     * Standard CSV headers for method-level defect prediction datasets.
     * Includes project information, method identification, code quality metrics,
     * version control metrics, change metrics, and bug classification.
     */
    public static final String[] METHOD_DATASET_HEADERS = {
        PROJECT_HEADER, 
        METHOD_NAME_HEADER, 
        RELEASE_HEADER, 
        CodeQualityMetrics.CODE_SMELLS, 
        CodeQualityMetrics.CYCLOMATIC_COMPLEXITY, 
        CodeQualityMetrics.PARAMETER_COUNT,
        CodeQualityMetrics.DUPLICATION, 
        CodeQualityMetrics.NR, 
        CodeQualityMetrics.NAUTH, 
        CodeQualityMetrics.STMT_ADDED, 
        CodeQualityMetrics.STMT_DELETED, 
        CodeQualityMetrics.MAX_CHURN, 
        CodeQualityMetrics.AVG_CHURN, 
        CodeQualityMetrics.IS_BUGGY
    };

    /**
     * Legacy CSV headers for backward compatibility.
     * Used in older versions of the dataset builder.
     */
    public static final String[] LEGACY_METHOD_DATASET_HEADERS = {
        PROJECT_HEADER, 
        "MethodName", 
        RELEASE_HEADER, 
        "CodeSmells", 
        "CyclomaticComplexity", 
        "ParameterCount",
        "NestingDepth", 
        "NR", 
        "NAuth", 
        "stmtAdded", 
        "stmtDeleted", 
        "maxChurn", 
        "avgChurn", 
        "IsBuggy"
    };

    /**
     * Gets the appropriate CSV headers based on the dataset version.
     * 
     * @param useLegacyHeaders Whether to use legacy headers for backward compatibility
     * @return Array of CSV header strings
     */
    public static String[] getMethodDatasetHeaders(final boolean useLegacyHeaders) {
        return useLegacyHeaders ? LEGACY_METHOD_DATASET_HEADERS : METHOD_DATASET_HEADERS;
    }

    /**
     * Gets the standard CSV headers for method-level datasets.
     * 
     * @return Array of CSV header strings
     */
    public static String[] getMethodDatasetHeaders() {
        return METHOD_DATASET_HEADERS;
    }

    /**
     * Validates that the provided headers match the expected format.
     * 
     * @param headers The headers to validate
     * @return true if headers are valid, false otherwise
     */
    public static boolean validateHeaders(final String[] headers) {
        if (headers == null || headers.length == 0) {
            return false;
        }
        
        // Check for required headers
        final String[] requiredHeaders = {PROJECT_HEADER, METHOD_NAME_HEADER, RELEASE_HEADER, CodeQualityMetrics.IS_BUGGY};
        for (final String required : requiredHeaders) {
            boolean found = false;
            for (final String header : headers) {
                if (required.equals(header)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        
        return true;
    }
}
