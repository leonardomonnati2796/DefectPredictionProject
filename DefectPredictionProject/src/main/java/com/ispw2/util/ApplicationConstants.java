package com.ispw2.util;

/**
 * Centralized constants for the application to reduce code duplication.
 * Contains all commonly used string literals, file extensions, and configuration values.
 */
public final class ApplicationConstants {

    private ApplicationConstants() {
        // Utility class - prevent instantiation
    }

    // Directory names
    public static final String DATASETS_DIR_NAME = "datasets";
    public static final String GIT_PROJECTS_DIR_NAME = "github_projects";
    public static final String AFMETHOD_REFACTORED_DIR = "AFMethod_refactored";
    
    // File extensions
    public static final String CSV_EXTENSION = ".csv";
    public static final String ARFF_EXTENSION = ".arff";
    public static final String MODEL_EXTENSION = "_best.model";
    public static final String AFMETHOD_EXTENSION = "_AFMethod.txt";
    public static final String AFMETHOD_REFACTORED_EXTENSION = "_AFMethod_refactored.txt";
    public static final String PROCESSED_SUFFIX = "_processed";
    
    // Bug classification values
    public static final String BUGGY_YES = "yes";
    public static final String BUGGY_NO = "no";
    
    // Method key separator
    public static final String METHOD_KEY_SEPARATOR = "::";
    
    // Default values
    public static final double PROPORTION_DEFAULT_COEFFICIENT = 1.5;
    
    // Machine Learning constants
    public static final int NUM_FOLDS = 10;
    public static final int NUM_REPEATS = 10;
    
    // Error messages
    public static final String FATAL_IO_ERROR_MSG = "A fatal I/O error occurred while setting up directories.";
    public static final String PARENT_DIR_ERROR_MSG = "Cannot determine parent directory. Please run from within the project folder.";
    public static final String CLASSIFIER_ERROR_MSG = "Failed to obtain a valid classifier for project {}";
    public static final String ACTIONABLE_FEATURE_ERROR_MSG = "Failed to find actionable feature for project {}";
    public static final String PREPROCESSING_ERROR_MSG = "Failed to preprocess data";
    
    // Logging messages
    public static final String MILESTONE_2_STEP_10 = "[Milestone 2, Step 10] Starting What-If Simulation using AFeature: {}";
    public static final String MILESTONE_2_STEP_11 = "[Milestone 2, Step 11] Training BClassifier on dataset A (BClassifierA)...";
    public static final String MILESTONE_2_STEP_12 = "[Milestone 2, Step 12] Defect Prediction Summary Table:";
    public static final String MILESTONE_2_STEP_13 = "[Milestone 2, Step 13] Calculating final metrics based on custom formulas...";
    
    // Table formatting
    public static final String TABLE_SEPARATOR = "----------------------------------------------------------------------";
    public static final String TABLE_HEADER_FORMAT = "%-20s | %-10s | %-10s | %-10s | %-10s";
    public static final String TABLE_ROW_FORMAT = "%-20s | %-10.3f | %-10.3f | %-10.3f | %-10.3f";
    
    // Analysis messages
    public static final String PRELIMINARY_QUESTIONS_HEADER = "[PRELIMINARY QUESTIONS] Analyzing feature changes in AFMethod2 vs AFMethod...";
    public static final String PRELIMINARY_ANALYSIS_HEADER = "--- PRELIMINARY ANALYSIS ---";
    public static final String PRELIMINARY_ANALYSIS_FOOTER = "--- END PRELIMINARY ANALYSIS ---";
    
    // Formula calculation messages
    public static final String FORMULA_COMPONENTS_HEADER = "--- Formula Components ---";
    public static final String FORMULA_COMPONENTS_FOOTER = "--------------------------";
    
    // Question analysis messages
    public static final String QUESTION_1_YES = "QUESTION 1: YES - Predicted defects INCREASED in AFMethod2 ({} vs {}).";
    public static final String QUESTION_1_NO = "QUESTION 1: NO - Predicted defects did not increase in AFMethod2 ({} vs {}).";
    public static final String QUESTION_2_YES = "QUESTION 2: YES - Predicted defects DECREASED in AFMethod2 ({} vs {}).";
    public static final String QUESTION_2_NO_CHANGE = "QUESTION 2: NO CHANGE - Predicted defects remained the same in AFMethod2 ({}).";
    public static final String QUESTION_2_NO = "QUESTION 2: NO - Predicted defects increased in AFMethod2, suggesting maintainability may have worsened.";
    
    // Maintainability messages
    public static final String MAINTAINABILITY_NOT_IMPROVED = "This suggests we may NOT have improved maintainability in AFMethod2 compared to AFMethod.";
    public static final String MAINTAINABILITY_MAY_IMPROVED = "This suggests we MAY have improved maintainability by reducing {} from >0 to 0.";
    public static final String MAINTAINABILITY_NO_IMPACT = "This suggests the refactoring had no impact on predicted defect probability.";
    public static final String MAINTAINABILITY_WORSENED = "This suggests maintainability may have worsened.";
}
