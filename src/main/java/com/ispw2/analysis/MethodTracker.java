package com.ispw2.analysis;

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

    public MethodTracker(final GitConnector git) {
        this.git = git;
    }

    public List<TrackedMethod> getMethodsForRelease(final RevCommit releaseCommit) throws IOException, GitAPIException {
        final String commitId = releaseCommit.getName();
        final List<String> javaFiles = git.getJavaFilesForCommit(commitId);

        final List<TrackedMethod> currentMethods = new ArrayList<>();
        final Map<TrackedMethod, CallableDeclaration<?>> methodAstMap = new HashMap<>();

        for (final String file : javaFiles) {
            extractMethodsFromFile(file, commitId, currentMethods, methodAstMap);
        }

        for(final TrackedMethod method : currentMethods) {
            final CallableDeclaration<?> callable = methodAstMap.get(method);
            calculateAllFeatures(method, callable, releaseCommit);
        }

        lastKnownMethods.clear();
        currentMethods.forEach(m -> lastKnownMethods.put(m.filepath() + "::" + m.signature(), m));

        return currentMethods;
    }

    private void extractMethodsFromFile(final String file, final String commitId, final List<TrackedMethod> currentMethods, final Map<TrackedMethod, CallableDeclaration<?>> methodAstMap) throws IOException {
        final String content = git.getFileContent(file, commitId);
        if (content == null || content.isEmpty()) return;

        try {
            final CompilationUnit cu = StaticJavaParser.parse(content);
            cu.findAll(CallableDeclaration.class).forEach(callable -> {
                final String signature = callable.getSignature().asString();
                final String fullSignatureKey = file + "::" + signature;
                final String id = lastKnownMethods.getOrDefault(fullSignatureKey, new TrackedMethod(UUID.randomUUID().toString(), "", "")).id();
                
                final TrackedMethod trackedMethod = new TrackedMethod(id, signature, file);
                currentMethods.add(trackedMethod);
                methodAstMap.put(trackedMethod, callable);
            });
        } catch (final Exception e) {
            log.warn("Failed to parse Java file: {}", file);
        }
    }

    private void calculateAllFeatures(final TrackedMethod method, final CallableDeclaration<?> callable, final RevCommit releaseCommit) {
        method.addAllFeatures(StaticMetricsCalculator.calculateAll(callable));
        method.addFeature("Duplication", 0);

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

        final Set<String> authors = new HashSet<>();
        int revisionCount = 0;
        int totalLinesAdded = 0;
        int totalLinesDeleted = 0;
        int maxChurn = 0;

        final Iterable<RevCommit> commits = git.getGit().log().add(releaseCommit.getId()).addPath(trackedMethod.filepath()).call();

        for (final RevCommit commit : commits) {
            if (commit.getParentCount() == 0) continue;
            
            try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                diffFormatter.setRepository(git.getGit().getRepository());
                final List<DiffEntry> diffs = diffFormatter.scan(commit.getParent(0).getTree(), commit.getTree());
                for (final DiffEntry diff : diffs) {
                    if (!diff.getNewPath().equals(trackedMethod.filepath())) continue;

                    boolean methodWasTouched = false;
                    final FileHeader fileHeader = diffFormatter.toFileHeader(diff);
                    for (final Edit edit : fileHeader.toEditList()) {
                        if (Math.max(methodStartLine, edit.getBeginB() + 1) <= Math.min(methodEndLine, edit.getEndB())) {
                            totalLinesAdded += edit.getLengthB();
                            totalLinesDeleted += edit.getLengthA();
                            maxChurn = Math.max(maxChurn, edit.getLengthA() + edit.getLengthB());
                            methodWasTouched = true;
                        }
                    }
                    if(methodWasTouched) {
                        revisionCount++;
                        authors.add(commit.getAuthorIdent().getName());
                    }
                }
            }
        }
        
        final Map<String, Number> features = new HashMap<>();
        features.put("NR", revisionCount);
        features.put("NAuth", authors.size());
        features.put("stmtAdded", totalLinesAdded);
        features.put("stmtDeleted", totalLinesDeleted);
        features.put("maxChurn", maxChurn);
        features.put("avgChurn", (revisionCount > 0) ? (double)(totalLinesAdded + totalLinesDeleted) / revisionCount : 0);
        return features;
    }

    private Map<String, Number> getPlaceholderChangeFeatures() {
        final Map<String, Number> features = new HashMap<>();
        features.put("NR", 0);
        features.put("NAuth", 0);
        features.put("stmtAdded", 0);
        features.put("stmtDeleted", 0);
        features.put("maxChurn", 0);
        features.put("avgChurn", 0);
        return features;
    }
}