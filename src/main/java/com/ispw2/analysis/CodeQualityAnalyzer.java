package com.ispw2.analysis;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.ispw2.ConfigurationManager;
import com.ispw2.connectors.VersionControlConnector;
import com.ispw2.preprocessing.DatasetUtilities;
import com.ispw2.util.LoggingPatterns;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class CodeQualityAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(CodeQualityAnalyzer.class);

    private static final String ORIGINAL_AFMETHOD_FILENAME_SUFFIX = "_AFMethod.txt";
    private static final String REFACTORED_AFMETHOD_DIR = "AFMethod_refactored";
    private static final String REFACTORED_AFMETHOD_FILENAME_SUFFIX = "_AFMethod_refactored.txt";
    private static final String CSV_COL_METHOD_NAME = "MethodName";
    private static final String CSV_COL_RELEASE = "Release";
    private static final String CSV_COL_IS_BUGGY = "IsBuggy";

    private static final class MethodIdentifier {
        private final String filePath;
        private final String signature;

        public MethodIdentifier(String filePath, String signature) {
            this.filePath = filePath;
            this.signature = signature;
        }

        public String getFilePath() {
            return filePath;
        }

        public String signature() {
            return signature;
        }

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

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            MethodIdentifier that = (MethodIdentifier) obj;
            return Objects.equals(filePath, that.filePath) && Objects.equals(signature, that.signature);
        }

        @Override
        public int hashCode() {
            return Objects.hash(filePath, signature);
        }

        @Override
        public String toString() {
            return filePath + "/" + signature;
        }
    }

    private final ConfigurationManager config;
    private final String originalCsvPath;
    private final String processedArffPath;
    private final VersionControlConnector git;
    private final Map<String, RevCommit> releaseCommits;

    /**
     * Constructs a new CodeQualityAnalyzer for identifying actionable features and methods.
     * 
     * @param config Configuration manager with system settings
     * @param originalCsvPath Path to the original CSV dataset
     * @param processedArffPath Path to the processed ARFF dataset
     * @param git Version control connector for Git operations
     * @param releaseCommits Map of release names to Git commits
     */
    public CodeQualityAnalyzer(ConfigurationManager config, final String originalCsvPath, final String processedArffPath, final VersionControlConnector git, final Map<String, RevCommit> releaseCommits) {
        this.config = config;
        this.originalCsvPath = originalCsvPath;
        this.processedArffPath = processedArffPath;
        this.git = git;
        this.releaseCommits = releaseCommits != null ? new HashMap<>(releaseCommits) : null;
    }

    /**
     * Finds the most actionable feature and identifies the target method for refactoring.
     * Uses Information Gain to identify the feature most correlated with bugginess,
     * then finds the buggy method with the highest value of that feature.
     * 
     * @return The name of the most actionable feature
     * @throws IOException If analysis fails
     */
    public String findAndSaveActionableMethod() throws IOException {
        LoggingPatterns.info(log, "Starting data analysis to find actionable method...");
        try {
            final Instances data = DatasetUtilities.loadArff(processedArffPath);
            data.setClassIndex(data.numAttributes() - 1);

            final String aFeature = findTopActionableFeature(data);
            final Optional<CSVRecord> afMethodRecordOpt = findTargetMethodRecord(aFeature);

            if (afMethodRecordOpt.isPresent()) {
                saveMethodSourceToFile(afMethodRecordOpt.get());
            } else {
                log.warn("Skipping file saving as no AFMethod was identified.");
            }
            
            LoggingPatterns.info(log, "Data analysis completed.");
            return aFeature;
        } catch (Exception e) {
            throw new IOException("Failed during data analysis.", e);
        }
    }

    /**
     * Finds the top actionable feature using Information Gain attribute evaluation.
     * 
     * @param data The dataset to analyze for feature ranking
     * @return The name of the most actionable feature
     * @throws Exception If feature selection fails
     */
    private String findTopActionableFeature(final Instances data) throws Exception {
        LoggingPatterns.logMilestone(log, 2, "Finding Top Actionable Feature...");
        final AttributeSelection selector = new AttributeSelection();
        selector.setEvaluator(new InfoGainAttributeEval());
        selector.setSearch(new Ranker());
        selector.SelectAttributes(data);
        
        if (log.isDebugEnabled()) {
            StringBuilder rankedFeaturesLog = new StringBuilder("Ranked Features (InfoGain):").append(System.lineSeparator());
            for (double[] rankedAttribute : selector.rankedAttributes()) {
                rankedFeaturesLog.append(String.format("  - %s: %.4f%n", data.attribute((int) rankedAttribute[0]).name(), rankedAttribute[1]));
            }
            log.debug(rankedFeaturesLog.toString());
        }
        
        final List<String> actionableFeatures = config.getActionableFeatures();
        log.debug("Actionable features from config: {}", actionableFeatures);
        
        for (final double[] rankedAttribute : selector.rankedAttributes()) {
            final String featureName = data.attribute((int) rankedAttribute[0]).name();
            if (actionableFeatures.contains(featureName)) {
                LoggingPatterns.info(log, "Identified Top Actionable Feature (AFeature): {}", featureName);
                return featureName;
            }
        }
        final String fallbackFeature = actionableFeatures.get(0);
        log.warn("No actionable feature found in ranked list. Defaulting to first in config: {}", fallbackFeature);
        return fallbackFeature;
    }
    
    /**
     * Finds the target method record (AFMethod) with the highest value of the actionable feature.
     * 
     * @param aFeature The actionable feature to search for
     * @return Optional containing the target method record if found
     * @throws IOException If CSV reading fails
     */
    private Optional<CSVRecord> findTargetMethodRecord(final String aFeature) throws IOException {
        LoggingPatterns.logMilestone(log, 2, 6, "Identifying Target Method (AFMethod)...");
        log.debug("Searching for target method using AFeature: {}", aFeature);
        
        final List<CSVRecord> records = DatasetUtilities.readCsv(originalCsvPath);
        if (records.isEmpty()) {
            log.warn("Dataset is empty, cannot find AFMethod.");
            return Optional.empty();
        }

        final String lastReleaseName = records.get(records.size() - 1).get(CSV_COL_RELEASE);
        LoggingPatterns.info(log, "  -> Searching within the last analyzed release: {}", lastReleaseName);

        final List<CSVRecord> buggyMethodsInLastRelease = records.stream()
                .filter(r -> r.get(CSV_COL_RELEASE).equals(lastReleaseName) && "yes".equalsIgnoreCase(r.get(CSV_COL_IS_BUGGY)))
                .collect(Collectors.toList());

        LoggingPatterns.info(log, "  -> Found {} buggy methods in this release.", buggyMethodsInLastRelease.size());

        if (buggyMethodsInLastRelease.isEmpty()) {
            log.warn("  -> No buggy methods found in the last release. Cannot identify AFMethod.");
            return Optional.empty();
        }

        final Optional<CSVRecord> targetRecord = buggyMethodsInLastRelease.stream()
                .max(Comparator.comparingDouble(r -> Double.parseDouble(r.get(aFeature))));
        
        if (targetRecord.isPresent() && log.isInfoEnabled()) {
            LoggingPatterns.info(log, "Identified AFMethod (buggy method with highest {}):", aFeature);
            if (log.isDebugEnabled()){
                log.debug("  -> Full Record: {}", targetRecord.get().toMap());
            } else {
                LoggingPatterns.info(log, "  -> MethodName: {}", targetRecord.get().get(CSV_COL_METHOD_NAME));
                LoggingPatterns.info(log, "  -> {} Value: {}", aFeature, targetRecord.get().get(aFeature));
            }
        }
        return targetRecord;
    }
    
    /**
     * Saves the source code of the target method to files for refactoring analysis.
     * 
     * @param methodRecord The CSV record containing the method information
     */
    private void saveMethodSourceToFile(final CSVRecord methodRecord) {
        LoggingPatterns.logMilestone(log, 2, 7, "Saving AFMethod source code...");
        final Optional<MethodIdentifier> identifierOpt = MethodIdentifier.fromString(methodRecord.get(CSV_COL_METHOD_NAME));
        if (identifierOpt.isEmpty()) {
            if (log.isErrorEnabled()) {
                log.error("Could not parse method identifier: {}", methodRecord.get(CSV_COL_METHOD_NAME));
            }
            return;
        }
        final MethodIdentifier identifier = identifierOpt.get();
        log.debug("Attempting to save source for method: {}", identifier);
        
        final String releaseName = methodRecord.get(CSV_COL_RELEASE);
        final RevCommit commit = releaseCommits.get(releaseName);
        if (commit == null) {
            log.error("Could not find commit for release: {}", releaseName);
            return;
        }
        log.debug("Using commit {} for release {}", commit.getName(), releaseName);

        try {
            final String fileContent = git.getFileContent(identifier.getFilePath(), commit.getName());
            if (fileContent.isEmpty()) {
                log.warn("File content is empty for {}. Cannot extract method source.", identifier.getFilePath());
                return;
            }

            final Optional<String> methodSourceOpt = StaticJavaParser.parse(fileContent)
                    .findAll(CallableDeclaration.class).stream()
                    .filter(m -> m.getSignature().asString().equals(identifier.signature()))
                    .findFirst()
                    .map(CallableDeclaration::toString);

            if (methodSourceOpt.isPresent()) {
                log.debug("Successfully found and parsed method source code.");
                handleFileSaving(methodSourceOpt.get());
            } else {
                 log.error("Could not find method with signature '{}' in file {}", identifier.signature(), identifier.getFilePath());
            }
        } catch (IOException e) {
            log.error("Could not read or write file for method: {}", identifier, e);
        } catch (ParseProblemException e) {
            log.error("Failed to parse Java source for method: {}", identifier, e);
        }
    }

    /**
     * Handles the actual file saving operations for both original and refactored method files.
     * 
     * @param methodSource The source code of the method to save
     * @throws IOException If file operations fail
     */
    private void handleFileSaving(String methodSource) throws IOException {
        final Path datasetsPath = Paths.get(originalCsvPath).getParent();
        log.debug("Preparing to save files in and alongside directory: {}", datasetsPath);
                
        final String originalFileName = git.getProjectName() + ORIGINAL_AFMETHOD_FILENAME_SUFFIX;
        final Path originalOutputPath = datasetsPath.resolve(originalFileName);
        Files.writeString(originalOutputPath, methodSource);
        
        if (log.isInfoEnabled()) {
            LoggingPatterns.logFileOperation(log, "Source code of AFMethod saved to", originalOutputPath.toString());
        }

        final Path projectParentPath = datasetsPath.getParent();
        if (projectParentPath == null) {
            throw new IOException("Cannot determine the parent directory of the datasets folder.");
        }
        final Path refactoredDirPath = projectParentPath.resolve(REFACTORED_AFMETHOD_DIR);
        Files.createDirectories(refactoredDirPath);
        
        final String refactoredFileName = git.getProjectName() + REFACTORED_AFMETHOD_FILENAME_SUFFIX;
        final Path refactoredFilePath = refactoredDirPath.resolve(refactoredFileName);
        
        if (!Files.exists(refactoredFilePath)) {
            Files.createFile(refactoredFilePath);
            if (log.isInfoEnabled()) {
                LoggingPatterns.logFileOperation(log, "Created empty file for refactoring at", refactoredFilePath.toString());
            }
        } else if (log.isInfoEnabled()) {
            LoggingPatterns.logFileOperation(log, "Refactoring file already exists at", refactoredFilePath.toString());
        }
    }
}