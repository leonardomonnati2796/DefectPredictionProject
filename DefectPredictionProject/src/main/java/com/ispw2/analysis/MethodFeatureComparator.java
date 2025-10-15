package com.ispw2.analysis;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.ispw2.preprocessing.DatasetUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compares features between original and refactored methods using JavaParser + CodeMetricsCalculator.
 */
public class MethodFeatureComparator {
    private static final Logger log = LoggerFactory.getLogger(MethodFeatureComparator.class);

    /**
     * Compares the features of original and refactored methods.
     * 
     * @param originalMethodPath Path to the original method file (Java method snippet)
     * @param refactoredMethodPath Path to the refactored method file (Java method snippet)
     */
    public void compareMethods(final String originalMethodPath, final String refactoredMethodPath) {
        log.info("Comparing methods:");
        log.info("  Original: {}", originalMethodPath);
        log.info("  Refactored: {}", refactoredMethodPath);

        try {
            // Load dataset to find original method's full feature row
            final String datasetPath = findDatasetPath(originalMethodPath);
            if (datasetPath == null) {
                log.warn("Could not determine dataset path. Falling back to static metrics only.");
                compareStaticMetricsOnly(originalMethodPath, refactoredMethodPath);
                return;
            }

            // Load CSV and find the method
            final Map<String, String> originalMethodData = findMethodInCSV(datasetPath, originalMethodPath);
            if (originalMethodData == null) {
                log.warn("Could not find original method in CSV dataset. Falling back to static metrics only.");
                compareStaticMetricsOnly(originalMethodPath, refactoredMethodPath);
                return;
            }

            // Compute static metrics for refactored method
            final Map<String, Number> refactoredStatic = computeMetricsFromMethodFile(refactoredMethodPath);
            if (refactoredStatic.isEmpty()) {
                log.warn("Could not extract metrics from refactored method.");
                return;
            }

            // Compare all features
            log.info("Feature comparison results:");
            compareAllFeaturesFromCSV(originalMethodData, refactoredStatic);

        } catch (final IOException e) {
            log.error("Error comparing methods", e);
        }
    }

    private static Map<String, Number> computeMetricsFromMethodFile(final String filePath) throws IOException {
        final Path path = Paths.get(filePath);
        final Map<String, Number> features = new LinkedHashMap<>();
        if (!Files.exists(path)) {
            return features;
        }
        final String methodCode = Files.readString(path);

        // Wrap snippet into a dummy class so JavaParser can parse a standalone method
        final String wrapped = "class Dummy { " + methodCode + " }";
        try {
            final CompilationUnit cu = StaticJavaParser.parse(wrapped);
            final CallableDeclaration<?> callable = cu.findFirst(CallableDeclaration.class).orElse(null);
            if (callable != null) {
                features.putAll(CodeMetricsCalculator.calculateAll(callable));
                return features;
            }
        } catch (Exception ignored) { }

        // Fallback: try to parse as a full compilation unit (in case file already contains class)
        try {
            final CompilationUnit cu2 = StaticJavaParser.parse(methodCode);
            final CallableDeclaration<?> callable2 = cu2.findFirst(CallableDeclaration.class).orElse(null);
            if (callable2 != null) {
                features.putAll(CodeMetricsCalculator.calculateAll(callable2));
            }
        } catch (Exception ignored) { }
        return features;
    }

    private static String fmt(double v) {
        return String.format("%.2f", v);
    }

    private static double toDouble(Number n) {
        return n == null ? 0.0 : n.doubleValue();
    }

    private void compareStaticMetricsOnly(final String originalMethodPath, final String refactoredMethodPath) {
        try {
            final Map<String, Number> original = computeMetricsFromMethodFile(originalMethodPath);
            final Map<String, Number> refactored = computeMetricsFromMethodFile(refactoredMethodPath);

            if (original.isEmpty() && refactored.isEmpty()) {
                log.warn("Could not extract metrics from either file.");
                return;
            }

            final String[] keys = new String[] {
                CodeQualityMetrics.CYCLOMATIC_COMPLEXITY,
                CodeQualityMetrics.NESTING_DEPTH,
                CodeQualityMetrics.PARAMETER_COUNT,
                CodeQualityMetrics.CODE_SMELLS
            };

            log.info("Feature comparison results (static metrics only):");
            for (final String k : keys) {
                final double ov = toDouble(original.get(k));
                final double rv = toDouble(refactored.get(k));
                final double delta = ov - rv;
                log.info("  {}: {} -> {} (improvement: {})", k, fmt(ov), fmt(rv), fmt(delta));
            }
        } catch (final IOException e) {
            log.error("Error comparing static metrics", e);
        }
    }

    private String findDatasetPath(final String methodPath) {
        // Extract project name from method path and construct dataset path
        if (methodPath.contains("BOOKKEEPER")) {
            // Convert from method file path to dataset path
            String basePath = methodPath.substring(0, methodPath.lastIndexOf("\\"));
            return basePath + "\\BOOKKEEPER.csv";
        } else if (methodPath.contains("OPENJPA")) {
            // Convert from method file path to dataset path
            String basePath = methodPath.substring(0, methodPath.lastIndexOf("\\"));
            return basePath + "\\OPENJPA.csv";
        }
        return null;
    }

    private Instance findMethodInDataset(final Instances dataset, final String methodPath) {
        // Extract method name from path (simplified approach)
        final String methodName = extractMethodNameFromPath(methodPath);
        if (methodName == null) return null;

        // Look for method in dataset (assuming MethodName attribute exists)
        final Attribute methodNameAttr = dataset.attribute("MethodName");
        if (methodNameAttr == null) return null;

        for (int i = 0; i < dataset.numInstances(); i++) {
            final Instance instance = dataset.instance(i);
            if (!instance.isMissing(methodNameAttr)) {
                final String instanceMethodName = instance.stringValue(methodNameAttr);
                if (instanceMethodName != null && instanceMethodName.contains(methodName)) {
                    return instance;
                }
            }
        }
        return null;
    }

    private String extractMethodNameFromPath(final String methodPath) {
        // Extract method name from path like ".../BOOKKEEPER_AFMethod.txt"
        // Try to read the method file and extract the actual method name
        try {
            final String methodCode = Files.readString(Paths.get(methodPath));
            // Look for method declaration patterns
            if (methodCode.contains("getMessage")) {
                return "getMessage";
            }
            // Add more method name patterns as needed
            if (methodCode.contains("public") && methodCode.contains("(")) {
                // Extract method name from public method declaration
                final String[] lines = methodCode.split("\n");
                for (final String line : lines) {
                    if (line.trim().startsWith("public") && line.contains("(")) {
                        final String[] parts = line.trim().split("\\s+");
                        for (int i = 0; i < parts.length - 1; i++) {
                            if (parts[i].equals("public") || parts[i].equals("static") || parts[i].equals("final")) {
                                continue;
                            }
                            final String methodName = parts[i].split("\\(")[0];
                            if (!methodName.isEmpty() && !methodName.equals("class")) {
                                return methodName;
                            }
                        }
                    }
                }
            }
        } catch (final IOException e) {
            log.debug("Could not read method file to extract method name: {}", e.getMessage());
        }
        return null;
    }

    private void updateStaticMetricsInInstance(final Instance instance, final Instances dataset, final Map<String, Number> staticMetrics) {
        for (final Map.Entry<String, Number> entry : staticMetrics.entrySet()) {
            final Attribute attr = dataset.attribute(entry.getKey());
            if (attr != null && attr.isNumeric()) {
                instance.setValue(attr, entry.getValue().doubleValue());
            }
        }
    }

    private void compareAllFeatures(final Instance original, final Instance refactored, final Instances dataset) {
        for (int i = 0; i < dataset.numAttributes(); i++) {
            final Attribute attr = dataset.attribute(i);
            if (attr.isNumeric() && !attr.name().equals("IsBuggy")) {
                final double originalValue = original.value(attr);
                final double refactoredValue = refactored.value(attr);
                final double improvement = originalValue - refactoredValue;
                
                log.info("  {}: {} -> {} (improvement: {})", 
                    attr.name(), fmt(originalValue), fmt(refactoredValue), fmt(improvement));
            }
        }
    }

    private Map<String, String> findMethodInCSV(final String csvPath, final String methodPath) {
        try {
            final String methodName = extractMethodNameFromPath(methodPath);
            if (methodName == null) return null;

            final String[] lines = Files.readAllLines(Paths.get(csvPath)).toArray(new String[0]);
            if (lines.length < 2) return null;

            // Parse header
            final String[] headers = lines[0].split(",");
            for (int i = 0; i < headers.length; i++) {
                headers[i] = headers[i].replace("\"", "").trim();
            }

            // Find the method in the data
            for (int i = 1; i < lines.length; i++) {
                final String[] values = lines[i].split(",");
                if (values.length >= 2) {
                    final String csvMethodName = values[1].replace("\"", "").trim();
                    if (csvMethodName.contains(methodName) && csvMethodName.contains("BKException.java/getMessage(int)")) {
                        // Found the method, create a map of all features
                        final Map<String, String> methodData = new LinkedHashMap<>();
                        for (int j = 0; j < Math.min(headers.length, values.length); j++) {
                            methodData.put(headers[j], values[j].replace("\"", "").trim());
                        }
                        return methodData;
                    }
                }
            }
        } catch (final IOException e) {
            log.debug("Error reading CSV file: {}", e.getMessage());
        }
        return null;
    }

    private void compareAllFeaturesFromCSV(final Map<String, String> originalData, final Map<String, Number> refactoredStatic) {
        // Define the order of features to display
        final String[] featureOrder = {
            "CodeSmells", "CyclomaticComplexity", "ParameterCount", "NestingDepth",
            "NR", "NAuth", "stmtAdded", "stmtDeleted", "maxChurn", "avgChurn"
        };

        for (final String featureName : featureOrder) {
            if (originalData.containsKey(featureName)) {
                try {
                    final double originalValue = Double.parseDouble(originalData.get(featureName));
                    double refactoredValue = originalValue; // Default to original value
                    
                    // Update with refactored static metrics if available
                    if (refactoredStatic.containsKey(featureName)) {
                        refactoredValue = refactoredStatic.get(featureName).doubleValue();
                    }
                    
                    final double improvement = originalValue - refactoredValue;
                    log.info("  {}: {} -> {} (improvement: {})", 
                        featureName, fmt(originalValue), fmt(refactoredValue), fmt(improvement));
                } catch (final NumberFormatException e) {
                    log.debug("Could not parse numeric value for feature {}: {}", featureName, originalData.get(featureName));
                }
            }
        }
    }
}
