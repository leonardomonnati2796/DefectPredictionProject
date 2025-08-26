package com.ispw2.analysis;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.CallableDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class FeatureComparer {
    private static final Logger log = LoggerFactory.getLogger(FeatureComparer.class);
    
    private static final String TABLE_SEPARATOR = "-----------------------------------------------------------------";

    public void compareMethods(final String originalFilePath, final String refactoredFilePath) throws IOException {
        log.info("[Milestone 2, Step 7-9] Comparing Features of Original vs. Refactored Method...");
        
        final Map<String, Number> featuresBefore = extractStaticFeatures(originalFilePath);
        final Map<String, Number> featuresAfter = extractStaticFeatures(refactoredFilePath);

        printComparison(featuresBefore, featuresAfter);
    }

    private Map<String, Number> extractStaticFeatures(final String filePath) throws IOException {
        final String content = Files.readString(Paths.get(filePath));
        if (content.isBlank()) return Collections.emptyMap();

        try {
            final String parsableContent = "class DummyWrapper { " + content + " }";
            return StaticJavaParser.parse(parsableContent)
                    .findFirst(CallableDeclaration.class)
                    .map(StaticMetricsCalculator::calculateAll)
                    .orElseGet(Collections::emptyMap);
        } catch (final ParseProblemException e) {
            log.error("Error parsing method from file: {}. The content might not be a valid Java method.", filePath, e);
            return Collections.emptyMap();
        }
    }

    private void printComparison(final Map<String, Number> before, final Map<String, Number> after) {
        // --- MODIFICA QUI ---
        // L'intero blocco di log è stato reso condizionale per ottimizzazione.
        if (log.isInfoEnabled()) {
            log.info(TABLE_SEPARATOR);
            log.info(String.format("%-25s | %-15s | %-15s | %s", "Feature", "Before Refactor", "After Refactor", "Change"));
            log.info(TABLE_SEPARATOR);

            final List<String> featureNames = Arrays.asList(
                Metrics.LOC, 
                Metrics.CYCLOMATIC_COMPLEXITY, 
                Metrics.PARAMETER_COUNT
            );

            for(final String feature : featureNames) {
                final Number beforeNum = before.get(feature);
                final Number afterNum = after.get(feature);
                final String beforeValue = (beforeNum != null) ? beforeNum.toString() : "N/A";
                final String afterValue = (afterNum != null) ? afterNum.toString() : "N/A";
                final String marker = !beforeValue.equals(afterValue) ? "MODIFIED" : "";
                
                log.info(String.format("%-25s | %-15s | %-15s | %s", feature, beforeValue, afterValue, marker));
            }
            log.info(TABLE_SEPARATOR);
        }
    }
}