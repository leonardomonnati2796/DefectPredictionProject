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

public final class DataHelper {

    // --- MODIFICATION HERE ---
    // Added a logger instance.
    private static final Logger log = LoggerFactory.getLogger(DataHelper.class);

    private DataHelper() {}

    public static Instances loadArff(final String filePath) throws IOException {
        final ArffLoader loader = new ArffLoader();
        loader.setSource(new File(filePath));
        return loader.getDataSet();
    }

    public static List<CSVRecord> readCsv(final String filePath) throws IOException {
        try (Reader reader = new FileReader(filePath);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {
            return parser.getRecords();
        }
    }

    public static int countDefective(final Classifier model, final Instances data) {
        int defectiveCount = 0;
        final Attribute classAttribute = data.classAttribute();

        final Optional<Integer> buggyClassIndexOpt = findBuggyClassIndex(classAttribute);
        if (buggyClassIndexOpt.isEmpty()) {
            return 0; // No "buggy" class found
        }
        final double buggyClassIndex = buggyClassIndexOpt.get();

        for (final Instance instance : data) {
            try {
                if (model.classifyInstance(instance) == buggyClassIndex) {
                    defectiveCount++;
                }
            } catch (Exception e) {
                // --- MODIFICATION HERE ---
                // Replaced System.err with a logger warning.
                log.warn("Could not classify instance. Skipping. Reason: {}", e.getMessage());
            }
        }
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
        final Instances filtered = new Instances(data, 0);
        final Attribute attribute = data.attribute(attributeName);
        if (attribute == null) {
            return filtered; // Return empty set if attribute doesn't exist
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
                    // Operator not supported, do nothing
                    break;
            }
            
            if (conditionMet) {
                filtered.add(instance);
            }
        }
        return filtered;
    }
}