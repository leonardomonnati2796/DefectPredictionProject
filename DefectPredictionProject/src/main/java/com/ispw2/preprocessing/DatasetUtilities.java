package com.ispw2.preprocessing;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffLoader;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Optional;

/**
 * Utility class for loading and working with datasets in various formats.
 * Provides static methods for loading ARFF files and reading CSV data.
 */
public final class DatasetUtilities {

    private static final Logger log = LoggerFactory.getLogger(DatasetUtilities.class);
    // Threshold used to decide a positive ("yes") prediction based on probability
    // Tune this value if the summary table shows all zeros or too many positives
    private static final double PREDICTION_YES_THRESHOLD = 0.20;
    // Numerical tolerance to handle ties when calibrated threshold equals many probabilities
    private static final double DECISION_EPS = 1e-9;

    private DatasetUtilities() {}

    /**
     * Loads an ARFF file and returns it as a Weka Instances object.
     * 
     * @param filePath Path to the ARFF file to load
     * @return Instances object containing the loaded data
     * @throws IOException If file loading fails
     */
    public static Instances loadArff(final String filePath) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("Loading ARFF file from {}...", filePath);
        }
        final ArffLoader loader = new ArffLoader();
        loader.setSource(new File(filePath));
        Instances data = loader.getDataSet();
        if (log.isDebugEnabled()) {
            log.debug("Successfully loaded {} instances from {}.", data.numInstances(), filePath);
        }
        return data;
    }

    /**
     * Reads a CSV file and returns a list of CSV records.
     * 
     * @param filePath Path to the CSV file to read
     * @return List of CSV records
     * @throws IOException If file reading fails
     */
    public static List<CSVRecord> readCsv(final String filePath) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("Reading CSV file from {}...", filePath);
        }
        try (Reader reader = new FileReader(filePath);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {
            List<CSVRecord> records = parser.getRecords();
            if (log.isDebugEnabled()) {
                log.debug("Successfully read {} records from {}.", records.size(), filePath);
            }
            return records;
        }
    }

    /**
     * Counts the number of instances predicted as defective by a classifier.
     * 
     * @param model The trained classifier to use for prediction
     * @param data The dataset to classify
     * @return Number of instances predicted as defective
     */
    public static int countDefective(final Classifier model, final Instances data) {
        log.debug("Counting PREDICTED defective instances...");
        int defectiveCount = 0;
        final Attribute classAttribute = data.classAttribute();

        final Optional<Integer> buggyClassIndexOpt = findBuggyClassIndex(classAttribute);
        if (buggyClassIndexOpt.isEmpty()) {
            log.warn("Could not find a 'buggy' class label ('yes' or '1'). Returning 0 predicted defects.");
            return 0;
        }
        final int buggyClassIndex = buggyClassIndexOpt.get();

        // DEBUG: Log some prediction probabilities for first few instances
        if (data.numInstances() > 0) {
            log.info("DEBUG: Analyzing first 5 instances for prediction probabilities:");
            for (int i = 0; i < Math.min(5, data.numInstances()); i++) {
                try {
                    double[] distribution = model.distributionForInstance(data.instance(i));
                    double predictedClass = model.classifyInstance(data.instance(i));
                log.info("Instance {}: actual_class={}, predicted_class={}, prob_no={}, prob_yes={}", 
                    i, data.instance(i).classValue(), predictedClass, 
                    String.format("%.3f", distribution[0]), String.format("%.3f", distribution[1]));
                } catch (Exception e) {
                    log.info("Could not get distribution for instance {}: {}", i, e.getMessage());
                }
            }
        }

        for (final Instance instance : data) {
            try {
                final double[] distribution = model.distributionForInstance(instance);
                final double probYes = distribution[buggyClassIndex];
                if (probYes + DECISION_EPS >= PREDICTION_YES_THRESHOLD) {
                    defectiveCount++;
                }
            } catch (Exception e) {
                log.warn("Could not classify instance. Skipping. Reason: {}", e.getMessage());
            }
        }
        log.debug("Found {} PREDICTED defective instances out of {} total (threshold={}).", defectiveCount, data.numInstances(), PREDICTION_YES_THRESHOLD);
        return defectiveCount;
    }

    /**
     * Counts predicted defective instances using a custom probability threshold.
     */
    public static int countDefective(final Classifier model, final Instances data, final double yesThreshold) {
        int defectiveCount = 0;
        final Attribute classAttribute = data.classAttribute();
        final Optional<Integer> buggyClassIndexOpt = findBuggyClassIndex(classAttribute);
        if (buggyClassIndexOpt.isEmpty()) return 0;
        final int buggyClassIndex = buggyClassIndexOpt.get();
        for (final Instance instance : data) {
            try {
                final double[] distribution = model.distributionForInstance(instance);
                final double probYes = distribution[buggyClassIndex];
                if (probYes + DECISION_EPS >= yesThreshold) defectiveCount++;
            } catch (Exception ignored) { }
        }
        return defectiveCount;
    }

    /**
     * Computes an optimal decision threshold on dataset with ground truth by maximizing Youden's J.
     * Returns 0.5 if computation fails.
     */
    public static double computeOptimalYesThreshold(final Classifier model, final Instances labeledData) {
        try {
            final Attribute classAttribute = labeledData.classAttribute();
            final Optional<Integer> buggyClassIndexOpt = findBuggyClassIndex(classAttribute);
            if (buggyClassIndexOpt.isEmpty()) return 0.5;
            final int buggyIdx = buggyClassIndexOpt.get();

            // Collect probabilities and true labels
            final int n = labeledData.numInstances();
            final double[] probs = new double[n];
            final int[] labels = new int[n];
            int pos = 0, neg = 0;
            for (int i = 0; i < n; i++) {
                final Instance inst = labeledData.instance(i);
                labels[i] = (inst.classValue() == buggyIdx) ? 1 : 0;
                if (labels[i] == 1) pos++; else neg++;
                try {
                    final double[] dist = model.distributionForInstance(inst);
                    probs[i] = dist[buggyIdx];
                } catch (Exception e) {
                    probs[i] = 0.0;
                }
            }
            if (pos == 0 || neg == 0) return 0.5;

            // Build sorted unique probability list
            final double[] uniq = java.util.Arrays.stream(probs).distinct().sorted().toArray();
            if (uniq.length <= 1) return 0.5;

            // Evaluate thresholds at midpoints between adjacent unique probabilities
            double bestT = 0.5; double bestJ = -1.0;
            for (int i = 0; i < uniq.length - 1; i++) {
                final double t = (uniq[i] + uniq[i + 1]) / 2.0;
                int tp = 0, fp = 0;
                for (int j = 0; j < n; j++) {
                    final boolean predPos = probs[j] >= t;
                    if (predPos && labels[j] == 1) tp++;
                    else if (predPos) fp++;
                }
                final double tpr = pos > 0 ? (double) tp / pos : 0.0; // recall
                final double fpr = neg > 0 ? (double) fp / neg : 0.0;
                final double J = tpr - fpr; // Youden's J statistic
                if (J > bestJ) { bestJ = J; bestT = t; }
            }
            return bestT;
        } catch (Exception e) {
            return 0.5;
        }
    }

    /**
     * Counts the number of instances that are actually defective (ground truth).
     * 
     * @param data The dataset to analyze
     * @return Number of instances that are actually defective
     */
    public static int countActualDefective(Instances data) {
        log.debug("Counting ACTUAL defective instances...");
        int defectiveCount = 0;
        final Attribute classAttribute = data.classAttribute();

        final Optional<Integer> buggyClassIndexOpt = findBuggyClassIndex(classAttribute);
        if (buggyClassIndexOpt.isEmpty()) {
            log.warn("Could not find a 'buggy' class label ('yes' or '1'). Returning 0 actual defects.");
            return 0;
        }
        final double buggyClassIndex = buggyClassIndexOpt.get();

        for (final Instance instance : data) {
            if (instance.classValue() == buggyClassIndex) {
                defectiveCount++;
            }
        }
        log.debug("Found {} ACTUAL defective instances.", defectiveCount);
        return defectiveCount;
    }
    
    /**
     * Finds the index of the "buggy" class value in a class attribute.
     * 
     * @param classAttribute The class attribute to search in
     * @return Optional containing the buggy class index or empty if not found
     */
    private static Optional<Integer> findBuggyClassIndex(Attribute classAttribute) {
        int index = classAttribute.indexOfValue("yes");
        if (index != -1) {
            return Optional.of(index);
        }
        index = classAttribute.indexOfValue("1");
        if (index != -1) {
            return Optional.of(index);
        }
        return Optional.empty();
    }
    
    /**
     * Filters instances based on a numeric attribute condition.
     * 
     * @param data The dataset to filter
     * @param attributeName Name of the attribute to filter on
     * @param operator The comparison operator (">", "<=", etc.)
     * @param value The value to compare against
     * @return Filtered dataset containing only instances that meet the condition
     */
    public static Instances filterInstances(final Instances data, final String attributeName, final String operator, final double value) {
        if (log.isDebugEnabled()) {
            log.debug("Filtering {} instances where attribute '{}' {} {}...", data.numInstances(), attributeName, operator, value);
        }
        final Instances filtered = new Instances(data, 0);
        final Attribute attribute = data.attribute(attributeName);
        if (attribute == null) {
            log.warn("Attribute '{}' not found for filtering. Returning empty dataset.", attributeName);
            return filtered;
        }
        
        for (final Instance instance : data) {
            final double attrValue = instance.value(attribute);
            boolean conditionMet = false;
            switch (operator) {
                case ">":
                    if (attrValue > value) conditionMet = true;
                    break;
                case "<=":
                    if (attrValue <= value) conditionMet = true;
                    break;
                default:
                    // Operator not supported
                    break;
            }
            
            if (conditionMet) {
                filtered.add(instance);
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("Filtering complete. Result: {} instances.", filtered.numInstances());
        }
        return filtered;
    }
}