package com.ispw2.preprocessing;

import com.ispw2.ConfigurationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

public class DatasetPreprocessor {

    private static final Logger log = LoggerFactory.getLogger(DatasetPreprocessor.class);

    private final ConfigurationManager config;
    private final String inputCsvPath;
    private final String outputArffPath;

    private static final String PROJECT_COLUMN = "Project";
    private static final String METHOD_NAME_COLUMN = "MethodName";
    private static final String RELEASE_COLUMN = "Release";
    private static final String IS_BUGGY_COLUMN = "IsBuggy";
    
    private static final String REMOVE_INDICES = "1-3";

    /**
     * Constructs a new DatasetPreprocessor for converting CSV data to ARFF format.
     * 
     * @param config Configuration manager with system settings
     * @param inputCsvPath Path to the input CSV file
     * @param outputArffPath Path where the processed ARFF file will be saved
     */
    public DatasetPreprocessor(ConfigurationManager config, final String inputCsvPath, final String outputArffPath) {
        this.config = config;
        this.inputCsvPath = inputCsvPath;
        this.outputArffPath = outputArffPath;
    }

    /**
     * Processes the CSV data through multiple preprocessing steps including
     * attribute removal, missing value imputation, normalization, feature selection, and format conversion.
     * 
     * @throws Exception If any preprocessing step fails
     */
    public void processData() throws Exception {
        log.info("Starting data preprocessing for {}...", this.inputCsvPath);
        final Instances rawData = loadCsvManually(this.inputCsvPath);
        rawData.setClassIndex(rawData.numAttributes() - 1);
        log.debug("Loaded {} raw instances from CSV.", rawData.numInstances());

        final Remove removeFilter = new Remove();
        removeFilter.setAttributeIndices(REMOVE_INDICES); 
        removeFilter.setInputFormat(rawData);
        final Instances dataWithoutIds = Filter.useFilter(rawData, removeFilter);
        log.debug("Instances after removing ID attributes: {}", dataWithoutIds.numInstances());

        final ReplaceMissingValues missingValuesFilter = new ReplaceMissingValues();
        missingValuesFilter.setInputFormat(dataWithoutIds);
        final Instances dataImputed = Filter.useFilter(dataWithoutIds, missingValuesFilter);
        log.debug("Instances after imputing missing values: {}", dataImputed.numInstances());
        
        final Normalize normalizeFilter = new Normalize();
        normalizeFilter.setInputFormat(dataImputed);
        final Instances dataNormalized = Filter.useFilter(dataImputed, normalizeFilter);
        log.debug("Instances after normalization: {}", dataNormalized.numInstances());

        final AttributeSelection attributeSelectionFilter = new AttributeSelection();
        attributeSelectionFilter.setEvaluator(new InfoGainAttributeEval());
        final Ranker search = new Ranker();
        search.setNumToSelect(config.getFeaturesToSelect());
        attributeSelectionFilter.setSearch(search);
        attributeSelectionFilter.setInputFormat(dataNormalized);
        final Instances dataSelected = Filter.useFilter(dataNormalized, attributeSelectionFilter);
        log.debug("Instances after feature selection ({} features): {}", dataSelected.numAttributes() - 1, dataSelected.numInstances());

        final NominalToBinary nominalToBinaryFilter = new NominalToBinary();
        nominalToBinaryFilter.setInputFormat(dataSelected);
        final Instances finalData = Filter.useFilter(dataSelected, nominalToBinaryFilter);
        log.debug("Instances after NominalToBinary filter: {}", finalData.numInstances());
        
        saveToArff(finalData, this.outputArffPath);

        // If minority class ("yes") < 20%, also generate a balanced dataset variant using oversampling
        try {
            final int yesIndex = finalData.classAttribute().indexOfValue("yes");
            if (yesIndex != -1) {
                int yesCount = 0;
                for (int i = 0; i < finalData.numInstances(); i++) {
                    if ((int) finalData.instance(i).classValue() == yesIndex) yesCount++;
                }
                double minorityRatio = (finalData.numInstances() > 0) ? ((double) yesCount) / finalData.numInstances() : 0.0;
                log.info("Minority class ratio (yes): {}%", String.format(Locale.US, "%.2f", minorityRatio * 100));
                if (minorityRatio < 0.20) {
                    // Simple random oversampling of minority to roughly 30%
                    final Instances balanced = new Instances(finalData);
                    final java.util.Random rnd = new java.util.Random(42);
                    final int targetYes = Math.max(1, (int) Math.round(0.3 * finalData.numInstances()));
                    final List<weka.core.Instance> yesInstances = new ArrayList<>();
                    for (int i = 0; i < finalData.numInstances(); i++) {
                        if ((int) finalData.instance(i).classValue() == yesIndex) {
                            yesInstances.add(finalData.instance(i));
                        }
                    }
                    while (yesInstances.size() > 0 && yesCount < targetYes) {
                        final weka.core.Instance sample = yesInstances.get(rnd.nextInt(yesInstances.size()));
                        balanced.add(sample);
                        yesCount++;
                    }
                    saveToArff(balanced, this.outputArffPath.replace(".arff", "_balanced.arff"));
                    log.info("Balanced dataset written to {}", this.outputArffPath.replace(".arff", "_balanced.arff"));
                }
            }
        } catch (Exception balanceEx) {
            log.warn("Could not generate balanced dataset variant: {}", balanceEx.getMessage());
        }
        log.info("Preprocessing complete. Final dataset has {} instances and {} attributes.", finalData.numAttributes(), finalData.numInstances());
    }

    /**
     * Loads CSV data manually and converts it to Weka Instances format.
     * 
     * @param csvPath Path to the CSV file to load
     * @return Instances object containing the loaded data
     * @throws IOException If file reading fails
     */
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

    /**
     * Defines Weka attributes based on CSV headers, setting appropriate data types.
     * 
     * @param headers List of column headers from the CSV file
     * @return List of Weka Attribute objects
     */
    private ArrayList<Attribute> defineWekaAttributes(final List<String> headers) {
        final ArrayList<Attribute> attributes = new ArrayList<>();
        for (final String header : headers) {
            switch(header) {
                case PROJECT_COLUMN:
                case METHOD_NAME_COLUMN:
                case RELEASE_COLUMN:
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

    /**
     * Populates a Weka instance with data from a CSV record, handling different data types.
     * 
     * @param instance The Weka instance to populate
     * @param attribute The attribute being set
     * @param value The string value from the CSV record
     */
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

    /**
     * Saves the processed data to an ARFF file.
     * 
     * @param data The processed Instances data to save
     * @param filePath The path where the ARFF file should be saved
     * @throws IOException If file writing fails
     */
    private void saveToArff(final Instances data, final String filePath) throws IOException {
        final ArffSaver saver = new ArffSaver();
        saver.setInstances(data);
        saver.setFile(new File(filePath));
        saver.writeBatch();
    }
}