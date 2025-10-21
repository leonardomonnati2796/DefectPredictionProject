package com.ispw2;

import com.ispw2.analysis.CodeQualityAnalyzer;
import com.ispw2.analysis.MethodFeatureComparator;
import com.ispw2.analysis.RefactoringImpactAnalyzer;
import com.ispw2.classification.MachineLearningModelTrainer;
import com.ispw2.connectors.VersionControlConnector;
import com.ispw2.connectors.BugTrackingConnector;
import com.ispw2.model.SoftwareRelease;
import com.ispw2.preprocessing.DatasetPreprocessor;
import com.ispw2.util.LoggingUtils;
import com.ispw2.util.FileUtils;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.bridge.SLF4JBridgeHandler;
import weka.classifiers.Classifier;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefectPredictionPipeline {

    private static final Logger log = LoggerFactory.getLogger(DefectPredictionPipeline.class);
    
    // Constants for project configuration
    private static final List<SoftwareProject> PROJECTS_TO_ANALYZE = Arrays.asList(
            new SoftwareProject("BOOKKEEPER", "https://github.com/apache/bookkeeper.git"),
            new SoftwareProject("OPENJPA", "https://github.com/apache/openjpa.git")
    );

    // Directory names
    private static final String DATASETS_DIR_NAME = "datasets";
    private static final String GIT_PROJECTS_DIR_NAME = "github_projects";
    private static final String AFMETHOD_REFACTORED_DIR = "AFMethod_refactored";
    
    // File extensions
    private static final String CSV_EXTENSION = ".csv";
    private static final String ARFF_EXTENSION = ".arff";
    private static final String MODEL_EXTENSION = "_best.model";
    private static final String AFMETHOD_EXTENSION = "_AFMethod.txt";
    private static final String AFMETHOD_REFACTORED_EXTENSION = "_AFMethod_refactored.txt";
    private static final String PROCESSED_SUFFIX = "_processed";
    
    // Error messages
    private static final String FATAL_IO_ERROR_MSG = "A fatal I/O error occurred while setting up directories.";
    private static final String PARENT_DIR_ERROR_MSG = "Cannot determine parent directory. Please run from within the project folder.";
    private static final String CLASSIFIER_ERROR_MSG = "Failed to obtain a valid classifier for project {}";
    private static final String ACTIONABLE_FEATURE_ERROR_MSG = "Failed to find actionable feature for project {}";
    private static final String PREPROCESSING_ERROR_MSG = "Failed to preprocess data";

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
                this.releases = releases != null ? new ArrayList<>(releases) : null;
                return this;
            }

            public Builder releaseCommits(Map<String, RevCommit> releaseCommits) {
                this.releaseCommits = releaseCommits != null ? new HashMap<>(releaseCommits) : null;
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
        initializeLogging();
        log.info("Starting Defect Prediction Analysis...");
        
        final ConfigurationManager config = new ConfigurationManager();

        try {
            final Path projectRoot = determineProjectRoot();
            if (projectRoot == null) {
                return;
            }
            
            final Path[] paths = setupProjectDirectories(projectRoot);
            processAllProjects(config, paths[0], paths[1]);

        } catch (final IOException e) {
            log.error(FATAL_IO_ERROR_MSG, e);
        }

        log.info("All projects processed and evaluated successfully.");
    }
    
    /**
     * Initializes the logging system.
     */
    private static void initializeLogging() {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }
    
    /**
     * Determines the project root directory.
     * 
     * @return The project root path or null if cannot be determined
     */
    private static Path determineProjectRoot() {
        LoggingUtils.debugIfEnabled(log, "Setting up project directories...");
        try {
            final String parentDir = FileUtils.getParentDirectory(log);
            final Path projectRoot = Paths.get(parentDir);
            
            LoggingUtils.debugIfEnabled(log, "Project root determined as: {}", projectRoot);
            return projectRoot;
        } catch (final IOException e) {
            log.error(PARENT_DIR_ERROR_MSG, e);
            return null;
        }
    }
    
    /**
     * Sets up the necessary project directories.
     * 
     * @param projectRoot The project root directory
     * @return Array containing [datasetsPath, gitProjectsPath]
     * @throws IOException If directory creation fails
     */
    private static Path[] setupProjectDirectories(final Path projectRoot) throws IOException {
        final Path datasetsPath = projectRoot.resolve(DATASETS_DIR_NAME);
        final Path gitProjectsPath = projectRoot.resolve(GIT_PROJECTS_DIR_NAME);
        
        FileUtils.createDirectoryIfNotExists(datasetsPath.toString(), log);
        FileUtils.createDirectoryIfNotExists(gitProjectsPath.toString(), log);
        
        log.info("Output for datasets: {}", datasetsPath);
        log.info("Output for git clones: {}", gitProjectsPath);
        
        return new Path[]{datasetsPath, gitProjectsPath};
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
        LoggingUtils.debugIfEnabled(log, "Starting to process all configured projects...");
        for (final SoftwareProject project : PROJECTS_TO_ANALYZE) {
            MDC.put("projectName", project.name());
            LoggingUtils.debugIfEnabled(log, "--- Start processing project: {} ---", project.name());
            try {
                runPipelineFor(config, project, datasetsPath.toString(), gitProjectsPath.toString());
            } catch (Exception e) {
                log.error("A fatal error occurred during the pipeline for project {}. Moving to the next one.", project.name(), e);
            } finally {
                LoggingUtils.debugIfEnabled(log, "--- Finished processing project: {} ---", project.name());
                MDC.clear();
            }
        }
        LoggingUtils.debugIfEnabled(log, "All configured projects have been processed.");
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
    private static void runPipelineFor(final ConfigurationManager config, final SoftwareProject project, final String datasetsBasePath, final String gitProjectsPath) throws IOException {
        logPipelineStart(project.name());

        final ProjectContext context = buildProjectContext(config, project, datasetsBasePath, gitProjectsPath);
        
        LoggingUtils.debugIfEnabled(log, "Created project context for pipeline: {}", context);

        executePipelineSteps(context);
        
        log.info("--- FINISHED PIPELINE FOR: {} ---", project.name());
    }
    
    /**
     * Logs the start of pipeline execution.
     * 
     * @param projectName The name of the project being processed
     */
    private static void logPipelineStart(final String projectName) {
        log.info("---------------------------------------------------------");
        log.info("--- STARTING PIPELINE FOR: {} ---", projectName);
        log.info("---------------------------------------------------------");
    }
    
    /**
     * Builds the project context with all necessary components.
     * 
     * @param config Configuration manager
     * @param project The software project
     * @param datasetsBasePath Base path for datasets
     * @param gitProjectsPath Path for Git repositories
     * @return The built project context
     * @throws IOException If Git operations fail
     */
    private static ProjectContext buildProjectContext(final ConfigurationManager config, 
                                                    final SoftwareProject project, 
                                                    final String datasetsBasePath, 
                                                    final String gitProjectsPath) throws IOException {
        final String originalCsvPath = Paths.get(datasetsBasePath, project.name() + CSV_EXTENSION).toString();
        final String processedArffPath = Paths.get(datasetsBasePath, project.name() + PROCESSED_SUFFIX + ARFF_EXTENSION).toString();
        final String repoPath = Paths.get(gitProjectsPath, project.name()).toString();
        final String modelPath = Paths.get(datasetsBasePath, project.name() + MODEL_EXTENSION).toString();

        final VersionControlConnector git = new VersionControlConnector(project.name(), project.gitUrl(), repoPath);
        git.cloneOrOpenRepo(); 

        final BugTrackingConnector jira = new BugTrackingConnector(project.name());
        final List<SoftwareRelease> releases = jira.getProjectReleases();
        final Map<String, RevCommit> releaseCommits = git.getReleaseCommits(releases);
        
        return new ProjectContext.Builder()
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
    }
    
    /**
     * Executes all pipeline steps for the project.
     * 
     * @param context The project context
     * @throws IOException If pipeline steps fail
     */
    private static void executePipelineSteps(final ProjectContext context) throws IOException {
        generateDatasetIfNotExists(context);
        preprocessData(context);
        runAnalysisAndSimulation(context);
    }

    /**
     * Executes the analysis and simulation phase of the defect prediction pipeline.
     * This includes training the best classifier, finding actionable features,
     * running what-if simulations, and comparing method features.
     * 
     * @param context Project context containing all necessary data and configurations
     */
    private static void runAnalysisAndSimulation(final ProjectContext context) {
        try {
            final Classifier bestModel = trainBestClassifier(context);
            if (bestModel == null) {
                return;
            }
            
            final String aFeature = findActionableFeature(context);
            if (aFeature == null) {
                return;
            }

            runSimulationAndComparison(context, bestModel, aFeature);

        } catch (final Exception e) {
            log.error("An error occurred during analysis and simulation for project {}", context.projectName(), e);
        }
    }
    
    /**
     * Trains the best classifier for the project.
     * 
     * @param context The project context
     * @return The best classifier or null if training fails
     */
    private static Classifier trainBestClassifier(final ProjectContext context) {
        try {
            final MachineLearningModelTrainer runner = new MachineLearningModelTrainer(context.config(), context.processedArffPath(), context.modelPath());
            final Classifier bestModel = runner.getBestClassifier();
            
            if (bestModel == null) {
                log.error(CLASSIFIER_ERROR_MSG, context.projectName());
            }
            
            return bestModel;
        } catch (final IOException e) {
            log.error("Failed to train classifier for project {}", context.projectName(), e);
            return null;
        }
    }
    
    /**
     * Finds the actionable feature for the project.
     * 
     * @param context The project context
     * @return The actionable feature name or null if not found
     */
    private static String findActionableFeature(final ProjectContext context) {
        try {
            final CodeQualityAnalyzer analyzer = new CodeQualityAnalyzer(context.config(), context.originalCsvPath(), context.processedArffPath(), context.git(), context.releaseCommits());
            final String aFeature = analyzer.findAndSaveActionableMethod();

            if (aFeature == null) {
                log.error(ACTIONABLE_FEATURE_ERROR_MSG, context.projectName());
            }
            
            return aFeature;
        } catch (final IOException e) {
            log.error("Failed to find actionable feature for project {}", context.projectName(), e);
            return null;
        }
    }
    
    /**
     * Runs the simulation and method comparison.
     * 
     * @param context The project context
     * @param bestModel The trained classifier
     * @param aFeature The actionable feature
     */
    private static void runSimulationAndComparison(final ProjectContext context, final Classifier bestModel, final String aFeature) {
        try {
            final RefactoringImpactAnalyzer simulator = new RefactoringImpactAnalyzer(context.processedArffPath(), bestModel, aFeature);
            simulator.runFullDatasetSimulation();

            final String[] methodPaths = buildMethodPaths(context);
            
            LoggingUtils.debugIfEnabled(log, "Comparing original method at '{}' with refactored method at '{}'", methodPaths[0], methodPaths[1]);
            
            final MethodFeatureComparator comparer = new MethodFeatureComparator();
            comparer.compareMethods(methodPaths[0], methodPaths[1]);
        } catch (final IOException e) {
            log.error("Failed to run simulation and comparison for project {}", context.projectName(), e);
        }
    }
    
    /**
     * Builds the paths for original and refactored method files.
     * 
     * @param context The project context
     * @return Array containing [originalMethodPath, refactoredMethodPath]
     */
    private static String[] buildMethodPaths(final ProjectContext context) {
        final String originalMethodPath = Paths.get(context.datasetsBasePath(), context.projectName() + AFMETHOD_EXTENSION).toString();
        final Path projectParentPath = Paths.get(context.datasetsBasePath()).getParent();
        final String refactoredMethodPath = projectParentPath.resolve(AFMETHOD_REFACTORED_DIR).resolve(context.projectName() + AFMETHOD_REFACTORED_EXTENSION).toString();
        
        return new String[]{originalMethodPath, refactoredMethodPath};
    }

    /**
     * Generates the dataset for the project if it doesn't already exist.
     * Checks for existing CSV file and only generates if missing or empty.
     * 
     * @param context Project context containing dataset paths and configurations
     */
    private static void generateDatasetIfNotExists(final ProjectContext context) {
        log.info("[Milestone 1, Step 1] Checking for Dataset...");
        final File datasetFile = new File(context.originalCsvPath());
        LoggingUtils.debugIfEnabled(log, "Checking for dataset file at: {}", context.originalCsvPath());
        
        if (isDatasetValid(datasetFile)) {
            log.info("Dataset already exists. Skipping generation.");
        } else {
            generateNewDataset(context);
        }
    }
    
    /**
     * Checks if the dataset file is valid (exists and not empty).
     * 
     * @param datasetFile The dataset file to check
     * @return true if the dataset is valid, false otherwise
     */
    private static boolean isDatasetValid(final File datasetFile) {
        return FileUtils.isFileValid(datasetFile.getAbsolutePath(), log) && datasetFile.length() > 0;
    }
    
    /**
     * Generates a new dataset for the project.
     * 
     * @param context The project context
     */
    private static void generateNewDataset(final ProjectContext context) {
        log.info("Dataset not found or is empty. Generating...");
        final ProjectDatasetBuilder generator = new ProjectDatasetBuilder(
            context.config(), 
            context.projectName(), 
            context.git(), 
            context.jira(), 
            context.releases(), 
            context.releaseCommits()
        );
        generator.generateCsv(context.datasetsBasePath());
    }

    /**
     * Preprocesses the raw CSV data into ARFF format for machine learning analysis.
     * Handles data cleaning, feature scaling, and format conversion.
     * 
     * @param context Project context containing dataset paths and configurations
     * @throws IOException If preprocessing fails
     */
    private static void preprocessData(final ProjectContext context) throws IOException {
        log.info("[...] Preprocessing data for analysis...");
        final File arffFile = new File(context.processedArffPath());
        LoggingUtils.debugIfEnabled(log, "Checking for preprocessed file at: {}", context.processedArffPath());
        
        if (isArffFileValid(arffFile)) {
            log.info("Processed ARFF file already exists. Skipping preprocessing.");
        } else {
            performDataPreprocessing(context);
        }
    }
    
    /**
     * Checks if the ARFF file is valid (exists and not empty).
     * 
     * @param arffFile The ARFF file to check
     * @return true if the file is valid, false otherwise
     */
    private static boolean isArffFileValid(final File arffFile) {
        return FileUtils.isFileValid(arffFile.getAbsolutePath(), log) && arffFile.length() > 0;
    }
    
    /**
     * Performs the actual data preprocessing.
     * 
     * @param context The project context
     * @throws IOException If preprocessing fails
     */
    private static void performDataPreprocessing(final ProjectContext context) throws IOException {
        log.info("ARFF file not found. Starting preprocessing...");
        try {
            final DatasetPreprocessor processor = new DatasetPreprocessor(context.config(), context.originalCsvPath(), context.processedArffPath());
            processor.processData();
            log.info("Data preprocessing complete. Output: {}", context.processedArffPath());
        } catch (final Exception e) {
            throw new IOException(PREPROCESSING_ERROR_MSG, e);
        }
    }
}