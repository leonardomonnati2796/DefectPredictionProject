package com.ispw2.connectors;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.ispw2.model.JiraTicket;
import com.ispw2.model.ProjectRelease;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitConnector {
    private static final Logger log = LoggerFactory.getLogger(GitConnector.class);
    private static final String METHOD_KEY_SEPARATOR = "::";
    // Il pattern Regex viene compilato una sola volta per migliorare le performance.
    private static final Pattern JIRA_TICKET_PATTERN = Pattern.compile("([A-Z][A-Z0-9]+-\\d+)");

    private final String projectName;
    private final String remoteUrl;
    private final String localPath;
    private Repository repository;
    private Git git;

    public GitConnector(final String projectName, final String remoteUrl, final String localPath) {
        this.projectName = projectName;
        this.remoteUrl = remoteUrl;
        this.localPath = localPath;
    }

    public void cloneOrOpenRepo() throws IOException {
        final File repoDir = new File(this.localPath);
        try {
            this.git = Git.open(repoDir);
            this.repository = this.git.getRepository();
            log.info("Opening existing repository: {}", this.localPath);
        } catch (final RepositoryNotFoundException e) {
            log.warn("Repository not found at {}. Performing a fresh clone.", this.localPath);
            if (repoDir.exists() && !deleteDirectory(repoDir)) {
                // Lancia un'eccezione se la pulizia fallisce, invece di continuare.
                throw new IOException("Could not delete existing, corrupted directory: " + repoDir.getPath());
            }
            try {
                log.info("Cloning {}...", this.projectName);
                this.git = Git.cloneRepository()
                        .setURI(this.remoteUrl)
                        .setDirectory(repoDir)
                        .call();
                this.repository = this.git.getRepository();
                log.info("Clone complete.");
            } catch (final GitAPIException gitEx) {
                throw new IOException("Git clone failed.", gitEx);
            }
        }
    }

    public void cleanupRepo() {
        if (this.git != null) {
            this.git.close();
        }
        final File repoDir = new File(this.localPath);
        if (repoDir.exists() && log.isInfoEnabled()) {
             log.info("Cleaning up repository: {}", this.localPath);
            if (deleteDirectory(repoDir)) {
                 log.info("Cleanup successful.");
            } else {
                 log.error("Cleanup failed. Could not delete directory: {}", this.localPath);
            }
        }
    }
    
    public Map<String, RevCommit> getReleaseCommits(final List<ProjectRelease> releases) throws IOException {
        final Map<String, RevCommit> releaseCommits = new HashMap<>();
        final Map<String, Ref> tagMap = new HashMap<>();
        try {
            for (final Ref tagRef : this.git.tagList().call()) {
                tagMap.put(tagRef.getName().replace("refs/tags/", ""), tagRef);
            }
        } catch (final GitAPIException e) {
            throw new IOException("Failed to list git tags.", e);
        }

        try (RevWalk walk = new RevWalk(repository)) {
            for (final ProjectRelease release : releases) {
                final Ref tagRef = findTagRef(release.name(), tagMap);
                if (tagRef != null) {
                    releaseCommits.put(release.name(), walk.parseCommit(tagRef.getObjectId()));
                }
            }
        }
        return releaseCommits;
    }

    private Ref findTagRef(String releaseName, Map<String, Ref> tagMap) {
        final List<String> tagPatterns = Arrays.asList(
            releaseName,
            "v" + releaseName,
            "release-" + releaseName,
            this.projectName.toLowerCase() + "-" + releaseName
        );

        for (String pattern : tagPatterns) {
            if (tagMap.containsKey(pattern)) {
                return tagMap.get(pattern);
            }
        }
        return null;
    }

    public Map<String, List<String>> getBugToMethodsMap(final List<JiraTicket> tickets) throws IOException {
        final Map<String, List<String>> bugToMethods = new HashMap<>();
        try (RevWalk revWalk = new RevWalk(repository)) {
            for (final JiraTicket ticket : tickets) {
                if (ticket.getFixCommitHash() != null) {
                    final RevCommit commit = revWalk.parseCommit(repository.resolve(ticket.getFixCommitHash()));
                    if (commit.getParentCount() > 0) {
                        final RevCommit parent = revWalk.parseCommit(commit.getParent(0).getId());
                        final List<DiffEntry> diffs = getDiff(parent, commit);
                        final Set<String> affectedMethods = new HashSet<>();
                        for (final DiffEntry diff : diffs) {
                            // La condizione complessa è stata estratta in un metodo per leggibilità.
                            if (isJavaFileModification(diff)) {
                                affectedMethods.addAll(getModifiedMethods(diff, commit));
                            }
                        }
                        bugToMethods.put(ticket.getKey(), new ArrayList<>(affectedMethods));
                    }
                }
            }
        }
        return bugToMethods;
    }
    
    private boolean isJavaFileModification(DiffEntry diff) {
        return diff.getChangeType() == DiffEntry.ChangeType.MODIFY && diff.getNewPath().endsWith(".java");
    }

    private Set<String> getModifiedMethods(final DiffEntry diff, final RevCommit commit) throws IOException {
        // Usa un Set per evitare di aggiungere lo stesso metodo più volte.
        final Set<String> modifiedMethods = new HashSet<>();
        final String newPath = diff.getNewPath();
        final String fileContent = getFileContent(newPath, commit.getName());
        if (fileContent.isEmpty()) return modifiedMethods;
        
        final List<MethodDeclaration> methods;
        try {
            methods = StaticJavaParser.parse(fileContent).findAll(MethodDeclaration.class);
        } catch (final ParseProblemException e) { 
            log.warn("Failed to parse Java file during diff: {}. Details: {}", newPath, e.getMessage());
            return Collections.emptySet();
        }

        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            diffFormatter.setRepository(repository);
            final FileHeader fileHeader = diffFormatter.toFileHeader(diff);
            for (final Edit edit : fileHeader.toEditList()) {
                for (final MethodDeclaration method : methods) {
                    // Controlla se il range di linee di una modifica (edit) si sovrappone al range di linee di un metodo.
                    if (method.getRange().isPresent() &&
                        Math.max(method.getRange().get().begin.line, edit.getBeginB()) <= Math.min(method.getRange().get().end.line, edit.getEndB())) {
                        modifiedMethods.add(newPath + METHOD_KEY_SEPARATOR + method.getSignature().asString());
                    }
                }
            }
        }
        return modifiedMethods;
    }

    public void findAndSetFixCommits(final List<JiraTicket> tickets) throws GitAPIException, IOException {
        final Map<String, JiraTicket> ticketMap = new HashMap<>();
        for (final JiraTicket ticket : tickets) {
            ticketMap.put(ticket.getKey(), ticket);
        }
        final Iterable<RevCommit> commits = git.log().all().call();
        for (final RevCommit commit : commits) {
            final Matcher matcher = JIRA_TICKET_PATTERN.matcher(commit.getFullMessage());
            while (matcher.find()) {
                final String ticketKey = matcher.group(1);
                final JiraTicket ticket = ticketMap.get(ticketKey);
                if (ticket != null && ticket.getFixCommitHash() == null) {
                    ticket.setFixCommitHash(commit.getName());
                    ticket.setResolutionDate(LocalDateTime.ofInstant(commit.getAuthorIdent().getWhenAsInstant(), ZoneId.systemDefault()));
                }
            }
        }
    }

    private List<DiffEntry> getDiff(final RevCommit commit1, final RevCommit commit2) throws IOException {
        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            diffFormatter.setRepository(repository);
            diffFormatter.setDetectRenames(true);
            try (ObjectReader reader = repository.newObjectReader()) {
                final CanonicalTreeParser oldTreeParser = new CanonicalTreeParser();
                oldTreeParser.reset(reader, commit1.getTree().getId());
                final CanonicalTreeParser newTreeParser = new CanonicalTreeParser();
                newTreeParser.reset(reader, commit2.getTree().getId());
                return diffFormatter.scan(oldTreeParser, newTreeParser);
            }
        }
    }

    public List<String> getJavaFilesForCommit(final String commitId) throws IOException {
        final List<String> javaFiles = new ArrayList<>();
        // Il metodo non esegue più un 'checkout', ma ispeziona direttamente il commit.
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(repository.resolve(commitId));
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(commit.getTree());
                treeWalk.setRecursive(true);
                while (treeWalk.next()) {
                    final String path = treeWalk.getPathString();
                    if (path.endsWith(".java") && !path.toLowerCase().contains("test")) {
                        javaFiles.add(path);
                    }
                }
            }
        }
        return javaFiles;
    }

    public String getFileContent(final String filePath, final String commitId) throws IOException {
        final ObjectId objId = repository.resolve(commitId + ":" + filePath);
        if (objId == null) {
            log.warn("Could not resolve file path '{}' in commit '{}'", filePath, commitId);
            return "";
        }
        return new String(repository.open(objId).getBytes(), StandardCharsets.UTF_8);
    }
    
    public Git getGit() { 
        return git; 
    }
    
    public String getProjectName() {
        return this.projectName;
    }

    private boolean deleteDirectory(final File directory) {
        final File[] allContents = directory.listFiles();
        if (allContents != null) {
            for (final File file : allContents) {
                if (!deleteDirectory(file)) {
                    return false;
                }
            }
        }
        return directory.delete();
    }
}