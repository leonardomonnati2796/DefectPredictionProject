package com.ispw2.analysis;

public final class Metrics {
    private Metrics() {
        // Utility class
    }

    public static final String LOC = "LOC";
    public static final String CYCLOMATIC_COMPLEXITY = "CyclomaticComplexity";
    public static final String PARAMETER_COUNT = "ParameterCount";
    public static final String DUPLICATION = "Duplication";
    public static final String NR = "NR";
    public static final String NAUTH = "NAuth";
    public static final String STMT_ADDED = "stmtAdded";
    public static final String STMT_DELETED = "stmtDeleted";
    public static final String MAX_CHURN = "maxChurn";
    public static final String AVG_CHURN = "avgChurn";
}