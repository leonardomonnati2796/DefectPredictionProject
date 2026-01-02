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
    
    // Constants for string formatting
    private static final String FEATURE_COMPARISON_FORMAT = "  %s: %.2f -> %.2f (improvement: %.2f)";
    private static final String TABLE_HEADER = String.format("%-30s | %12s | %12s | %12s", "Feature", "Original", "Refactored", "Improvement");
    private static final String TABLE_ROW_FORMAT = "%-30s | %12.2f | %12.2f | %12.2f";

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
            log.info(TABLE_HEADER);
            log.info(String.join("", java.util.Collections.nCopies(74, "-")));
            // Also prepare CSV output
            final java.util.List<String> csvLines = new java.util.ArrayList<>();
            csvLines.add("Feature,Original,Refactored,Improvement");

            for (final String feature : originalFeatures.keySet()) {
                final double originalValue = originalFeatures.getOrDefault(feature, 0.0);
                final double refactoredValue = refactoredFeatures.getOrDefault(feature, 0.0);
                final double improvement = originalValue - refactoredValue;

                final String row = String.format(TABLE_ROW_FORMAT, feature, originalValue, refactoredValue, improvement);
                log.info(row);

                // CSV line
                csvLines.add(String.format(java.util.Locale.US, "%s,%.2f,%.2f,%.2f", feature, originalValue, refactoredValue, improvement));
            }

            // Write CSV to datasets folder if possible
            try {
                final java.nio.file.Path outDir = java.nio.file.Paths.get("datasets");
                if (!java.nio.file.Files.exists(outDir)) {
                    java.nio.file.Files.createDirectories(outDir);
                }
                final java.nio.file.Path outFile = outDir.resolve("method_feature_comparison.csv");
                final java.nio.file.Path tmpFile = outDir.resolve("method_feature_comparison.csv.tmp");

                // Retry a few times in case file is temporarily locked by another process
                final int maxAttempts = 3;
                int attempt = 0;
                boolean written = false;
                while (attempt < maxAttempts && !written) {
                    attempt++;
                    try {
                        java.nio.file.Files.write(tmpFile, csvLines, java.nio.charset.StandardCharsets.UTF_8,
                                java.nio.file.StandardOpenOption.CREATE,
                                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                        try {
                            java.nio.file.Files.move(tmpFile, outFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        } catch (java.nio.file.AtomicMoveNotSupportedException amnse) {
                            // fallback to non-atomic move
                            java.nio.file.Files.move(tmpFile, outFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                        written = true;
                        log.info("Feature comparison CSV written to: {}", outFile.toString());
                    } catch (final IOException ioe) {
                        if (attempt >= maxAttempts) {
                            log.warn("Could not write feature comparison CSV after {} attempts: {}", attempt, ioe.getMessage());
                            log.debug("Stacktrace:", ioe);
                            // Try fallback: write to unique timestamped file to avoid locked target
                            try {
                                final String ts = String.valueOf(System.currentTimeMillis());
                                final java.nio.file.Path altFile = outDir.resolve("method_feature_comparison_" + ts + ".csv");
                                java.nio.file.Files.write(altFile, csvLines, java.nio.charset.StandardCharsets.UTF_8,
                                        java.nio.file.StandardOpenOption.CREATE_NEW);
                                log.info("Feature comparison CSV written to fallback file: {}", altFile.toString());
                                written = true;
                            } catch (final IOException altEx) {
                                log.warn("Fallback write also failed: {}", altEx.getMessage());
                                log.debug("Fallback stacktrace:", altEx);
                            }
                        } else {
                            try {
                                Thread.sleep(200);
                            } catch (final InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }
                // cleanup tmp file if it still exists
                try {
                    if (java.nio.file.Files.exists(tmpFile)) {
                        java.nio.file.Files.deleteIfExists(tmpFile);
                    }
                } catch (final IOException ignore) {
                    // ignore cleanup failures
                }
            } catch (final Exception e) {
                log.warn("Could not write feature comparison CSV: {}", e.getMessage());
                log.debug("Stacktrace:", e);
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
