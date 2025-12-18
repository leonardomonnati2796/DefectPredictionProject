package com.ispw2.util;

import org.slf4j.Logger;
import weka.classifiers.Classifier;
import weka.core.Instances;
import com.ispw2.preprocessing.DatasetUtilities;

/**
 * Utility class for common table formatting patterns to reduce code duplication.
 * Provides standardized table formatting methods used across the application.
 */
public final class TableFormattingUtils {

    private TableFormattingUtils() {
        // Utility class - prevent instantiation
    }

    // Common table formatting constants
    private static final String DEFAULT_SEPARATOR = "----------------------------------------------------------------------";
    private static final String COMPACT_SEPARATOR = "------------------------------------------------------------------";
    
    // Table format strings
    private static final String CLASSIFIER_TABLE_HEADER_FORMAT = "%-20s | %-10s | %-10s | %-10s | %-10s";
    private static final String CLASSIFIER_TABLE_ROW_FORMAT = "%-20s | %-10.3f | %-10.3f | %-10.3f | %-10.3f";
    
    private static final String SIMULATION_TABLE_HEADER_FORMAT = "| %-20s | %-15s | %-15s |";
    private static final String SIMULATION_TABLE_ROW_FORMAT = "| %-20s | %-15d | %-15d |";

    /**
     * Logs a classifier evaluation table with standardized formatting.
     * 
     * @param logger The logger instance
     * @param classifiers List of classifiers to display
     * @param evaluations List of evaluation results corresponding to classifiers
     */
    public static void logClassifierEvaluationTable(final Logger logger, 
                                                   final Classifier[] classifiers, 
                                                   final double[][] evaluations) {
        if (!logger.isInfoEnabled()) return;
        
        logger.info(DEFAULT_SEPARATOR);
        logger.info(String.format(CLASSIFIER_TABLE_HEADER_FORMAT, "Classifier", "AUC", "Precision", "Recall", "Kappa"));
        logger.info(DEFAULT_SEPARATOR);
        
        for (int i = 0; i < classifiers.length; i++) {
            final String classifierName = classifiers[i].getClass().getSimpleName();
            final double auc = evaluations[i][0];
            final double precision = evaluations[i][1];
            final double recall = evaluations[i][2];
            final double kappa = evaluations[i][3];
            
            logger.info(String.format(CLASSIFIER_TABLE_ROW_FORMAT, classifierName, auc, precision, recall, kappa));
        }
        
        logger.info(DEFAULT_SEPARATOR);
    }

    /**
     * Logs a simulation summary table with standardized formatting.
     * 
     * @param logger The logger instance
     * @param dataA Full dataset A
     * @param bPlus Dataset B+ (at-risk instances)
     * @param b Dataset B (refactored instances)
     * @param c Dataset C (safe instances)
     * @param bClassifierA Classifier trained on dataset A
     * @param aFeatureName Name of the actionable feature
     */
    public static void logSimulationSummaryTable(final Logger logger,
                                                final Instances dataA,
                                                final Instances bPlus,
                                                final Instances b,
                                                final Instances c,
                                                final Classifier bClassifierA,
                                                final String aFeatureName) {
        if (!logger.isInfoEnabled()) return;
        
        // Calculate defect counts
        final int defectsInA = DatasetUtilities.countDefective(bClassifierA, dataA);
        final int defectsInBplus = DatasetUtilities.countDefective(bClassifierA, bPlus);
        final int defectsInB = DatasetUtilities.countDefective(bClassifierA, b);
        final int defectsInC = DatasetUtilities.countDefective(bClassifierA, c);

        logger.info(COMPACT_SEPARATOR);
        logger.info(String.format(SIMULATION_TABLE_HEADER_FORMAT, "Dataset", "Total Instances", "Predicted Defects"));
        logger.info(COMPACT_SEPARATOR);
        logger.info(String.format(SIMULATION_TABLE_ROW_FORMAT, "A (Full Dataset)", dataA.numInstances(), defectsInA));
        logger.info(String.format(SIMULATION_TABLE_ROW_FORMAT, "B+ (" + aFeatureName + " > 0)", bPlus.numInstances(), defectsInBplus));
        logger.info(String.format(SIMULATION_TABLE_ROW_FORMAT, "B (B+ with " + aFeatureName + "=0)", b.numInstances(), defectsInB));
        logger.info(String.format(SIMULATION_TABLE_ROW_FORMAT, "C (" + aFeatureName + " <= 0)", c.numInstances(), defectsInC));
        logger.info(COMPACT_SEPARATOR);
    }

    /**
     * Creates a formatted table separator with specified length.
     * 
     * @param length The length of the separator
     * @return The formatted separator string
     */
    public static String createSeparator(final int length) {
        return "-".repeat(Math.max(1, length));
    }

    /**
     * Creates a formatted table separator with default length.
     * 
     * @return The formatted separator string
     */
    public static String createDefaultSeparator() {
        return DEFAULT_SEPARATOR;
    }

    /**
     * Creates a formatted table separator with compact length.
     * 
     * @return The formatted separator string
     */
    public static String createCompactSeparator() {
        return COMPACT_SEPARATOR;
    }
}
