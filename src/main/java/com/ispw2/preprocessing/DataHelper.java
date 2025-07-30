package com.ispw2.preprocessing;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.converters.ArffLoader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.List;

public final class DataHelper {

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

    public static int countDefective(final Classifier model, final Instances data) throws Exception {
        int defectiveCount = 0;
        final Attribute classAttribute = data.classAttribute();
        double buggyClassIndex = -1.0;
        if (classAttribute.indexOfValue("yes") != -1) {
            buggyClassIndex = classAttribute.indexOfValue("yes");
        } else if (classAttribute.indexOfValue("1") != -1) {
            buggyClassIndex = classAttribute.indexOfValue("1");
        }
        
        if (buggyClassIndex < 0) return 0;

        for (int i = 0; i < data.numInstances(); i++) {
            if (model.classifyInstance(data.instance(i)) == buggyClassIndex) {
                defectiveCount++;
            }
        }
        return defectiveCount;
    }
    
    public static Instances filterInstances(final Instances data, final String attributeName, final String operator, final double value) {
        final Instances filtered = new Instances(data, 0);
        final Attribute attribute = data.attribute(attributeName);
        for (int i = 0; i < data.numInstances(); i++) {
            final double attrValue = data.instance(i).value(attribute);
            boolean conditionMet = false;
            if (">".equals(operator) && attrValue > value) conditionMet = true;
            if ("<=".equals(operator) && attrValue <= value) conditionMet = true;
            
            if (conditionMet) {
                filtered.add(data.instance(i));
            }
        }
        return filtered;
    }
}