package com.ispw2.analysis;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.ispw2.connectors.GitConnector;
import com.ispw2.model.TrackedMethod;
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
import java.util.*;

public class MethodTracker {
    private static final Logger log = LoggerFactory.getLogger(MethodTracker.class);
    private final GitConnector git;
    private final Map<String, TrackedMethod> lastKnownMethods = new HashMap<>();

    private static final String METHOD_KEY_SEPARATOR = "::";

    public MethodTracker(final GitConnector git) {
        this.git = git;
    }

    // --- MODIFICA QUI ---
    // Rimossa la dichiarazione "GitAPIException" perché non è più lanciata dal corpo del metodo.
    public List<TrackedMethod> getMethodsForRelease(final RevCommit releaseCommit) throws IOException {
        final String commitId = releaseCommit.getName();
        final List<String> javaFiles = git.getJavaFilesForCommit(commitId);

        final List<TrackedMethod> currentMethods = new ArrayList<>();
        final Map<TrackedMethod, CallableDeclaration<?>> methodAstMap = new HashMap<>();

        for (final String file : javaFiles) {
            extractMethodsFromFile(file, commitId, currentMethods, methodAstMap);
        }

        for(final TrackedMethod method : currentMethods) {
            final CallableDeclaration<?> callable = methodAstMap.get(method);
            if (callable != null) {
                calculateAllFeatures(method, callable, releaseCommit);
            }
        }

        lastKnownMethods.clear();
        currentMethods.forEach(m -> lastKnownMethods.put(m.filepath() + METHOD_KEY_SEPARATOR + m.signature(), m));

        return currentMethods;
    }

    private void extractMethodsFromFile(final String file, final String commitId, final List<TrackedMethod> currentMethods, final Map<TrackedMethod, CallableDeclaration<?>> methodAstMap) throws IOException {
        final String content = git.getFileContent(file, commitId);
        if (content == null || content.isEmpty()) return;

        try {
            final CompilationUnit cu = StaticJavaParser.parse(content);
            cu.findAll(CallableDeclaration.class).forEach(callable -> {
                final String signature = callable.getSignature().asString();
                final String fullSignatureKey = file + METHOD_KEY_SEPARATOR + signature;
                final TrackedMethod existingMethod = lastKnownMethods.get(fullSignatureKey);
                final String id = (existingMethod != null) ? existingMethod.id() : UUID.randomUUID().toString();
                
                final TrackedMethod trackedMethod = new TrackedMethod(id, signature, file);
                currentMethods.add(trackedMethod);
                methodAstMap.put(trackedMethod, callable);
            });
        } catch (final ParseProblemException e) {
            log.warn("Failed to parse Java file: {}. Skipping method extraction.", file);
        }
    }

    private void calculateAllFeatures(final TrackedMethod method, final CallableDeclaration<?> callable, final RevCommit releaseCommit) {
        method.addAllFeatures(StaticMetricsCalculator.calculateAll(callable));
        method.addFeature(Metrics.DUPLICATION, 0);

        try {
            method.addAllFeatures(calculateChangeHistoryFeatures(method, callable, releaseCommit));
        } catch (final GitAPIException | IOException e) {
            log.warn("Could not compute change history for method {}. Using placeholder values.", method.signature(), e);
            method.addAllFeatures(getPlaceholderChangeFeatures());
        }
    }

    private Map<String, Number> calculateChangeHistoryFeatures(final TrackedMethod trackedMethod, final CallableDeclaration<?> callable, final RevCommit releaseCommit) throws GitAPIException, IOException {
        final int methodStartLine = callable.getBegin().map(p -> p.line).orElse(-1);
        if (methodStartLine == -1) return getPlaceholderChangeFeatures();
        final int methodEndLine = callable.getEnd().map(p -> p.line).orElse(-1);

        final ChangeMetrics metrics = new ChangeMetrics();
        final Iterable<RevCommit> commits = git.getGit().log().add(releaseCommit.getId()).addPath(trackedMethod.filepath()).call();

        for (final RevCommit commit : commits) {
            if (commit.getParentCount() > 0) {
                isMethodTouchedInCommit(trackedMethod.filepath(), commit, methodStartLine, methodEndLine, metrics);
            }
        }
        
        return metrics.toMap();
    }

    private void isMethodTouchedInCommit(String filePath, RevCommit commit, int methodStart, int methodEnd, ChangeMetrics metrics) throws IOException {
        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            diffFormatter.setRepository(git.getGit().getRepository());
            final List<DiffEntry> diffs = diffFormatter.scan(commit.getParent(0).getTree(), commit.getTree());

            for (final DiffEntry diff : diffs) {
                if (!diff.getNewPath().equals(filePath)) continue;

                boolean methodWasTouched = false;
                final FileHeader fileHeader = diffFormatter.toFileHeader(diff);
                for (final Edit edit : fileHeader.toEditList()) {
                    if (Math.max(methodStart, edit.getBeginB() + 1) <= Math.min(methodEnd, edit.getEndB())) {
                        metrics.update(edit.getLengthA(), edit.getLengthB());
                        methodWasTouched = true;
                    }
                }
                if (methodWasTouched) {
                    metrics.incrementRevisions();
                    metrics.addAuthor(commit.getAuthorIdent().getName());
                }
            }
        }
    }
    
    // Inner class to handle change metrics calculation
    private static class ChangeMetrics {
        private final Set<String> authors = new HashSet<>();
        private int revisionCount = 0;
        private int totalLinesAdded = 0;
        private int totalLinesDeleted = 0;
        private int maxChurn = 0;

        void addAuthor(String name) { authors.add(name); }
        void incrementRevisions() { revisionCount++; }
        void update(int deleted, int added) {
            totalLinesAdded += added;
            totalLinesDeleted += deleted;
            maxChurn = Math.max(maxChurn, deleted + added);
        }

        Map<String, Number> toMap() {
            final Map<String, Number> features = new HashMap<>();
            features.put(Metrics.NR, revisionCount);
            features.put(Metrics.NAUTH, authors.size());
            features.put(Metrics.STMT_ADDED, totalLinesAdded);
            features.put(Metrics.STMT_DELETED, totalLinesDeleted);
            features.put(Metrics.MAX_CHURN, maxChurn);
            features.put(Metrics.AVG_CHURN, (revisionCount > 0) ? (double)(totalLinesAdded + totalLinesDeleted) / revisionCount : 0);
            return features;
        }
    }

    private Map<String, Number> getPlaceholderChangeFeatures() {
        final Map<String, Number> features = new HashMap<>();
        features.put(Metrics.NR, 0);
        features.put(Metrics.NAUTH, 0);
        features.put(Metrics.STMT_ADDED, 0);
        features.put(Metrics.STMT_DELETED, 0);
        features.put(Metrics.MAX_CHURN, 0);
        features.put(Metrics.AVG_CHURN, 0);
        return features;
    }
}