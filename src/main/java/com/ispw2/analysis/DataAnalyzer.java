package com.ispw2.analysis;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.ispw2.ConfigurationManager;
import com.ispw2.connectors.GitConnector;
import com.ispw2.preprocessing.DataHelper;
import org.apache.commons.csv.CSVRecord;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.attributeSelection.AttributeSelection;
import weka.attributeSelection.InfoGainAttributeEval;
import weka.attributeSelection.Ranker;
import weka.core.Instances;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DataAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(DataAnalyzer.class);

    private record MethodIdentifier(String filePath, String signature) {
        public static Optional<MethodIdentifier> fromString(final String fullIdentifier) {
            final int separator = fullIdentifier.lastIndexOf('/');
            if (separator == -1) {
                return Optional.empty();
            }
            return Optional.of(new MethodIdentifier(
                fullIdentifier.substring(0, separator),
                fullIdentifier.substring(separator + 1)
            ));
        }
    }

    private final String originalCsvPath;
    private final String processedArffPath;
    private final GitConnector git;
    private final Map<String, RevCommit> releaseCommits;

    public DataAnalyzer(final String originalCsvPath, final String processedArffPath, final GitConnector git, final Map<String, RevCommit> releaseCommits) {
        this.originalCsvPath = originalCsvPath;
        this.processedArffPath = processedArffPath;
        this.git = git;
        this.releaseCommits = releaseCommits;
    }

    public String findAndSaveActionableMethod() throws Exception {
        final Instances data = DataHelper.loadArff(processedArffPath);
        data.setClassIndex(data.numAttributes() - 1);

        final String aFeature = findTopActionableFeature(data);
        final Optional<CSVRecord> afMethodRecordOpt = findTargetMethodRecord(aFeature);

        if (afMethodRecordOpt.isPresent()) {
            saveMethodSourceToFile(afMethodRecordOpt.get());
        } else {
            log.warn("Skipping Step 7 as no AFMethod was identified.");
        }
        
        return aFeature;
    }

    private String findTopActionableFeature(final Instances data) throws Exception {
        log.info("[Milestone 2, Step 4 & 5] Finding Top Actionable Feature...");
        final AttributeSelection selector = new AttributeSelection();
        selector.setEvaluator(new InfoGainAttributeEval());
        selector.setSearch(new Ranker());
        selector.SelectAttributes(data);
        
        final List<String> actionableFeatures = ConfigurationManager.getInstance().getActionableFeatures();
        
        for (final double[] rankedAttribute : selector.rankedAttributes()) {
            final String featureName = data.attribute((int) rankedAttribute[0]).name();
            if (actionableFeatures.contains(featureName)) {
                log.info("Identified AFeature: {}", featureName);
                return featureName;
            }
        }
        final String fallbackFeature = actionableFeatures.get(0);
        log.warn("No actionable feature found in ranked list. Defaulting to first in config: {}", fallbackFeature);
        return fallbackFeature;
    }
    
    private Optional<CSVRecord> findTargetMethodRecord(final String aFeature) throws IOException {
        log.info("[Milestone 2, Step 6] Identifying Target Method (AFMethod)...");
        final List<CSVRecord> records = DataHelper.readCsv(originalCsvPath);
        if (records.isEmpty()) {
            log.warn("Dataset is empty, cannot find AFMethod.");
            return Optional.empty();
        }

        final String lastReleaseName = records.get(records.size() - 1).get("Release");
        log.info("  -> Searching within the last analyzed release: {}", lastReleaseName);

        final List<CSVRecord> buggyMethodsInLastRelease = records.stream()
                .filter(r -> r.get("Release").equals(lastReleaseName) && "yes".equalsIgnoreCase(r.get("IsBuggy")))
                .toList();

        log.info("  -> Found {} buggy methods in this release.", buggyMethodsInLastRelease.size());

        if (buggyMethodsInLastRelease.isEmpty()) {
            log.warn("  -> No buggy methods found in the last release. Cannot identify AFMethod.");
            return Optional.empty();
        }

        final Optional<CSVRecord> targetRecord = buggyMethodsInLastRelease.stream()
                .max(Comparator.comparingDouble(r -> Double.parseDouble(r.get(aFeature))));
        
        if (targetRecord.isPresent()) {
            log.info("Identified AFMethod (buggy method with highest {}):", aFeature);
            log.info("  -> MethodName: {}", targetRecord.get().get("MethodName"));
            log.info("  -> {} Value: {}", aFeature, targetRecord.get().get(aFeature));
        }
        return targetRecord;
    }
    
    private void saveMethodSourceToFile(final CSVRecord methodRecord) {
        log.info("[Milestone 2, Step 7] Saving AFMethod source code and preparing for refactoring...");
        final Optional<MethodIdentifier> identifierOpt = MethodIdentifier.fromString(methodRecord.get("MethodName"));
        if (identifierOpt.isEmpty()) {
            log.error("Could not parse method identifier: {}", methodRecord.get("MethodName"));
            return;
        }
        final MethodIdentifier identifier = identifierOpt.get();
        
        final String releaseName = methodRecord.get("Release");
        final RevCommit commit = releaseCommits.get(releaseName);
        if (commit == null) {
            log.error("Could not find commit for release: {}", releaseName);
            return;
        }

        try {
            final String fileContent = git.getFileContent(identifier.filePath(), commit.getName());
            if (fileContent.isEmpty()) {
                return;
            }

            final Optional<String> methodSourceOpt = StaticJavaParser.parse(fileContent)
                    .findAll(CallableDeclaration.class).stream()
                    .filter(m -> m.getSignature().asString().equals(identifier.signature()))
                    .findFirst()
                    .map(CallableDeclaration::toString);

            if (methodSourceOpt.isPresent()) {
                final Path datasetsPath = Paths.get(originalCsvPath).getParent();
                
                // Salva il file originale
                final String originalFileName = git.getProjectName() + "_AFMethod.txt";
                final Path originalOutputPath = datasetsPath.resolve(originalFileName);
                Files.writeString(originalOutputPath, methodSourceOpt.get());
                log.info("  -> Source code of AFMethod saved to: {}", originalOutputPath);

                // Crea la cartella e il file per il refactoring
                final Path refactoredDirPath = datasetsPath.resolve("AFMethod_refactored");
                Files.createDirectories(refactoredDirPath);
                
                final String refactoredFileName = git.getProjectName() + "_AFMethod_refactored.txt";
                final Path refactoredFilePath = refactoredDirPath.resolve(refactoredFileName);
                
                if (!Files.exists(refactoredFilePath)) {
                    Files.createFile(refactoredFilePath);
                    log.info("  -> Created empty file for refactoring at: {}", refactoredFilePath);
                } else {
                    log.info("  -> Refactoring file already exists at: {}", refactoredFilePath);
                }

            } else {
                 log.error("Could not find method with signature '{}' in file {}", identifier.signature(), identifier.filePath());
            }
        } catch (final IOException e) {
            log.error("Could not read or write file for method: {}", identifier, e);
        } catch (final Exception e) {
            log.error("Unexpected error parsing or saving Java source for method: {}", identifier, e);
        }
    }
}