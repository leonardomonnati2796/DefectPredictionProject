package com.ispw2;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigurationManager {
    private static final Logger log = LoggerFactory.getLogger(ConfigurationManager.class);
    private static final String CONFIG_FILE = "/config.properties";
    private static ConfigurationManager instance;
    private final Properties properties;

    private ConfigurationManager() {
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

    public static synchronized ConfigurationManager getInstance() {
        if (instance == null) {
            instance = new ConfigurationManager();
        }
        return instance;
    }

    public double getReleaseCutoffPercentage() {
        return Double.parseDouble(properties.getProperty("analysis.release_cutoff_percentage", "0.5"));
    }

    public List<String> getActionableFeatures() {
        final String features = properties.getProperty("analysis.actionable_features", "LOC,CyclomaticComplexity");
        return Arrays.asList(features.split(","));
    }
    
    public int getFeaturesToSelect() {
        return Integer.parseInt(properties.getProperty("preprocessing.features_to_select", "5"));
    }
    
    public String[] getIbkTuningParams() {
        final String range = properties.getProperty("tuner.ibk.k_range", "1 10 1");
        return ("K " + range).split(" ");
    }
    
    public String[] getRandomForestTuningParams() {
        final String range = properties.getProperty("tuner.randomforest.iterations_range", "10 100 10");
        return ("I " + range).split(" ");
    }
}