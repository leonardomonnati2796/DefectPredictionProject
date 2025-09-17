package com.ispw2;

import com.ispw2.analysis.CodeQualityAnalyzer;
import com.ispw2.analysis.MethodFeatureComparator;
import com.ispw2.analysis.RefactoringImpactAnalyzer;
import com.ispw2.classification.MachineLearningModelTrainer;
import com.ispw2.connectors.VersionControlConnector;
import com.ispw2.connectors.BugTrackingConnector;
import com.ispw2.model.SoftwareRelease;
import com.ispw2.preprocessing.DatasetPreprocessor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.bridge.SLF4JBridgeHandler;
import weka.classifiers.Classifier;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class DefectPredictionPipeline {

    private static final Logger log = LoggerFactory.getLogger(DefectPredictionPipeline.class);
    private static final List<SoftwareProject> PROJECTS_TO_ANALYZE = Arrays.asList(
            new SoftwareProject("BOOKKEEPER", "https://github.com/apache/bookkeeper.git"),
            new SoftwareProject("OPENJPA", "https://github.com/apache/openjpa.git")
    );

    private static final String DATASETS_DIR_NAME = "datasets";
    private static final String GIT_PROJECTS_DIR_NAME = "github_projects";

    private static final class ProjectContext {
        private final ConfigurationManager config;
        private final String projectName;
        private final String datasetsBasePath;
        private final String originalCsvPath;
        private final String processedArffPath;
        private final String modelPath;
        private final VersionControlConnector git;
        private final BugTrackingConnector jira;
        private final List<SoftwareRelease> releases;
        private final Map<String, RevCommit> releaseCommits;

        private ProjectContext(Builder builder) {
            this.config = builder.config;
            this.projectName = builder.projectName;
            this.datasetsBasePath = builder.datasetsBasePath;
            this.originalCsvPath = builder.originalCsvPath;
            this.processedArffPath = builder.processedArffPath;
            this.modelPath = builder.modelPath;
            this.git = builder.git;
            this.jira = builder.jira;
            this.releases = builder.releases;
            this.releaseCommits = builder.releaseCommits;
        }

        public ConfigurationManager config() { return config; }
        public String projectName() { return projectName; }
        public String datasetsBasePath() { return datasetsBasePath; }
        public String originalCsvPath() { return originalCsvPath; }
        public String processedArffPath() { return processedArffPath; }
        public String modelPath() { return modelPath; }
        public VersionControlConnector git() { return git; }
        public BugTrackingConnector jira() { return jira; }
        public List<SoftwareRelease> releases() { return releases; }
        public Map<String, RevCommit> releaseCommits() { return releaseCommits; }

        public static class Builder {
            private ConfigurationManager config;
            private String projectName;
            private String datasetsBasePath;
            private String originalCsvPath;
            private String processedArffPath;
            private String modelPath;
            private VersionControlConnector git;
            private BugTrackingConnector jira;
            private List<SoftwareRelease> releases;
            private Map<String, RevCommit> releaseCommits;

            public Builder config(ConfigurationManager config) {
                this.config = config;
                return this;
            }

            public Builder projectName(String projectName) {
                this.projectName = projectName;
                return this;
            }

            public Builder datasetsBasePath(String datasetsBasePath) {
                this.datasetsBasePath = datasetsBasePath;
                return this;
            }

            public Builder originalCsvPath(String originalCsvPath) {
                this.originalCsvPath = originalCsvPath;
                return this;
            }

            public Builder processedArffPath(String processedArffPath) {
                this.processedArffPath = processedArffPath;
                return this;
            }

            public Builder modelPath(String modelPath) {
                this.modelPath = modelPath;
                return this;
            }

            public Builder git(VersionControlConnector git) {
                this.git = git;
                return this;
            }

            public Builder jira(BugTrackingConnector jira) {
                this.jira = jira;
                return this;
            }

            public Builder releases(List<SoftwareRelease> releases) {
                this.releases = releases;
                return this;
            }

            public Builder releaseCommits(Map<String, RevCommit> releaseCommits) {
                this.releaseCommits = releaseCommits;
                return this;
            }

            public ProjectContext build() {
                return new ProjectContext(this);
            }
        }
    }

    /**
     * Main entry point for the defect prediction pipeline.
     * Initializes the system, creates necessary directories, and processes all configured projects.
     * 
     * @param args Command line arguments (currently unused)
     */
    public static void main(final String[] args) {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        
        log.info("Starting Defect Prediction Analysis...");
        
        final ConfigurationManager config = new ConfigurationManager();

        try {
            log.debug("Setting up project directories...");
            final String executionDir = System.getProperty("user.dir");
            final Path projectRoot = Paths.get(executionDir).getParent();
            
            if (projectRoot == null) {
                log.error("Cannot determine parent directory. Please run from within the project folder.");
                return;
            }
            
            if(log.isDebugEnabled()){
                log.debug("Execution directory: {}", executionDir);
                log.debug("Project root determined as: {}", projectRoot);
            }
            
            final Path datasetsPath = projectRoot.resolve(DATASETS_DIR_NAME);
            final Path gitProjectsPath = projectRoot.resolve(GIT_PROJECTS_DIR_NAME);
            Files.createDirectories(datasetsPath);
            Files.createDirectories(gitProjectsPath);
            
            if (log.isInfoEnabled()) {
                log.info("Output for datasets: {}", datasetsPath);
                log.info("Output for git clones: {}", gitProjectsPath);
            }

            processAllProjects(config, datasetsPath, gitProjectsPath);

        } catch (final IOException e) {
            log.error("A fatal I/O error occurred while setting up directories.", e);
        }

        log.info("All projects processed and evaluated successfully.");
    }

    /**
     * Processes all configured software projects through the complete defect prediction pipeline.
     * Each project goes through dataset generation, preprocessing, analysis, and simulation.
     * 
     * @param config Configuration manager containing system settings
     * @param datasetsPath Path where datasets will be stored
     * @param gitProjectsPath Path where Git repositories will be cloned
     */
    private static void processAllProjects(ConfigurationManager config, Path datasetsPath, Path gitProjectsPath) {
        log.debug("Starting to process all configured projects...");
        for (final SoftwareProject project : PROJECTS_TO_ANALYZE) {
            MDC.put("projectName", project.name());
            log.debug("--- Start processing project: {} ---", project.name());
            try {
                runPipelineFor(config, project, datasetsPath.toString(), gitProjectsPath.toString());
            } catch (Exception e) {
                log.error("A fatal error occurred during the pipeline for project {}. Moving to the next one.", project.name(), e);
            } finally {
                log.debug("--- Finished processing project: {} ---", project.name());
                MDC.clear();
            }
        }
        log.debug("All configured projects have been processed.");
    }

    /**
     * Executes the complete defect prediction pipeline for a single software project.
     * This includes dataset generation, preprocessing, machine learning training, and analysis.
     * 
     * @param config Configuration manager with system settings
     * @param project The software project to analyze
     * @param datasetsBasePath Base path for storing datasets
     * @param gitProjectsPath Path for Git repository storage
     * @throws IOException If file operations fail
     */
    private static void runPipelineFor(ConfigurationManager config, final SoftwareProject project, final String datasetsBasePath, final String gitProjectsPath) throws IOException {
        log.info("---------------------------------------------------------");
        log.info("--- STARTING PIPELINE FOR: {} ---", project.name());
        log.info("---------------------------------------------------------");

        final String originalCsvPath = Paths.get(datasetsBasePath, project.name() + ".csv").toString();
        final String processedArffPath = Paths.get(datasetsBasePath, project.name() + "_processed.arff").toString();
        final String repoPath = Paths.get(gitProjectsPath, project.name()).toString();
        final String modelPath = Paths.get(datasetsBasePath, project.name() + "_best.model").toString();

        final VersionControlConnector git = new VersionControlConnector(project.name(), project.gitUrl(), repoPath);
        git.cloneOrOpenRepo(); 

        final BugTrackingConnector jira = new BugTrackingConnector(project.name());
        final List<SoftwareRelease> releases = jira.getProjectReleases();
        final Map<String, RevCommit> releaseCommits = git.getReleaseCommits(releases);
        
        ProjectContext context = new ProjectContext.Builder()
                .config(config)
                .projectName(project.name())
                .datasetsBasePath(datasetsBasePath)
                .originalCsvPath(originalCsvPath)
                .processedArffPath(processedArffPath)
                .modelPath(modelPath)
                .git(git)
                .jira(jira)
                .releases(releases)
                .releaseCommits(releaseCommits)
                .build();
        
        if (log.isDebugEnabled()) {
            log.debug("Created project context for pipeline: {}", context);
        }

        generateDatasetIfNotExists(context);
        preprocessData(context);
        runAnalysisAndSimulation(context);
        
        log.info("--- FINISHED PIPELINE FOR: {} ---", project.name());
    }

    /**
     * Executes the analysis and simulation phase of the defect prediction pipeline.
     * This includes training the best classifier, finding actionable features,
     * running what-if simulations, and comparing method features.
     * 
     * @param context Project context containing all necessary data and configurations
     */
    private static void runAnalysisAndSimulation(ProjectContext context) {
        try {
            final MachineLearningModelTrainer runner = new MachineLearningModelTrainer(context.config(), context.processedArffPath(), context.modelPath());
            final Classifier bestModel = runner.getBestClassifier();
            
            final CodeQualityAnalyzer analyzer = new CodeQualityAnalyzer(context.config(), context.originalCsvPath(), context.processedArffPath(), context.git(), context.releaseCommits());
            final String aFeature = analyzer.findAndSaveActionableMethod();

            final RefactoringImpactAnalyzer simulator = new RefactoringImpactAnalyzer(context.processedArffPath(), bestModel, aFeature);
            simulator.runFullDatasetSimulation();

            final String originalMethodPath = Paths.get(context.datasetsBasePath(), context.projectName() + "_AFMethod.txt").toString();
            final Path projectParentPath = Paths.get(context.datasetsBasePath()).getParent();
            final String refactoredMethodPath = projectParentPath.resolve("AFMethod_refactored").resolve(context.projectName() + "_AFMethod_refactored.txt").toString();
            
            if (log.isDebugEnabled()){
                log.debug("Comparing original method at '{}' with refactored method at '{}'", originalMethodPath, refactoredMethodPath);
            }
            final MethodFeatureComparator comparer = new MethodFeatureComparator();
            comparer.compareMethods(originalMethodPath, refactoredMethodPath);

        } catch (Exception e) {
            log.error("An error occurred during analysis and simulation for project {}", context.projectName(), e);
        }
    }

    /**
     * Generates the dataset for the project if it doesn't already exist.
     * Checks for existing CSV file and only generates if missing or empty.
     * 
     * @param context Project context containing dataset paths and configurations
     */
    private static void generateDatasetIfNotExists(ProjectContext context) {
        log.info("[Milestone 1, Step 1] Checking for Dataset...");
        final File datasetFile = new File(context.originalCsvPath());
        log.debug("Checking for dataset file at: {}", context.originalCsvPath());
        
        if (datasetFile.exists() && datasetFile.length() > 0) {
            log.info("Dataset already exists. Skipping generation.");
        } else {
            log.info("Dataset not found or is empty. Generating...");
            final ProjectDatasetBuilder generator = new ProjectDatasetBuilder(context.config(), context.projectName(), context.git(), context.jira(), context.releases(), context.releaseCommits());
            generator.generateCsv(context.datasetsBasePath());
        }
    }

    /**
     * Preprocesses the raw CSV data into ARFF format for machine learning analysis.
     * Handles data cleaning, feature scaling, and format conversion.
     * 
     * @param context Project context containing dataset paths and configurations
     * @throws IOException If preprocessing fails
     */
    private static void preprocessData(ProjectContext context) throws IOException {
        log.info("[...] Preprocessing data for analysis...");
        final File arffFile = new File(context.processedArffPath());
        log.debug("Checking for preprocessed file at: {}", context.processedArffPath());
        if (arffFile.exists() && arffFile.length() > 0) {
            log.info("Processed ARFF file already exists. Skipping preprocessing.");
        } else {
            log.info("ARFF file not found. Starting preprocessing...");
            try {
                final DatasetPreprocessor processor = new DatasetPreprocessor(context.config(), context.originalCsvPath(), context.processedArffPath());
                processor.processData();
                log.info("Data preprocessing complete. Output: {}", context.processedArffPath());
            } catch (Exception e) {
                throw new IOException("Failed to preprocess data", e);
            }
        }
    }
}