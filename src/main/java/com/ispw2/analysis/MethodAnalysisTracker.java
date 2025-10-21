package com.ispw2.analysis;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.ispw2.connectors.VersionControlConnector;
import com.ispw2.model.AnalyzedMethod;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class MethodAnalysisTracker {
    private static final Logger log = LoggerFactory.getLogger(MethodAnalysisTracker.class);
    private final VersionControlConnector git;
    private final Map<String, AnalyzedMethod> lastKnownMethods = new HashMap<>();

    private static final String METHOD_KEY_SEPARATOR = "::";
    private static final int INVALID_LINE_NUMBER = -1;
    private static final int PARENT_COMMIT_INDEX = 0;
    private static final int LINE_OFFSET = 1;

    /**
     * Constructs a new MethodAnalysisTracker for analyzing methods across releases.
     * 
     * @param git Version control connector for Git operations
     */
    public MethodAnalysisTracker(final VersionControlConnector git) {
        this.git = git;
    }

    /**
     * Gets all methods for a specific release commit with their calculated metrics.
     * 
     * @param releaseCommit The Git commit representing the release
     * @return List of analyzed methods with their features
     * @throws IOException If Git operations fail
     */
    public List<AnalyzedMethod> getMethodsForRelease(final RevCommit releaseCommit) throws IOException {
        final String commitId = releaseCommit.getName();
        log.debug("Getting all methods for release commit: {}", commitId);
        
        final List<String> javaFiles = git.getJavaFilesForCommit(commitId);
        log.debug("Found {} Java files in commit {}", javaFiles.size(), commitId);

        final List<AnalyzedMethod> currentMethods = new ArrayList<>();
        final Map<AnalyzedMethod, CallableDeclaration<?>> methodAstMap = new HashMap<>();

        extractMethodsFromAllFiles(javaFiles, commitId, currentMethods, methodAstMap);
        calculateFeaturesForAllMethods(currentMethods, methodAstMap, releaseCommit);
        updateLastKnownMethods(currentMethods);

        return currentMethods;
    }
    
    /**
     * Extracts methods from all Java files.
     * 
     * @param javaFiles List of Java file paths
     * @param commitId The commit ID
     * @param currentMethods List to populate with methods
     * @param methodAstMap Map to store method AST nodes
     * @throws IOException If file operations fail
     */
    private void extractMethodsFromAllFiles(final List<String> javaFiles, 
                                          final String commitId, 
                                          final List<AnalyzedMethod> currentMethods, 
                                          final Map<AnalyzedMethod, CallableDeclaration<?>> methodAstMap) throws IOException {
        for (final String file : javaFiles) {
            extractMethodsFromFile(file, commitId, currentMethods, methodAstMap);
        }
    }
    
    /**
     * Calculates features for all methods.
     * 
     * @param currentMethods List of methods to process
     * @param methodAstMap Map of method AST nodes
     * @param releaseCommit The release commit
     */
    private void calculateFeaturesForAllMethods(final List<AnalyzedMethod> currentMethods, 
                                               final Map<AnalyzedMethod, CallableDeclaration<?>> methodAstMap, 
                                               final RevCommit releaseCommit) {
        for (final AnalyzedMethod method : currentMethods) {
            final CallableDeclaration<?> callable = methodAstMap.get(method);
            if (callable != null) {
                calculateAllFeatures(method, callable, releaseCommit);
            }
        }
    }
    
    /**
     * Updates the last known methods map.
     * 
     * @param currentMethods List of current methods
     */
    private void updateLastKnownMethods(final List<AnalyzedMethod> currentMethods) {
        lastKnownMethods.clear();
        currentMethods.forEach(m -> lastKnownMethods.put(m.filepath() + METHOD_KEY_SEPARATOR + m.signature(), m));
        log.debug("Updated last known methods with {} entries.", lastKnownMethods.size());
    }

    /**
     * Extracts all methods from a Java file and creates AnalyzedMethod objects.
     * 
     * @param file The file path to extract methods from
     * @param commitId The Git commit ID
     * @param currentMethods List to add extracted methods to
     * @param methodAstMap Map to store method AST nodes
     * @throws IOException If file reading fails
     */
    private void extractMethodsFromFile(final String file, final String commitId, final List<AnalyzedMethod> currentMethods, final Map<AnalyzedMethod, CallableDeclaration<?>> methodAstMap) throws IOException {
        log.debug("Parsing file for methods: {}", file);
        final String content = git.getFileContent(file, commitId);
        if (content.isEmpty()) {
            log.warn("File content is empty for {}. Skipping.", file);
            return;
        }

        try {
            final CompilationUnit cu = StaticJavaParser.parse(content);
            processCallableDeclarations(cu, file, currentMethods, methodAstMap);
        } catch (final ParseProblemException e) {
            log.warn("Failed to parse Java file: {}. Skipping method extraction. Reason: {}", file, e.getMessage());
        }
    }
    
    /**
     * Processes all callable declarations in a compilation unit.
     * 
     * @param cu The compilation unit
     * @param file The file path
     * @param currentMethods List to add methods to
     * @param methodAstMap Map to store method AST nodes
     */
    private void processCallableDeclarations(final CompilationUnit cu, 
                                          final String file, 
                                          final List<AnalyzedMethod> currentMethods, 
                                          final Map<AnalyzedMethod, CallableDeclaration<?>> methodAstMap) {
        final AtomicInteger count = new AtomicInteger(0);
        cu.findAll(CallableDeclaration.class).forEach(callable -> {
            count.incrementAndGet();
            final AnalyzedMethod trackedMethod = createAnalyzedMethod(callable, file);
            currentMethods.add(trackedMethod);
            methodAstMap.put(trackedMethod, callable);
        });
        log.debug("Found {} methods in file {}.", count.get(), file);
    }
    
    /**
     * Creates an AnalyzedMethod from a callable declaration.
     * 
     * @param callable The callable declaration
     * @param file The file path
     * @return The created AnalyzedMethod
     */
    private AnalyzedMethod createAnalyzedMethod(final CallableDeclaration<?> callable, final String file) {
        final String signature = callable.getSignature().asString();
        final String fullSignatureKey = file + METHOD_KEY_SEPARATOR + signature;
        final AnalyzedMethod existingMethod = lastKnownMethods.get(fullSignatureKey);
        final String id = (existingMethod != null) ? existingMethod.id() : UUID.randomUUID().toString();
        
        return new AnalyzedMethod(id, signature, file);
    }

    /**
     * Calculates all features for a method including static metrics and change history.
     * 
     * @param method The method to calculate features for
     * @param callable The AST node representing the method
     * @param releaseCommit The release commit for change history calculation
     */
    private void calculateAllFeatures(final AnalyzedMethod method, final CallableDeclaration<?> callable, final RevCommit releaseCommit) {
        log.debug("Calculating all features for method: {}", method.signature());
        method.addAllFeatures(CodeMetricsCalculator.calculateAll(callable));
        method.addFeature(CodeQualityMetrics.DUPLICATION, 0);

        try {
            method.addAllFeatures(calculateChangeHistoryFeatures(method, callable, releaseCommit));
        } catch (final IOException e) {
            log.warn("Could not compute change history for method {}. Using placeholder values.", method.signature(), e);
            method.addAllFeatures(getPlaceholderChangeFeatures());
        }
    }

    /**
     * Calculates change history features for a method by analyzing Git commit history.
     * 
     * @param trackedMethod The method to calculate change history for
     * @param callable The AST node representing the method
     * @param releaseCommit The release commit to analyze up to
     * @return Map of change history features
     * @throws IOException If Git operations fail
     */
    private Map<String, Number> calculateChangeHistoryFeatures(final AnalyzedMethod trackedMethod, final CallableDeclaration<?> callable, final RevCommit releaseCommit) throws IOException {
        log.debug("Calculating change history for method '{}' in file {}...", trackedMethod.signature(), trackedMethod.filepath());
        
        final int methodStartLine = callable.getBegin().map(p -> p.line).orElse(INVALID_LINE_NUMBER);
        if (methodStartLine == INVALID_LINE_NUMBER) {
            return getPlaceholderChangeFeatures();
        }
        
        final int methodEndLine = callable.getEnd().map(p -> p.line).orElse(INVALID_LINE_NUMBER);
        final ChangeMetrics metrics = new ChangeMetrics();
        
        analyzeCommitHistory(trackedMethod, releaseCommit, methodStartLine, methodEndLine, metrics);
        
        return metrics.toMap();
    }
    
    /**
     * Analyzes commit history for a method.
     * 
     * @param trackedMethod The method being analyzed
     * @param releaseCommit The release commit
     * @param methodStartLine The method start line
     * @param methodEndLine The method end line
     * @param metrics The metrics object to update
     * @throws IOException If Git operations fail
     */
    private void analyzeCommitHistory(final AnalyzedMethod trackedMethod, 
                                    final RevCommit releaseCommit, 
                                    final int methodStartLine, 
                                    final int methodEndLine, 
                                    final ChangeMetrics metrics) throws IOException {
        try {
            final Iterable<RevCommit> commits = git.getCommitLog(releaseCommit.getName(), trackedMethod.filepath());
            
            int commitCount = 0;
            for (final RevCommit commit : commits) {
                commitCount++;
                if (commit.getParentCount() > 0) {
                    isMethodTouchedInCommit(trackedMethod.filepath(), commit, methodStartLine, methodEndLine, metrics);
                }
            }
            log.debug("Analyzed {} commits for method history.", commitCount);
        } catch (final GitAPIException e) {
            throw new IOException("Failed to get commit log for file " + trackedMethod.filepath(), e);
        }
    }

    /**
     * Checks if a method was touched in a specific commit by analyzing diff entries.
     * 
     * @param filePath The file path containing the method
     * @param commit The commit to check
     * @param methodStart The starting line number of the method
     * @param methodEnd The ending line number of the method
     * @param metrics The metrics object to update if method was touched
     * @throws IOException If diff analysis fails
     */
    private void isMethodTouchedInCommit(final String filePath, 
                                        final RevCommit commit, 
                                        final int methodStart, 
                                        final int methodEnd, 
                                        final ChangeMetrics metrics) throws IOException {
        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            diffFormatter.setRepository(git.getRepository());
            final List<DiffEntry> diffs = diffFormatter.scan(commit.getParent(PARENT_COMMIT_INDEX).getTree(), commit.getTree());

            for (final DiffEntry diff : diffs) {
                if (!diff.getNewPath().equals(filePath)) {
                    continue;
                }

                processDiffForMethod(diff, diffFormatter, methodStart, methodEnd, metrics, commit);
            }
        }
    }
    
    /**
     * Processes a diff entry to check if the method was touched.
     * 
     * @param diff The diff entry
     * @param diffFormatter The diff formatter
     * @param methodStart The method start line
     * @param methodEnd The method end line
     * @param metrics The metrics object
     * @param commit The commit being analyzed
     * @throws IOException If diff processing fails
     */
    private void processDiffForMethod(final DiffEntry diff, 
                                   final DiffFormatter diffFormatter, 
                                   final int methodStart, 
                                   final int methodEnd, 
                                   final ChangeMetrics metrics, 
                                   final RevCommit commit) throws IOException {
        boolean methodWasTouched = false;
        final FileHeader fileHeader = diffFormatter.toFileHeader(diff);
        
        for (final Edit edit : fileHeader.toEditList()) {
            if (isMethodAffectedByEdit(methodStart, methodEnd, edit)) {
                metrics.update(edit.getLengthA(), edit.getLengthB());
                methodWasTouched = true;
            }
        }
        
        if (methodWasTouched) {
            metrics.incrementRevisions();
            metrics.addAuthor(commit.getAuthorIdent().getName());
        }
    }
    
    /**
     * Checks if a method is affected by a specific edit.
     * 
     * @param methodStart The method start line
     * @param methodEnd The method end line
     * @param edit The edit to check
     * @return true if the method is affected, false otherwise
     */
    private boolean isMethodAffectedByEdit(final int methodStart, final int methodEnd, final Edit edit) {
        return Math.max(methodStart, edit.getBeginB() + LINE_OFFSET) <= Math.min(methodEnd, edit.getEndB());
    }
    
    /**
     * Inner class to track change metrics for a method.
     */
    private static class ChangeMetrics {
        private final Set<String> authors = new HashSet<>();
        private int revisionCount = 0;
        private int totalLinesAdded = 0;
        private int totalLinesDeleted = 0;
        private int maxChurn = 0;

        /**
         * Adds an author to the set of authors.
         * 
         * @param name The author name
         */
        void addAuthor(final String name) { 
            authors.add(name); 
        }
        
        /**
         * Increments the revision count.
         */
        void incrementRevisions() { 
            revisionCount++; 
        }
        
        /**
         * Updates metrics with new edit information.
         * 
         * @param deleted Number of lines deleted
         * @param added Number of lines added
         */
        void update(final int deleted, final int added) {
            totalLinesAdded += added;
            totalLinesDeleted += deleted;
            maxChurn = Math.max(maxChurn, deleted + added);
        }

        /**
         * Converts metrics to a map of features.
         * 
         * @return Map of feature names to values
         */
        Map<String, Number> toMap() {
            final Map<String, Number> features = new HashMap<>();
            features.put(CodeQualityMetrics.NR, revisionCount);
            features.put(CodeQualityMetrics.NAUTH, authors.size());
            features.put(CodeQualityMetrics.STMT_ADDED, totalLinesAdded);
            features.put(CodeQualityMetrics.STMT_DELETED, totalLinesDeleted);
            features.put(CodeQualityMetrics.MAX_CHURN, maxChurn);
            features.put(CodeQualityMetrics.AVG_CHURN, calculateAverageChurn());
            return features;
        }
        
        /**
         * Calculates the average churn per revision.
         * 
         * @return The average churn value
         */
        private double calculateAverageChurn() {
            return (revisionCount > 0) ? (double)(totalLinesAdded + totalLinesDeleted) / revisionCount : 0;
        }
    }

    /**
     * Returns placeholder change features when change history calculation fails.
     * 
     * @return Map of placeholder change features with zero values
     */
    private Map<String, Number> getPlaceholderChangeFeatures() {
        final Map<String, Number> features = new HashMap<>();
        features.put(CodeQualityMetrics.NR, 0);
        features.put(CodeQualityMetrics.NAUTH, 0);
        features.put(CodeQualityMetrics.STMT_ADDED, 0);
        features.put(CodeQualityMetrics.STMT_DELETED, 0);
        features.put(CodeQualityMetrics.MAX_CHURN, 0);
        features.put(CodeQualityMetrics.AVG_CHURN, 0);
        return features;
    }
}