package com.ispw2;

import com.ispw2.analysis.MethodTracker;
import com.ispw2.connectors.GitConnector;
import com.ispw2.connectors.JiraConnector;
import com.ispw2.model.JiraTicket;
import com.ispw2.model.ProjectRelease;
import com.ispw2.model.TrackedMethod;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class DatasetGenerator {

    private static final Logger log = LoggerFactory.getLogger(DatasetGenerator.class);
    private static final String[] CSV_HEADERS = {
        "Project", "MethodName", "Release", "LOC", "CyclomaticComplexity", "ParameterCount",
        "Duplication", "NR", "NAuth", "stmtAdded", "stmtDeleted", "maxChurn", "avgChurn", "IsBuggy"
    };

    private final String projectName;
    private final GitConnector git;
    private final JiraConnector jira;
    private final List<ProjectRelease> releases;
    private final Map<String, RevCommit> releaseCommits;

    public DatasetGenerator(final String projectName, final GitConnector git, final JiraConnector jira, final List<ProjectRelease> releases, final Map<String, RevCommit> releaseCommits) {
        this.projectName = projectName;
        this.git = git;
        this.jira = jira;
        this.releases = releases;
        this.releaseCommits = releaseCommits;
    }

    public void generateCsv(final String basePath) {
        final String csvFilePath = Paths.get(basePath, this.projectName + ".csv").toString();

        try {
            final List<JiraTicket> tickets = jira.getBugTickets();
            git.findAndSetFixCommits(tickets);
            setVersionIndices(tickets, releases);
            final double pMedian = calculateProportionCoefficient(tickets);

            final Map<String, List<String>> bugToMethodsMap = git.getBugToMethodsMap(tickets);
            final MethodTracker tracker = new MethodTracker(git);

            final List<String[]> csvData = buildCsvData(releases, tickets, releaseCommits, tracker, bugToMethodsMap, pMedian);
            writeToCsv(csvFilePath, csvData);

        } catch (IOException | GitAPIException e) {
            log.error("Error during dataset generation for {}", this.projectName, e);
        }
    }
    
    private List<String[]> buildCsvData(final List<ProjectRelease> allReleases, final List<JiraTicket> tickets, final Map<String, RevCommit> releaseCommits, final MethodTracker tracker, final Map<String, List<String>> bugToMethodsMap, final double pMedian) throws IOException, GitAPIException {
        final List<String[]> csvData = new ArrayList<>();
        csvData.add(CSV_HEADERS);

        final double cutoffPercentage = ConfigurationManager.getInstance().getReleaseCutoffPercentage();
        final int releaseCutoff = (int) Math.ceil(allReleases.size() * cutoffPercentage);
        final List<ProjectRelease> releasesToAnalyze = allReleases.subList(0, releaseCutoff);

        for (final ProjectRelease release : releasesToAnalyze) {
            log.info("Analyzing release: {}", release.name());
            final RevCommit releaseCommit = releaseCommits.get(release.name());
            if (releaseCommit == null) continue;

            final List<TrackedMethod> methods = tracker.getMethodsForRelease(releaseCommit);
            for (final TrackedMethod method : methods) {
                final String[] row = createCsvRow(method, release, tickets, bugToMethodsMap, pMedian);
                csvData.add(row);
            }
        }
        return csvData;
    }

    private String[] createCsvRow(final TrackedMethod method, final ProjectRelease release, final List<JiraTicket> tickets, final Map<String, List<String>> bugToMethodsMap, final double pMedian) {
        final boolean isBuggy = isMethodBuggy(method, release, tickets, pMedian, bugToMethodsMap);
        final Map<String, Number> features = method.getFeatures();
        final String methodName = method.filepath() + "/" + method.signature();

        return new String[]{
            this.projectName, methodName, release.name(),
            features.getOrDefault("LOC", 0).toString(),
            features.getOrDefault("CyclomaticComplexity", 0).toString(),
            features.getOrDefault("ParameterCount", 0).toString(),
            features.getOrDefault("Duplication", 0).toString(),
            features.getOrDefault("NR", 0).toString(),
            features.getOrDefault("NAuth", 0).toString(),
            features.getOrDefault("stmtAdded", 0).toString(),
            features.getOrDefault("stmtDeleted", 0).toString(),
            features.getOrDefault("maxChurn", 0).toString(),
            String.format(Locale.US, "%.2f", features.getOrDefault("avgChurn", 0.0)),
            isBuggy ? "yes" : "no"
        };
    }

    private void writeToCsv(final String filePath, final List<String[]> csvData) throws IOException {
        final CSVFormat format = CSVFormat.DEFAULT.builder()
                .setQuoteMode(QuoteMode.ALL)
                .build();
        try (FileWriter writer = new FileWriter(filePath);
             CSVPrinter csvPrinter = new CSVPrinter(writer, format)) {
            csvPrinter.printRecords(csvData);
        }
    }

    private boolean isMethodBuggy(final TrackedMethod method, final ProjectRelease currentRelease, final List<JiraTicket> allTickets, final double pMedian, final Map<String, List<String>> bugToMethodsMap) {
        final String methodKey = method.filepath() + "::" + method.signature();
        for (final JiraTicket ticket : allTickets) {
            final List<String> fixedMethods = bugToMethodsMap.get(ticket.getKey());
            if (fixedMethods == null || !fixedMethods.contains(methodKey)) continue;

            int iv = ticket.getIntroductionVersionIndex();
            final int fv = ticket.getFixedVersionIndex();
            if (iv <= 0 && fv > 0 && ticket.getOpeningVersionIndex() > 0 && fv > ticket.getOpeningVersionIndex()) {
                iv = (int) Math.round(fv - (fv - ticket.getOpeningVersionIndex()) * pMedian);
                if (iv < 1) iv = 1;
            }
            if (iv > 0 && fv > 0 && currentRelease.index() >= iv && currentRelease.index() < fv) {
                return true;
            }
        }
        return false;
    }

    private void setVersionIndices(final List<JiraTicket> tickets, final List<ProjectRelease> releases) {
        final Map<String, Integer> releaseNameIndexMap = releases.stream().collect(Collectors.toMap(ProjectRelease::name, ProjectRelease::index));
        for (final JiraTicket ticket : tickets) {
            ticket.setOpeningVersionIndex(findReleaseIndexForDate(ticket.getCreationDate().toLocalDate(), releases));
            if (ticket.getResolutionDate() != null) {
                ticket.setFixedVersionIndex(findReleaseIndexForDate(ticket.getResolutionDate().toLocalDate(), releases));
            }
            ticket.getAffectedVersions().stream()
                    .map(releaseNameIndexMap::get)
                    .filter(Objects::nonNull)
                    .min(Integer::compareTo)
                    .ifPresent(ticket::setIntroductionVersionIndex);
        }
    }

    private int findReleaseIndexForDate(final LocalDate date, final List<ProjectRelease> releases) {
        for (final ProjectRelease release : releases) {
            if (!date.isAfter(release.releaseDate())) return release.index();
        }
        return releases.isEmpty() ? -1 : releases.get(releases.size() - 1).index();
    }

    private double calculateProportionCoefficient(final List<JiraTicket> tickets) {
        final List<Double> pValues = tickets.stream()
                .filter(t -> t.getIntroductionVersionIndex() > 0 && t.getFixedVersionIndex() > 0 && t.getOpeningVersionIndex() > 0 && t.getFixedVersionIndex() > t.getOpeningVersionIndex())
                .map(t -> (double) (t.getFixedVersionIndex() - t.getIntroductionVersionIndex()) / (t.getFixedVersionIndex() - t.getOpeningVersionIndex()))
                .sorted()
                .collect(Collectors.toList());
        if (pValues.isEmpty()) return 1.5;
        final int size = pValues.size();
        return (size % 2 == 1) ? pValues.get(size / 2) : (pValues.get(size / 2 - 1) + pValues.get(size / 2)) / 2.0;
    }
}