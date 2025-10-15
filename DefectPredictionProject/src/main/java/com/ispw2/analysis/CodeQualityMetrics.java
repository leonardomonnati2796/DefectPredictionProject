package com.ispw2.analysis;

/**
 * Constants for code quality metrics used throughout the analysis.
 * Provides standardized metric names for consistency across the system.
 */
public final class CodeQualityMetrics {
    
    // Code complexity metrics
    public static final String CYCLOMATIC_COMPLEXITY = "CyclomaticComplexity";
    public static final String PARAMETER_COUNT = "ParameterCount";
    public static final String CODE_SMELLS = "CodeSmells";
    
    // Structural metrics
    public static final String NESTING_DEPTH = "NestingDepth";
    
    // Version control metrics
    public static final String NR = "NR";
    public static final String NAUTH = "NAuth";
    
    // Change metrics
    public static final String STMT_ADDED = "stmtAdded";
    public static final String STMT_DELETED = "stmtDeleted";
    public static final String MAX_CHURN = "maxChurn";
    public static final String AVG_CHURN = "avgChurn";
    
    // Target variable
    public static final String IS_BUGGY = "IsBuggy";
    
    private CodeQualityMetrics() {
        // Utility class - prevent instantiation
    }
}