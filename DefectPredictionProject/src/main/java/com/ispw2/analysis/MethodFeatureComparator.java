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

            log.info("Feature comparison results (only actionable features):");
            log.info(TABLE_HEADER);
            log.info(String.join("", java.util.Collections.nCopies(74, "-")));

            // Define actionable features (could be externalized if needed)
            final java.util.List<String> actionable = java.util.Arrays.asList("CyclomaticComplexity");

            // Prepare CSV output
            final java.util.List<String> csvLines = new java.util.ArrayList<>();
            csvLines.add("Feature,Original,Refactored,Improvement");

            for (final String feature : actionable) {
                final Double originalValue = originalFeatures.get(feature);
                final Double refactoredValue = refactoredFeatures.get(feature);

                final String originalStr = formatValue(originalValue);
                final String refactoredStr = formatValue(refactoredValue);

                String improvementStr;
                if (originalValue != null && refactoredValue != null) {
                    final double improvement = originalValue - refactoredValue;
                    improvementStr = String.format(java.util.Locale.US, "%.2f", improvement);
                } else {
                    improvementStr = "n/a";
                }

                log.info(String.format(TABLE_ROW_FORMAT, feature, parseOrZero(originalStr), parseOrZero(refactoredStr), parseOrZero(improvementStr))
                        .replace("NaN", "   n/a"));

                // CSV line preserves n/a when values are missing
                csvLines.add(String.format("%s,%s,%s,%s", feature, originalStr, refactoredStr, improvementStr));
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
     * Extracts features from both source code comments (e.g., "ORIGINAL: CyclomaticComplexity = 25")
     * and from structured format lines (e.g., "CyclomaticComplexity: 25").
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
        final int loc = lines.length;
        final double computedCc = calculateCyclomaticComplexity(content);
        
        boolean hasRefactoredTag = false;
        final Map<String, Double> originalFromComments = new HashMap<>();
        final Map<String, Double> refactoredFromComments = new HashMap<>();
        
        for (final String line : lines) {
            // Pattern: "* ORIGINAL: FeatureName = value" or "* REFACTORED: FeatureName = value"
            if (line.contains("ORIGINAL:")) {
                final String[] parts = line.split("=");
                if (parts.length >= 2) {
                    try {
                        final String beforeEquals = parts[0];
                        final String featureName = beforeEquals.substring(beforeEquals.indexOf("ORIGINAL:") + 9).trim();
                        
                        final String afterEquals = parts[1].trim();
                        final String valueStr = afterEquals.split("\\s+")[0].trim();
                        final double value = Double.parseDouble(valueStr);
                        
                        originalFromComments.put(featureName, value);
                    } catch (final Exception e) {
                        log.debug("Could not parse ORIGINAL feature from line: {}", line);
                    }
                }
            }
            
            if (line.contains("REFACTORED:")) {
                hasRefactoredTag = true;
                final String[] parts = line.split("=");
                if (parts.length >= 2) {
                    try {
                        final String beforeEquals = parts[0];
                        final String featureName = beforeEquals.substring(beforeEquals.indexOf("REFACTORED:") + 11).trim();
                        
                        final String afterEquals = parts[1].trim();
                        final String valueStr = afterEquals.split("\\s+")[0].trim();
                        final double value = Double.parseDouble(valueStr);
                        
                        refactoredFromComments.put(featureName, value);
                    } catch (final Exception e) {
                        log.debug("Could not parse REFACTORED feature from line: {}", line);
                    }
                }
            }
            
            // Also try standard format: "FeatureName: value"
            if (line.contains(":") && !line.contains("//") && !line.contains("*")) {
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
        
        // Merge features: prefer REFACTORED values if available, otherwise use calculated values
        if (hasRefactoredTag && !refactoredFromComments.isEmpty()) {
            features.putAll(refactoredFromComments);
            // Also calculate metrics not in metadata
            if (!features.containsKey("LOC")) {
                features.put("LOC", (double) loc);
            }
            // If CC is missing or zeroed in metadata, recompute from code to avoid stale values.
            final Double cc = features.get("CyclomaticComplexity");
            if (cc == null || cc <= 0) {
                features.put("CyclomaticComplexity", computedCc);
            }
        } else if (!originalFromComments.isEmpty()) {
            features.putAll(originalFromComments);
            // Also calculate metrics not in metadata
            if (!features.containsKey("LOC")) {
                features.put("LOC", (double) loc);
            }
            if (!features.containsKey("CyclomaticComplexity")) {
                features.put("CyclomaticComplexity", computedCc);
            }
        } else {
            // If no features found in comments, calculate basic metrics from code
            log.debug("No metadata found, calculating basic metrics for: {}", filePath);
            features.put("CyclomaticComplexity", computedCc);
            features.put("LOC", (double) loc);
        }

        return features;
    }
    
    /**
     * Calculates basic cyclomatic complexity by counting decision points.
     * 
     * @param code The source code to analyze
     * @return Estimated cyclomatic complexity
     */
    private double calculateCyclomaticComplexity(final String code) {
        int complexity = 1; // Base complexity
        
        // Count decision points
        complexity += countOccurrences(code, "if ");
        complexity += countOccurrences(code, "else if");
        complexity += countOccurrences(code, "for ");
        complexity += countOccurrences(code, "while ");
        complexity += countOccurrences(code, "case ");
        complexity += countOccurrences(code, "catch ");
        complexity += countOccurrences(code, "&&");
        complexity += countOccurrences(code, "||");
        complexity += countOccurrences(code, "?"); // ternary operator
        
        return complexity;
    }
    
    /**
     * Counts occurrences of a substring in a string.
     * 
     * @param str The string to search in
     * @param substr The substring to count
     * @return Number of occurrences
     */
    private int countOccurrences(final String str, final String substr) {
        int count = 0;
        int index = 0;
        while ((index = str.indexOf(substr, index)) != -1) {
            count++;
            index += substr.length();
        }
        return count;
    }

    /**
     * Formats a Double value to two decimals, or returns "n/a" if null.
     */
    private String formatValue(final Double value) {
        return value == null ? "n/a" : String.format(java.util.Locale.US, "%.2f", value);
    }

    /**
     * Parses a formatted string to double, or returns 0 if not a number (used only for aligned logging).
     */
    private double parseOrZero(final String formatted) {
        try {
            return Double.parseDouble(formatted);
        } catch (final Exception e) {
            return Double.NaN;
        }
    }
}
