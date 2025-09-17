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

public final class DatasetUtilities {

    private static final Logger log = LoggerFactory.getLogger(DatasetUtilities.class);

    private DatasetUtilities() {}

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

    public static int countDefective(final Classifier model, final Instances data) {
        log.debug("Counting PREDICTED defective instances...");
        int defectiveCount = 0;
        final Attribute classAttribute = data.classAttribute();

        final Optional<Integer> buggyClassIndexOpt = findBuggyClassIndex(classAttribute);
        if (buggyClassIndexOpt.isEmpty()) {
            log.warn("Could not find a 'buggy' class label ('yes' or '1'). Returning 0 predicted defects.");
            return 0;
        }
        final double buggyClassIndex = buggyClassIndexOpt.get();

        for (final Instance instance : data) {
            try {
                if (model.classifyInstance(instance) == buggyClassIndex) {
                    defectiveCount++;
                }
            } catch (Exception e) {
                log.warn("Could not classify instance. Skipping. Reason: {}", e.getMessage());
            }
        }
        log.debug("Found {} PREDICTED defective instances.", defectiveCount);
        return defectiveCount;
    }

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