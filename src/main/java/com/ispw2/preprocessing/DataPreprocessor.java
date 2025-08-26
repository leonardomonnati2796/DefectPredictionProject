package com.ispw2.preprocessing;

import com.ispw2.ConfigurationManager;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import weka.attributeSelection.InfoGainAttributeEval;
import weka.attributeSelection.Ranker;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.converters.ArffSaver;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.filters.unsupervised.attribute.NominalToBinary;
import weka.filters.unsupervised.attribute.Normalize;
import weka.filters.unsupervised.attribute.Remove;
import weka.filters.unsupervised.attribute.ReplaceMissingValues;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class DataPreprocessor {
    private final ConfigurationManager config;
    private final String inputCsvPath;
    private final String outputArffPath;

    private static final String PROJECT_COLUMN = "Project";
    private static final String METHOD_NAME_COLUMN = "MethodName";
    private static final String RELEASE_COLUMN = "Release";
    private static final String IS_BUGGY_COLUMN = "IsBuggy";
    
    private static final String REMOVE_INDICES = "1-3";

    public DataPreprocessor(ConfigurationManager config, final String inputCsvPath, final String outputArffPath) {
        this.config = config;
        this.inputCsvPath = inputCsvPath;
        this.outputArffPath = outputArffPath;
    }

    public void processData() throws Exception {
        final Instances rawData = loadCsvManually(this.inputCsvPath);
        rawData.setClassIndex(rawData.numAttributes() - 1);

        final Remove removeFilter = new Remove();
        removeFilter.setAttributeIndices(REMOVE_INDICES); 
        removeFilter.setInputFormat(rawData);
        final Instances dataWithoutIds = Filter.useFilter(rawData, removeFilter);

        final ReplaceMissingValues missingValuesFilter = new ReplaceMissingValues();
        missingValuesFilter.setInputFormat(dataWithoutIds);
        final Instances dataImputed = Filter.useFilter(dataWithoutIds, missingValuesFilter);
        
        final Normalize normalizeFilter = new Normalize();
        normalizeFilter.setInputFormat(dataImputed);
        final Instances dataNormalized = Filter.useFilter(dataImputed, normalizeFilter);

        final AttributeSelection attributeSelectionFilter = new AttributeSelection();
        attributeSelectionFilter.setEvaluator(new InfoGainAttributeEval());
        final Ranker search = new Ranker();
        search.setNumToSelect(config.getFeaturesToSelect());
        attributeSelectionFilter.setSearch(search);
        attributeSelectionFilter.setInputFormat(dataNormalized);
        final Instances dataSelected = Filter.useFilter(dataNormalized, attributeSelectionFilter);

        final NominalToBinary nominalToBinaryFilter = new NominalToBinary();
        nominalToBinaryFilter.setInputFormat(dataSelected);
        final Instances finalData = Filter.useFilter(dataSelected, nominalToBinaryFilter);
        
        saveToArff(finalData, this.outputArffPath);
    }

    private Instances loadCsvManually(final String csvPath) throws IOException {
        Locale.setDefault(Locale.US);
        final CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();
        try (Reader reader = new FileReader(csvPath);
             CSVParser parser = new CSVParser(reader, format)) {

            final List<String> headers = parser.getHeaderNames();
            final ArrayList<Attribute> attributes = defineWekaAttributes(headers);
            final Instances data = new Instances("Dataset", attributes, 0);

            for (final CSVRecord csvRecord : parser) {
                if (csvRecord.size() != headers.size()) continue;
                final DenseInstance instance = new DenseInstance(data.numAttributes());
                instance.setDataset(data);
                for (int i = 0; i < headers.size(); i++) {
                    populateWekaInstance(instance, data.attribute(i), csvRecord.get(i));
                }
                data.add(instance);
            }
            return data;
        }
    }

    private ArrayList<Attribute> defineWekaAttributes(final List<String> headers) {
        final ArrayList<Attribute> attributes = new ArrayList<>();
        for (final String header : headers) {
            switch(header) {
                case PROJECT_COLUMN, METHOD_NAME_COLUMN, RELEASE_COLUMN:
                    attributes.add(new Attribute(header, (List<String>) null));
                    break;
                case IS_BUGGY_COLUMN:
                    attributes.add(new Attribute(header, Arrays.asList("no", "yes")));
                    break;
                default:
                    attributes.add(new Attribute(header));
                    break;
            }
        }
        return attributes;
    }

    private void populateWekaInstance(final DenseInstance instance, final Attribute attribute, final String value) {
        if (value == null || value.isEmpty()) {
            instance.setMissing(attribute);
            return;
        }
        try {
            if (attribute.isNumeric()) {
                instance.setValue(attribute, Double.parseDouble(value));
            } else {
                instance.setValue(attribute, value);
            }
        } catch (final NumberFormatException e) {
            instance.setMissing(attribute);
        }
    }

    private void saveToArff(final Instances data, final String filePath) throws IOException {
        final ArffSaver saver = new ArffSaver();
        saver.setInstances(data);
        saver.setFile(new File(filePath));
        saver.writeBatch();
    }
}