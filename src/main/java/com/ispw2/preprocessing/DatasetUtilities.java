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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Utility class for loading and working with datasets in various formats.
 * Provides static methods for loading ARFF files and reading CSV data.
 */
public final class DatasetUtilities {

    private static final Logger log = LoggerFactory.getLogger(DatasetUtilities.class);

    private DatasetUtilities() {}

    /**
     * Loads an ARFF file and returns it as a Weka Instances object.
     * 
     * @param filePath Path to the ARFF file to load
     * @return Instances object containing the loaded data
     * @throws IOException If file loading fails
     */
    public static Instances loadArff(final String filePath) throws IOException {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }
        
        final File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("ARFF file does not exist: " + filePath);
        }
        
        if (log.isDebugEnabled()) {
            log.debug("Loading ARFF file from {}...", filePath);
        }
        
        try {
            final ArffLoader loader = new ArffLoader();
            loader.setSource(file);
            final Instances data = loader.getDataSet();
            
            if (log.isDebugEnabled()) {
                log.debug("Successfully loaded {} instances from {}.", data.numInstances(), filePath);
            }
            return data;
        } catch (Exception e) {
            throw new IOException("Failed to load ARFF file: " + filePath, e);
        }
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
        try (Reader reader = new FileReader(filePath, StandardCharsets.UTF_8);
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
        return countDefectiveInstances(data, "PREDICTED", instance -> model.classifyInstance(instance));
    }

    /**
     * Counts the number of instances that are actually defective (ground truth).
     * 
     * @param data The dataset to analyze
     * @return Number of instances that are actually defective
     */
    public static int countActualDefective(Instances data) {
        return countDefectiveInstances(data, "ACTUAL", instance -> instance.classValue());
    }
    
    /**
     * Counts defective instances using a provided classifier function.
     * 
     * @param data The dataset to analyze
     * @param type Type of counting (for logging)
     * @param classifier Function to determine if an instance is defective
     * @return Number of defective instances
     */
    private static int countDefectiveInstances(Instances data, String type, InstanceClassifier classifier) {
        log.debug("Counting {} defective instances...", type);
        int defectiveCount = 0;
        final Attribute classAttribute = data.classAttribute();

        final Optional<Integer> buggyClassIndexOpt = findBuggyClassIndex(classAttribute);
        if (buggyClassIndexOpt.isEmpty()) {
            log.warn("Could not find a 'buggy' class label ('yes' or '1'). Returning 0 {} defects.", type.toLowerCase());
            return 0;
        }
        final double buggyClassIndex = buggyClassIndexOpt.get();

        for (final Instance instance : data) {
            try {
                if (classifier.isDefective(instance) == buggyClassIndex) {
                    defectiveCount++;
                }
            } catch (Exception e) {
                log.warn("Could not classify instance. Skipping. Reason: {}", e.getMessage());
            }
        }
        log.debug("Found {} {} defective instances.", defectiveCount, type);
        return defectiveCount;
    }
    
    /**
     * Functional interface for classifying instances.
     */
    @FunctionalInterface
    private interface InstanceClassifier {
        double isDefective(Instance instance) throws Exception;
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