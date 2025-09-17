package com.ispw2.analysis;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class MethodFeatureComparator {
    private static final Logger log = LoggerFactory.getLogger(MethodFeatureComparator.class);
    
    private static final String TABLE_SEPARATOR = "-----------------------------------------------------------------";

    public void compareMethods(final String originalFilePath, final String refactoredFilePath) throws IOException {
        log.info("[Milestone 2, Step 7-9] Comparing Features of Original vs. Refactored Method...");
        
        final Map<String, Number> featuresBefore = extractStaticFeatures(originalFilePath);
        final Map<String, Number> featuresAfter = extractStaticFeatures(refactoredFilePath);

        printComparison(featuresBefore, featuresAfter);
    }

    private Map<String, Number> extractStaticFeatures(final String filePath) throws IOException {
        log.debug("Extracting static features from file: {}", filePath);
        final String content = Files.readString(Paths.get(filePath));
        if (content.isBlank()) {
            log.warn("File is blank, no features to extract: {}", filePath);
            return Collections.emptyMap();
        }

        try {
            CompilationUnit cu;
            // Se il file è quello refattorizzato, usa un parser configurato per Java 17.
            // Altrimenti, usa il parser di default con il wrapper per il frammento di codice.
            if (filePath.contains("_refactored")) {
                ParserConfiguration parserConfiguration = new ParserConfiguration();
                parserConfiguration.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
                JavaParser javaParser = new JavaParser(parserConfiguration);
                
                ParseResult<CompilationUnit> result = javaParser.parse(content);
                if (!result.isSuccessful() || result.getResult().isEmpty()) {
                    // Lancia l'eccezione del primo problema trovato per un logging chiaro.
                    throw new ParseProblemException(result.getProblems());
                }
                cu = result.getResult().get();
            } else {
                final String parsableContent = "class DummyWrapper { " + content + " }";
                cu = StaticJavaParser.parse(parsableContent);
            }

            // Cerca il metodo 'main' per calcolare le metriche.
            // Se non lo trova, cerca il primo metodo disponibile come fallback.
            Map<String, Number> features = cu.findFirst(CallableDeclaration.class, decl -> "main".equals(decl.getNameAsString()))
                    .or(() -> cu.findFirst(CallableDeclaration.class))
                    .map(StaticMetricsCalculator::calculateAll)
                    .orElseGet(Collections::emptyMap);
            
            if (log.isDebugEnabled()) {
                log.debug("Extracted features from {}: {}", filePath, features);
            }
            return features;
            
        } catch (final Exception e) {
            log.error("Error parsing source code from file: {}. The content might not be a valid Java method.", filePath, e);
            return Collections.emptyMap();
        }
    }

    private void printComparison(final Map<String, Number> before, final Map<String, Number> after) {
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