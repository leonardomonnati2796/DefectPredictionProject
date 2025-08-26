package com.ispw2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class ConfigurationManager {
    private static final Logger log = LoggerFactory.getLogger(ConfigurationManager.class);
    private static final String CONFIG_FILE = "/config.properties";

    // Property keys as constants
    private static final String KEY_RELEASE_CUTOFF = "analysis.release_cutoff_percentage";
    private static final String KEY_ACTIONABLE_FEATURES = "analysis.actionable_features";
    private static final String KEY_FEATURES_TO_SELECT = "preprocessing.features_to_select";
    private static final String KEY_IBK_K_RANGE = "tuner.ibk.k_range";
    private static final String KEY_RANDOMFOREST_ITERATIONS = "tuner.randomforest.iterations_range";
    
    private final Properties properties;

    public ConfigurationManager() {
        properties = new Properties();
        try (InputStream input = ConfigurationManager.class.getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                log.error("Unable to find {}. Please ensure it is in the src/main/resources folder.", CONFIG_FILE);
                return;
            }
            properties.load(input);
        } catch (final IOException e) {
            log.error("Error loading configuration file", e);
        }
    }

    // Il metodo getInstance() viene rimosso.
    // I metodi per ottenere le proprietà rimangono invariati ma non sono più statici.

    public double getReleaseCutoffPercentage() {
        return Double.parseDouble(properties.getProperty(KEY_RELEASE_CUTOFF, "0.5"));
    }

    public List<String> getActionableFeatures() {
        final String features = properties.getProperty(KEY_ACTIONABLE_FEATURES, "LOC,CyclomaticComplexity");
        return Arrays.asList(features.split(","));
    }
    
    public int getFeaturesToSelect() {
        return Integer.parseInt(properties.getProperty(KEY_FEATURES_TO_SELECT, "5"));
    }
    
    public String[] getIbkTuningParams() {
        final String range = properties.getProperty(KEY_IBK_K_RANGE, "1 10 1");
        return ("K " + range).split(" ");
    }
    
    public String[] getRandomForestTuningParams() {
        final String range = properties.getProperty(KEY_RANDOMFOREST_ITERATIONS, "10 100 10");
        return ("I " + range).split(" ");
    }
}