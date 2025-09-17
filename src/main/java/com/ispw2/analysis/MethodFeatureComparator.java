package com.ispw2.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Compares features between original and refactored methods.
 * Analyzes the impact of refactoring on code metrics.
 */
public class MethodFeatureComparator {
    private static final Logger log = LoggerFactory.getLogger(MethodFeatureComparator.class);

    /**
     * Compares the features of original and refactored methods.
     * 
     * @param originalMethodPath Path to the original method file
     * @param refactoredMethodPath Path to the refactored method file
     */
    public void compareMethods(final String originalMethodPath, final String refactoredMethodPath) {
        log.info("Comparing methods:");
        log.info("  Original: {}", originalMethodPath);
        log.info("  Refactored: {}", refactoredMethodPath);

        try {
            final Map<String, Double> originalFeatures = parseMethodFeatures(originalMethodPath);
            final Map<String, Double> refactoredFeatures = parseMethodFeatures(refactoredMethodPath);

            log.info("Feature comparison results:");
            for (final String feature : originalFeatures.keySet()) {
                final double originalValue = originalFeatures.getOrDefault(feature, 0.0);
                final double refactoredValue = refactoredFeatures.getOrDefault(feature, 0.0);
                final double improvement = originalValue - refactoredValue;
                
                final String message = String.format("  %s: %.2f -> %.2f (improvement: %.2f)", 
                        feature, originalValue, refactoredValue, improvement);
                log.info(message);
            }

        } catch (final IOException e) {
            log.error("Error comparing methods", e);
        }
    }

    /**
     * Parses method features from a file.
     * 
     * @param filePath Path to the method file
     * @return Map of feature names to values
     * @throws IOException If file reading fails
     */
    private Map<String, Double> parseMethodFeatures(final String filePath) throws IOException {
        final Map<String, Double> features = new HashMap<>();
        final Path path = Paths.get(filePath);
        
        if (!Files.exists(path)) {
            log.warn("Method file not found: {}", filePath);
            return features;
        }

        final String content = Files.readString(path);
        final String[] lines = content.split("\n");
        
        for (final String line : lines) {
            if (line.contains(":")) {
                final String[] parts = line.split(":");
                if (parts.length == 2) {
                    try {
                        final String featureName = parts[0].trim();
                        final double value = Double.parseDouble(parts[1].trim());
                        features.put(featureName, value);
                    } catch (final NumberFormatException e) {
                        log.debug("Skipping non-numeric line: {}", line);
                    }
                }
            }
        }

        return features;
    }
}
