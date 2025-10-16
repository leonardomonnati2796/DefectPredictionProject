package com.ispw2;

import com.ispw2.analysis.MethodAnalysisTracker;
import com.ispw2.connectors.VersionControlConnector;
import com.ispw2.connectors.BugTrackingConnector;
import com.ispw2.model.BugReport;
import com.ispw2.model.SoftwareRelease;
import com.ispw2.model.AnalyzedMethod;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class ProjectDatasetBuilder {

    private static final Logger log = LoggerFactory.getLogger(ProjectDatasetBuilder.class);
    private static final String[] CSV_HEADERS = {
        "Project", "MethodName", "Release", "CodeSmells", "CyclomaticComplexity", "ParameterCount",
        "Duplication", "NR", "NAuth", "stmtAdded", "stmtDeleted", "maxChurn", "avgChurn", "IsBuggy"
    };

    private static final double PROPORTION_DEFAULT_COEFFICIENT = 1.5;
    private static final String BUGGY_YES = "yes";
    private static final String BUGGY_NO = "no";
    private static final String METHOD_KEY_SEPARATOR = "::";

    private final ConfigurationManager config;
    private final String projectName;
    private final VersionControlConnector git;
    private final BugTrackingConnector jira;
    private final List<SoftwareRelease> releases;
    private final Map<String, RevCommit> releaseCommits;

    /**
     * Constructs a new ProjectDatasetBuilder for generating defect prediction datasets.
     * 
     * @param config Configuration manager with system settings
     * @param projectName Name of the software project to analyze
     * @param git Version control connector for Git operations
     * @param jira Bug tracking connector for JIRA operations
     * @param releases List of software releases for the project
     * @param releaseCommits Map of release names to Git commits
     */
    public ProjectDatasetBuilder(ConfigurationManager config, final String projectName, final VersionControlConnector git, final BugTrackingConnector jira, final List<SoftwareRelease> releases, final Map<String, RevCommit> releaseCommits) {
        this.config = config;
        this.projectName = projectName;
        this.git = git;
        this.jira = jira;
        this.releases = releases != null ? new ArrayList<>(releases) : null;
        this.releaseCommits = releaseCommits != null ? new HashMap<>(releaseCommits) : null;
    }

    /**
     * Generates a CSV dataset containing method-level metrics and bug information.
     * The dataset includes code quality metrics, change history, and bug labels for each method.
     * 
     * @param basePath Base directory path where the CSV file will be saved
     */
    public void generateCsv(final String basePath) {
        log.info("Starting dataset generation for project {}...", this.projectName);
        final String csvFilePath = Paths.get(basePath, this.projectName + ".csv").toString();

        try {
            final List<BugReport> tickets = jira.getBugTickets();
            git.findAndSetFixCommits(tickets);
            setVersionIndices(tickets, releases);
            final double pMedian = calculateProportionCoefficient(tickets);
            log.debug("Calculated proportion median for bug introduction: {}", pMedian);

            final Map<String, List<String>> bugToMethodsMap = git.getBugToMethodsMap(tickets);
            final MethodAnalysisTracker tracker = new MethodAnalysisTracker(git);

            final List<String[]> csvData = buildCsvData(releases, tickets, releaseCommits, tracker, bugToMethodsMap, pMedian);
            log.debug("Generated {} total rows (including header) for the dataset.", csvData.size());

            writeToCsv(csvFilePath, csvData);
            log.info("Dataset successfully written to {}", csvFilePath);

        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate dataset for project " + this.projectName, e);
        }
    }
    
    /**
     * Builds the CSV data by processing each release and extracting method metrics.
     * 
     * @param allReleases List of all software releases
     * @param tickets List of bug reports
     * @param releaseCommits Map of release names to Git commits
     * @param tracker Method analysis tracker
     * @param bugToMethodsMap Map linking bugs to affected methods
     * @param pMedian Proportion median for bug introduction calculation
     * @return List of CSV rows as string arrays
     * @throws IOException If data processing fails
     */
    private List<String[]> buildCsvData(final List<SoftwareRelease> allReleases, final List<BugReport> tickets, final Map<String, RevCommit> releaseCommits, final MethodAnalysisTracker tracker, final Map<String, List<String>> bugToMethodsMap, final double pMedian) throws IOException {
        final List<String[]> csvData = new ArrayList<>();
        csvData.add(CSV_HEADERS);

        final double cutoffPercentage = config.getReleaseCutoffPercentage();
        final int releaseCutoff = (int) Math.ceil(allReleases.size() * cutoffPercentage);
        final List<SoftwareRelease> releasesToAnalyze = allReleases.subList(0, releaseCutoff);
        log.debug("Analyzing {} of {} releases (cutoff at {}%).", releasesToAnalyze.size(), allReleases.size(), cutoffPercentage * 100);

        for (final SoftwareRelease release : releasesToAnalyze) {
            log.debug("Processing release: {}", release.name());
            final RevCommit releaseCommit = releaseCommits.get(release.name());
            if (releaseCommit == null) {
                log.warn("Skipping release {} as no commit was found for it.", release.name());
                continue;
            }

            final List<AnalyzedMethod> methods = tracker.getMethodsForRelease(releaseCommit);
            log.debug("Found {} methods for release {}", methods.size(), release.name());
            for (final AnalyzedMethod method : methods) {
                final String[] row = createCsvRow(method, release, tickets, bugToMethodsMap, pMedian);
                csvData.add(row);
            }
        }
        return csvData;
    }

    /**
     * Creates a CSV row for a specific method and release with all calculated metrics.
     * 
     * @param method The analyzed method to create a row for
     * @param release The software release the method belongs to
     * @param tickets List of all bug reports
     * @param bugToMethodsMap Map linking bug keys to affected methods
     * @param pMedian Proportion median for bug introduction calculation
     * @return Array of strings representing the CSV row
     */
    private String[] createCsvRow(final AnalyzedMethod method, final SoftwareRelease release, final List<BugReport> tickets, final Map<String, List<String>> bugToMethodsMap, final double pMedian) {
        final boolean isBuggy = isMethodBuggy(method, release, tickets, pMedian, bugToMethodsMap);
        final Map<String, Number> features = method.getFeatures();
        final String methodName = method.filepath() + "/" + method.signature();

        return new String[]{
            this.projectName, methodName, release.name(),
            features.getOrDefault("CodeSmells", 0).toString(),
            features.getOrDefault("CyclomaticComplexity", 0).toString(),
            features.getOrDefault("ParameterCount", 0).toString(),
            features.getOrDefault("Duplication", 0).toString(),
            features.getOrDefault("NR", 0).toString(),
            features.getOrDefault("NAuth", 0).toString(),
            features.getOrDefault("stmtAdded", 0).toString(),
            features.getOrDefault("stmtDeleted", 0).toString(),
            features.getOrDefault("maxChurn", 0).toString(),
            String.format(Locale.US, "%.2f", features.getOrDefault("avgChurn", 0.0)),
            isBuggy ? BUGGY_YES : BUGGY_NO
        };
    }

    /**
     * Writes the CSV data to a file using the specified file path.
     * 
     * @param filePath The path where the CSV file should be written
     * @param csvData List of string arrays representing CSV rows
     * @throws IOException If writing to file fails
     */
    private void writeToCsv(final String filePath, final List<String[]> csvData) throws IOException {
        final CSVFormat format = CSVFormat.DEFAULT.builder()
                .setQuoteMode(QuoteMode.ALL)
                .build();
        try (FileWriter writer = new FileWriter(filePath, StandardCharsets.UTF_8);
             CSVPrinter csvPrinter = new CSVPrinter(writer, format)) {
            csvPrinter.printRecords(csvData);
        }
    }

    /**
     * Determines if a method is buggy based on bug reports and version information.
     * 
     * @param method The method to check for bugginess
     * @param currentRelease The current software release
     * @param allTickets List of all bug reports
     * @param pMedian Proportion median for bug introduction calculation
     * @param bugToMethodsMap Map linking bug keys to affected methods
     * @return true if the method is considered buggy, false otherwise
     */
    private boolean isMethodBuggy(final AnalyzedMethod method, final SoftwareRelease currentRelease, final List<BugReport> allTickets, final double pMedian, final Map<String, List<String>> bugToMethodsMap) {
        final String methodKey = method.filepath() + METHOD_KEY_SEPARATOR + method.signature();
        
        for (final BugReport ticket : allTickets) {
            final List<String> fixedMethods = bugToMethodsMap.get(ticket.getKey());
            if (fixedMethods == null || !fixedMethods.contains(methodKey)) continue;

            int iv = calculateIntroductionVersion(ticket, pMedian);
            int fv = ticket.getFixedVersionIndex();

            if (iv > 0 && fv > 0 && currentRelease.index() >= iv && currentRelease.index() < fv) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Calculates the introduction version index for a bug report using proportion median.
     * 
     * @param ticket The bug report to calculate introduction version for
     * @param pMedian The proportion median coefficient
     * @return The calculated introduction version index
     */
    private int calculateIntroductionVersion(BugReport ticket, double pMedian) {
        int iv = ticket.getIntroductionVersionIndex();
        int fv = ticket.getFixedVersionIndex();
        int ov = ticket.getOpeningVersionIndex();

        if (iv <= 0 && fv > 0 && ov > 0 && fv > ov) {
            iv = (int) Math.round(fv - (fv - ov) * pMedian);
            return Math.max(1, iv);
        }
        return iv;
    }

    /**
     * Sets version indices for all bug tickets based on release information.
     * 
     * @param tickets List of bug reports to set version indices for
     * @param releases List of software releases for index mapping
     */
    private void setVersionIndices(final List<BugReport> tickets, final List<SoftwareRelease> releases) {
        final Map<String, Integer> releaseNameIndexMap = releases.stream().collect(Collectors.toMap(SoftwareRelease::name, SoftwareRelease::index));
        for (final BugReport ticket : tickets) {
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

    /**
     * Finds the release index for a given date by finding the latest release before or on that date.
     * 
     * @param date The date to find the release for
     * @param releases List of software releases sorted by date
     * @return The index of the appropriate release, or -1 if no release found
     */
    private int findReleaseIndexForDate(final LocalDate date, final List<SoftwareRelease> releases) {
        for (final SoftwareRelease release : releases) {
            if (!date.isAfter(release.releaseDate())) return release.index();
        }
        return releases.isEmpty() ? -1 : releases.get(releases.size() - 1).index();
    }

    /**
     * Calculates the proportion coefficient used for bug introduction version estimation.
     * 
     * @param tickets List of bug reports to calculate the coefficient from
     * @return The calculated proportion median coefficient
     */
    private double calculateProportionCoefficient(final List<BugReport> tickets) {
        final List<Double> pValues = tickets.stream()
                .filter(t -> t.getIntroductionVersionIndex() > 0 && t.getFixedVersionIndex() > 0 && t.getOpeningVersionIndex() > 0 && t.getFixedVersionIndex() > t.getOpeningVersionIndex())
                .map(t -> (double) (t.getFixedVersionIndex() - t.getIntroductionVersionIndex()) / (t.getFixedVersionIndex() - t.getOpeningVersionIndex()))
                .sorted()
                .collect(Collectors.toList());
        if (pValues.isEmpty()) return PROPORTION_DEFAULT_COEFFICIENT;
        
        final int size = pValues.size();
        if (size % 2 == 1) {
            return pValues.get(size / 2);
        }
        return (pValues.get(size / 2 - 1) + pValues.get(size / 2)) / 2.0;
    }
}