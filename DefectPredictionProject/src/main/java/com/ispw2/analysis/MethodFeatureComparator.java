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
    private static final String CYCLOMATIC_COMPLEXITY = "CyclomaticComplexity";

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

            final java.util.List<String> csvLines = createComparisonData(originalFeatures, refactoredFeatures);
            writeCsvWithRetry(csvLines);

        } catch (final IOException e) {
            log.error("Error comparing methods", e);
        }
    }

    /**
     * Creates comparison data for features, logging results and building CSV lines.
     *
     * @param originalFeatures Features from the original method
     * @param refactoredFeatures Features from the refactored method
     * @return List of CSV lines including header
     */
    private java.util.List<String> createComparisonData(final Map<String, Double> originalFeatures, 
                                                         final Map<String, Double> refactoredFeatures) {
        if (log.isInfoEnabled()) {
            log.info("Feature comparison results (only actionable features):");
            log.info(TABLE_HEADER);
            log.info(String.join("", java.util.Collections.nCopies(74, "-")));
        }

        final java.util.List<String> actionable = java.util.Arrays.asList(CYCLOMATIC_COMPLEXITY);
        final java.util.List<String> csvLines = new java.util.ArrayList<>();
        csvLines.add("Feature,Original,Refactored,Improvement");

        for (final String feature : actionable) {
            final Double originalValue = originalFeatures.get(feature);
            final Double refactoredValue = refactoredFeatures.get(feature);

            final String originalStr = formatValue(originalValue);
            final String refactoredStr = formatValue(refactoredValue);
            final String improvementStr = calculateImprovement(originalValue, refactoredValue);

                if (log.isInfoEnabled()) {
                log.info(String.format(TABLE_ROW_FORMAT, feature, parseOrZero(originalStr), 
                    parseOrZero(refactoredStr), parseOrZero(improvementStr)).replace("NaN", "   n/a"));
                }

            csvLines.add(String.format("%s,%s,%s,%s", feature, originalStr, refactoredStr, improvementStr));
        }

        return csvLines;
    }

    /**
     * Calculates the improvement value between original and refactored features.
     *
     * @param originalValue Original feature value
     * @param refactoredValue Refactored feature value
     * @return Formatted improvement string or "n/a" if values are null
     */
    private String calculateImprovement(final Double originalValue, final Double refactoredValue) {
        if (originalValue != null && refactoredValue != null) {
            final double improvement = originalValue - refactoredValue;
            return String.format(java.util.Locale.US, "%.2f", improvement);
        }
        return "n/a";
    }

    /**
     * Writes CSV data to file with retry logic and fallback mechanisms.
     *
     * @param csvLines List of CSV lines to write
     */
    private void writeCsvWithRetry(final java.util.List<String> csvLines) {
        try {
            final java.nio.file.Path outDir = java.nio.file.Paths.get("datasets");
            if (!java.nio.file.Files.exists(outDir)) {
                java.nio.file.Files.createDirectories(outDir);
            }
            final java.nio.file.Path outFile = outDir.resolve("method_feature_comparison.csv");
            final java.nio.file.Path tmpFile = outDir.resolve("method_feature_comparison.csv.tmp");

            attemptWriteWithRetry(csvLines, outFile, tmpFile, outDir);
            cleanupTempFile(tmpFile);

        } catch (final Exception e) {
            log.warn("Could not write feature comparison CSV: {}", e.getMessage());
            log.debug("Stacktrace:", e);
        }
    }

    /**
     * Attempts to write CSV file with multiple retries.
     *
     * @param csvLines CSV data to write
     * @param outFile Target output file
     * @param tmpFile Temporary file for atomic write
     * @param outDir Output directory for fallback
     */
    private void attemptWriteWithRetry(final java.util.List<String> csvLines, 
                                       final java.nio.file.Path outFile,
                                       final java.nio.file.Path tmpFile,
                                       final java.nio.file.Path outDir) {
        final int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (tryWriteFile(csvLines, outFile, tmpFile, attempt, maxAttempts, outDir)) {
                return;
            }
            if (attempt < maxAttempts) {
                sleepBetweenAttempts();
            }
        }
    }

    /**
     * Tries to write the CSV file, returns true if successful.
     *
     * @param csvLines CSV data to write
     * @param outFile Target output file
     * @param tmpFile Temporary file for atomic write
     * @param attempt Current attempt number
     * @param maxAttempts Maximum number of attempts
     * @param outDir Output directory for fallback
     * @return true if write was successful, false otherwise
     */
    private boolean tryWriteFile(final java.util.List<String> csvLines,
                                 final java.nio.file.Path outFile,
                                 final java.nio.file.Path tmpFile,
                                 final int attempt,
                                 final int maxAttempts,
                                 final java.nio.file.Path outDir) {
        try {
            java.nio.file.Files.write(tmpFile, csvLines, java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            moveFileWithFallback(tmpFile, outFile);
            log.info("Feature comparison CSV written to: {}", outFile);
            return true;
        } catch (final IOException ioe) {
            if (attempt >= maxAttempts) {
                handleWriteFailure(csvLines, outDir, attempt, ioe);
                return true;
            }
            return false;
        }
    }

    /**
     * Moves file with fallback to non-atomic move if atomic is not supported.
     *
     * @param source Source file
     * @param target Target file
     * @throws IOException If move fails
     */
    private void moveFileWithFallback(final java.nio.file.Path source, final java.nio.file.Path target) throws IOException {
        try {
            java.nio.file.Files.move(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException amnse) {
            java.nio.file.Files.move(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Handles write failure by attempting to write to a timestamped fallback file.
     *
     * @param csvLines CSV data to write
     * @param outDir Output directory
     * @param attempt Number of failed attempts
     * @param ioe Original IOException
     */
    private void handleWriteFailure(final java.util.List<String> csvLines,
                                    final java.nio.file.Path outDir,
                                    final int attempt,
                                    final IOException ioe) {
        log.warn("Could not write feature comparison CSV after {} attempts: {}", attempt, ioe.getMessage());
        log.debug("Stacktrace:", ioe);
        try {
            final String ts = String.valueOf(System.currentTimeMillis());
            final java.nio.file.Path altFile = outDir.resolve("method_feature_comparison_" + ts + ".csv");
            java.nio.file.Files.write(altFile, csvLines, java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE_NEW);
            log.info("Feature comparison CSV written to fallback file: {}", altFile.toString());
        } catch (final IOException altEx) {
            log.warn("Fallback write also failed: {}", altEx.getMessage());
            log.debug("Fallback stacktrace:", altEx);
        }
    }

    /**
     * Sleeps between write attempts.
     */
    private void sleepBetweenAttempts() {
        try {
            Thread.sleep(200);
        } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Cleans up temporary file if it exists.
     *
     * @param tmpFile Temporary file to clean up
     */
    private void cleanupTempFile(final java.nio.file.Path tmpFile) {
        try {
            if (java.nio.file.Files.exists(tmpFile)) {
                java.nio.file.Files.deleteIfExists(tmpFile);
            }
        } catch (final IOException ignore) {
            // ignore cleanup failures
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
        final Path path = Paths.get(filePath);
        
        if (!Files.exists(path)) {
            log.warn("Method file not found: {}", filePath);
            return new HashMap<>();
        }

        final String content = Files.readString(path);
        final String[] lines = content.split("\n");
        final int loc = lines.length;
        final double computedCc = calculateCyclomaticComplexity(content);
        
        final FeatureParsingContext context = new FeatureParsingContext(loc, computedCc);
        parseFeatureLinesFromContent(lines, context);
        
        return mergeAndFinalizeFeatures(context);
    }
    
    /**
     * Helper class to hold parsing context and results.
     */
    private static class FeatureParsingContext {
        final int loc;
        final double computedCc;
        boolean hasRefactoredTag = false;
        final Map<String, Double> originalFromComments = new HashMap<>();
        final Map<String, Double> refactoredFromComments = new HashMap<>();
        final Map<String, Double> features = new HashMap<>();
        
        FeatureParsingContext(int loc, double computedCc) {
            this.loc = loc;
            this.computedCc = computedCc;
        }
    }
    
    /**
     * Parses all feature lines from the content.
     */
    private void parseFeatureLinesFromContent(final String[] lines, final FeatureParsingContext context) {
        for (final String line : lines) {
            parseOriginalFeatureLine(line, context);
            parseRefactoredFeatureLine(line, context);
            parseStandardFeatureLine(line, context);
        }
    }
    
    /**
     * Parses a line containing ORIGINAL: feature definition.
     */
    private void parseOriginalFeatureLine(final String line, final FeatureParsingContext context) {
        if (!line.contains("ORIGINAL:")) return;
        
        final String[] parts = line.split("=");
        if (parts.length < 2) return;
        
        try {
            final String featureName = extractFeatureName(parts[0], "ORIGINAL:", 9);
            final String valueStr = parts[1].trim().split("\\s+")[0].trim();
            final double value = Double.parseDouble(valueStr);
            context.originalFromComments.put(featureName, value);
        } catch (final Exception e) {
            log.debug("Could not parse ORIGINAL feature from line: {}", line);
        }
    }
    
    /**
     * Parses a line containing REFACTORED: feature definition.
     */
    private void parseRefactoredFeatureLine(final String line, final FeatureParsingContext context) {
        if (!line.contains("REFACTORED:")) return;
        
        context.hasRefactoredTag = true;
        final String[] parts = line.split("=");
        if (parts.length < 2) return;
        
        try {
            final String featureName = extractFeatureName(parts[0], "REFACTORED:", 11);
            final String valueStr = parts[1].trim().split("\\s+")[0].trim();
            final double value = Double.parseDouble(valueStr);
            context.refactoredFromComments.put(featureName, value);
        } catch (final Exception e) {
            log.debug("Could not parse REFACTORED feature from line: {}", line);
        }
    }
    
    /**
     * Parses a line in standard format: "FeatureName: value".
     */
    private void parseStandardFeatureLine(final String line, final FeatureParsingContext context) {
        if (!line.contains(":") || line.contains("//") || line.contains("*")) return;
        
        final String[] parts = line.split(":");
        if (parts.length != 2) return;
        
        try {
            final String featureName = parts[0].trim();
            final double value = Double.parseDouble(parts[1].trim());
            context.features.put(featureName, value);
        } catch (final NumberFormatException e) {
            log.debug("Skipping non-numeric line: {}", line);
        }
    }
    
    /**
     * Extracts feature name from a line containing a tag.
     */
    private String extractFeatureName(final String beforeEquals, final String tag, final int tagLength) {
        return beforeEquals.substring(beforeEquals.indexOf(tag) + tagLength).trim();
    }
    
    /**
     * Merges parsed features and finalizes with computed metrics.
     */
    private Map<String, Double> mergeAndFinalizeFeatures(final FeatureParsingContext context) {
        if (context.hasRefactoredTag && !context.refactoredFromComments.isEmpty()) {
            return mergeFeaturesWithRefactored(context);
        } else if (!context.originalFromComments.isEmpty()) {
            return mergeFeaturesWithOriginal(context);
        } else {
            return mergeFeaturesWithDefaults(context);
        }
    }
    
    /**
     * Merges features when REFACTORED tag is present.
     */
    private Map<String, Double> mergeFeaturesWithRefactored(final FeatureParsingContext context) {
        context.features.putAll(context.refactoredFromComments);
        ensureLOCPresent(context.features, context.loc);
        ensureCCNotZero(context.features, context.computedCc);
        return context.features;
    }
    
    /**
     * Merges features when ORIGINAL is present.
     */
    private Map<String, Double> mergeFeaturesWithOriginal(final FeatureParsingContext context) {
        context.features.putAll(context.originalFromComments);
        ensureLOCPresent(context.features, context.loc);
        ensureCCPresent(context.features, context.computedCc);
        return context.features;
    }
    
    /**
     * Merges features with defaults when no comments found.
     */
    private Map<String, Double> mergeFeaturesWithDefaults(final FeatureParsingContext context) {
        log.debug("No metadata found, calculating basic metrics for file");
        context.features.put(CYCLOMATIC_COMPLEXITY, context.computedCc);
        context.features.put("LOC", (double) context.loc);
        return context.features;
    }
    
    /**
     * Ensures LOC is present in features.
     */
    private void ensureLOCPresent(final Map<String, Double> features, final int loc) {
        if (!features.containsKey("LOC")) {
            features.put("LOC", (double) loc);
        }
    }
    
    /**
     * Ensures CC is present or recalculates if missing/zero.
     */
    private void ensureCCPresent(final Map<String, Double> features, final double computedCc) {
        if (!features.containsKey(CYCLOMATIC_COMPLEXITY)) {
            features.put(CYCLOMATIC_COMPLEXITY, computedCc);
        }
    }
    
    /**
     * Ensures CC is not zero, recalculates if missing or zero.
     */
    private void ensureCCNotZero(final Map<String, Double> features, final double computedCc) {
        final Double cc = features.get(CYCLOMATIC_COMPLEXITY);
        if (cc == null || cc <= 0) {
            features.put(CYCLOMATIC_COMPLEXITY, computedCc);
        }
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
