package com.ispw2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Manages configuration settings for the defect prediction system.
 * Loads configuration from properties files and provides access to various system parameters.
 */
public class ConfigurationManager {
    private static final Logger log = LoggerFactory.getLogger(ConfigurationManager.class);
    private static final String CONFIG_FILE = "/config.properties";

    private static final String KEY_RELEASE_CUTOFF = "analysis.release_cutoff_percentage";
    private static final String KEY_ACTIONABLE_FEATURES = "analysis.actionable_features";
    private static final String KEY_FEATURES_TO_SELECT = "preprocessing.features_to_select";
    private static final String KEY_IBK_K_RANGE = "tuner.ibk.k_range";
    private static final String KEY_RANDOMFOREST_ITERATIONS = "tuner.randomforest.iterations_range";
    
    private final Properties properties;

    /**
     * Constructs a new ConfigurationManager and loads configuration from the properties file.
     * Initializes the configuration system and loads all available settings.
     */
    public ConfigurationManager() {
        log.debug("Initializing ConfigurationManager and loading properties...");
        properties = new Properties();
        try (InputStream input = ConfigurationManager.class.getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                log.error("Unable to find {}. Please ensure it is in the src/main/resources folder.", CONFIG_FILE);
                return;
            }
            properties.load(input);
            log.info("Configuration properties loaded successfully from {}.", CONFIG_FILE);
        } catch (final IOException e) {
            log.error("Error loading configuration file", e);
        }
    }

    /**
     * Gets the percentage of releases to analyze from the configuration.
     * 
     * @return The release cutoff percentage (default: 0.5)
     */
    public double getReleaseCutoffPercentage() {
        return Double.parseDouble(properties.getProperty(KEY_RELEASE_CUTOFF, "0.5"));
    }

    /**
     * Gets the list of actionable features from the configuration.
     * 
     * @return List of actionable feature names (default: "CodeSmells,CyclomaticComplexity")
     */
    public List<String> getActionableFeatures() {
        final String features = properties.getProperty(KEY_ACTIONABLE_FEATURES, "CodeSmells,CyclomaticComplexity");
        return Arrays.asList(features.split(","));
    }
    
    /**
     * Gets the number of features to select during preprocessing.
     * 
     * @return The number of features to select (default: 5)
     */
    public int getFeaturesToSelect() {
        return Integer.parseInt(properties.getProperty(KEY_FEATURES_TO_SELECT, "5"));
    }
    
    /**
     * Gets the IBk (k-Nearest Neighbors) tuning parameters from the configuration.
     * 
     * @return Array of IBk tuning parameters (default: "K 1 10 1")
     */
    public String[] getIbkTuningParams() {
        final String range = properties.getProperty(KEY_IBK_K_RANGE, "1 10 1");
        return ("K " + range).split(" ");
    }
    
    /**
     * Gets the RandomForest tuning parameters from the configuration.
     * 
     * @return Array of RandomForest tuning parameters (default: "I 10 100 10")
     */
    public String[] getRandomForestTuningParams() {
        final String range = properties.getProperty(KEY_RANDOMFOREST_ITERATIONS, "10 100 10");
        return ("I " + range).split(" ");
    }
}