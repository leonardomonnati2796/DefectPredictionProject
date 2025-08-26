package com.ispw2.analysis;

import com.github.javaparser.ParseProblemException;
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

    private static final String ORIGINAL_AFMETHOD_FILENAME_SUFFIX = "_AFMethod.txt";
    private static final String REFACTORED_AFMETHOD_DIR = "AFMethod_refactored";
    private static final String REFACTORED_AFMETHOD_FILENAME_SUFFIX = "_AFMethod_refactored.txt";
    private static final String CSV_COL_METHOD_NAME = "MethodName";
    private static final String CSV_COL_RELEASE = "Release";
    private static final String CSV_COL_IS_BUGGY = "IsBuggy";

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

    private final ConfigurationManager config;
    private final String originalCsvPath;
    private final String processedArffPath;
    private final GitConnector git;
    private final Map<String, RevCommit> releaseCommits;

    public DataAnalyzer(ConfigurationManager config, final String originalCsvPath, final String processedArffPath, final GitConnector git, final Map<String, RevCommit> releaseCommits) {
        this.config = config;
        this.originalCsvPath = originalCsvPath;
        this.processedArffPath = processedArffPath;
        this.git = git;
        this.releaseCommits = releaseCommits;
    }

    // --- MODIFICA 1: Cambiato il tipo di ritorno da 'void' a 'String' ---
    public String findAndSaveActionableMethod() throws IOException {
        try {
            final Instances data = DataHelper.loadArff(processedArffPath);
            data.setClassIndex(data.numAttributes() - 1);

            final String aFeature = findTopActionableFeature(data);
            final Optional<CSVRecord> afMethodRecordOpt = findTargetMethodRecord(aFeature);

            if (afMethodRecordOpt.isPresent()) {
                saveMethodSourceToFile(afMethodRecordOpt.get());
            } else {
                log.warn("Skipping Step 7 as no AFMethod was identified.");
            }
            
            // --- MODIFICA 2: Aggiunto il return statement ---
            return aFeature;
        } catch (Exception e) {
            throw new IOException("Failed during data analysis.", e);
        }
    }

    private String findTopActionableFeature(final Instances data) throws Exception {
        log.info("[Milestone 2, Step 4 & 5] Finding Top Actionable Feature...");
        final AttributeSelection selector = new AttributeSelection();
        selector.setEvaluator(new InfoGainAttributeEval());
        selector.setSearch(new Ranker());
        selector.SelectAttributes(data);
        
        final List<String> actionableFeatures = config.getActionableFeatures();
        
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

        final String lastReleaseName = records.get(records.size() - 1).get(CSV_COL_RELEASE);
        log.info("  -> Searching within the last analyzed release: {}", lastReleaseName);

        final List<CSVRecord> buggyMethodsInLastRelease = records.stream()
                .filter(r -> r.get(CSV_COL_RELEASE).equals(lastReleaseName) && "yes".equalsIgnoreCase(r.get(CSV_COL_IS_BUGGY)))
                .toList();

        log.info("  -> Found {} buggy methods in this release.", buggyMethodsInLastRelease.size());

        if (buggyMethodsInLastRelease.isEmpty()) {
            log.warn("  -> No buggy methods found in the last release. Cannot identify AFMethod.");
            return Optional.empty();
        }

        final Optional<CSVRecord> targetRecord = buggyMethodsInLastRelease.stream()
                .max(Comparator.comparingDouble(r -> Double.parseDouble(r.get(aFeature))));
        
        if (targetRecord.isPresent() && log.isInfoEnabled()) {
            log.info("Identified AFMethod (buggy method with highest {}):", aFeature);
            log.info("  -> MethodName: {}", targetRecord.get().get(CSV_COL_METHOD_NAME));
            log.info("  -> {} Value: {}", aFeature, targetRecord.get().get(aFeature));
        }
        return targetRecord;
    }
    
    private void saveMethodSourceToFile(final CSVRecord methodRecord) {
        log.info("[Milestone 2, Step 7] Saving AFMethod source code and preparing for refactoring...");
        final Optional<MethodIdentifier> identifierOpt = MethodIdentifier.fromString(methodRecord.get(CSV_COL_METHOD_NAME));
        if (identifierOpt.isEmpty()) {
            if (log.isErrorEnabled()) {
                log.error("Could not parse method identifier: {}", methodRecord.get(CSV_COL_METHOD_NAME));
            }
            return;
        }
        final MethodIdentifier identifier = identifierOpt.get();
        
        final String releaseName = methodRecord.get(CSV_COL_RELEASE);
        final RevCommit commit = releaseCommits.get(releaseName);
        if (commit == null) {
            log.error("Could not find commit for release: {}", releaseName);
            return;
        }

        try {
            final String fileContent = git.getFileContent(identifier.filePath(), commit.getName());
            if (fileContent.isEmpty()) return;

            final Optional<String> methodSourceOpt = StaticJavaParser.parse(fileContent)
                    .findAll(CallableDeclaration.class).stream()
                    .filter(m -> m.getSignature().asString().equals(identifier.signature()))
                    .findFirst()
                    .map(CallableDeclaration::toString);

            if (methodSourceOpt.isPresent()) {
                handleFileSaving(methodSourceOpt.get());
            } else {
                 log.error("Could not find method with signature '{}' in file {}", identifier.signature(), identifier.filePath());
            }
        } catch (IOException e) {
            log.error("Could not read or write file for method: {}", identifier, e);
        } catch (ParseProblemException e) {
            log.error("Failed to parse Java source for method: {}", identifier, e);
        }
    }

    private void handleFileSaving(String methodSource) throws IOException {
        final Path datasetsPath = Paths.get(originalCsvPath).getParent();
                
        final String originalFileName = git.getProjectName() + ORIGINAL_AFMETHOD_FILENAME_SUFFIX;
        final Path originalOutputPath = datasetsPath.resolve(originalFileName);
        Files.writeString(originalOutputPath, methodSource);
        
        if (log.isInfoEnabled()) {
            log.info("  -> Source code of AFMethod saved to: {}", originalOutputPath);
        }

        final Path refactoredDirPath = datasetsPath.resolve(REFACTORED_AFMETHOD_DIR);
        Files.createDirectories(refactoredDirPath);
        
        final String refactoredFileName = git.getProjectName() + REFACTORED_AFMETHOD_FILENAME_SUFFIX;
        final Path refactoredFilePath = refactoredDirPath.resolve(refactoredFileName);
        
        if (!Files.exists(refactoredFilePath)) {
            Files.createFile(refactoredFilePath);
            if (log.isInfoEnabled()) {
                log.info("  -> Created empty file for refactoring at: {}", refactoredFilePath);
            }
        } else if (log.isInfoEnabled()) {
            log.info("  -> Refactoring file already exists at: {}", refactoredFilePath);
        }
    }
}