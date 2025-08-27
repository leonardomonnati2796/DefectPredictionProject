package com.ispw2;

import com.ispw2.analysis.DataAnalyzer;
import com.ispw2.analysis.FeatureComparer;
import com.ispw2.analysis.WhatIfSimulator;
import com.ispw2.classification.ClassifierRunner;
import com.ispw2.connectors.GitConnector;
import com.ispw2.connectors.JiraConnector;
import com.ispw2.model.ProjectRelease;
import com.ispw2.preprocessing.DataPreprocessor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.bridge.SLF4JBridgeHandler;
import weka.classifiers.Classifier;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ParserConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final List<Project> PROJECTS_TO_ANALYZE = Arrays.asList(
            new Project("BOOKKEEPER", "https://github.com/apache/bookkeeper.git"),
            new Project("OPENJPA", "https://github.com/apache/openjpa.git")
    );

    private static final String DATASETS_DIR_NAME = "datasets";
    private static final String GIT_PROJECTS_DIR_NAME = "github_projects";

    private record ProjectContext(
        ConfigurationManager config,
        String projectName,
        String datasetsBasePath,
        String originalCsvPath,
        String processedArffPath,
        String modelPath,
        GitConnector git,
        JiraConnector jira,
        List<ProjectRelease> releases,
        Map<String, RevCommit> releaseCommits
    ) {}

    public static void main(final String[] args) {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        
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

    private static void processAllProjects(ConfigurationManager config, Path datasetsPath, Path gitProjectsPath) {
        log.debug("Starting to process all configured projects...");
        for (final Project project : PROJECTS_TO_ANALYZE) {
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

    private static void runPipelineFor(ConfigurationManager config, final Project project, final String datasetsBasePath, final String gitProjectsPath) throws IOException {
        log.info("---------------------------------------------------------");
        log.info("--- STARTING PIPELINE FOR: {} ---", project.name());
        log.info("---------------------------------------------------------");

        final String originalCsvPath = Paths.get(datasetsBasePath, project.name() + ".csv").toString();
        final String processedArffPath = Paths.get(datasetsBasePath, project.name() + "_processed.arff").toString();
        final String repoPath = Paths.get(gitProjectsPath, project.name()).toString();
        final String modelPath = Paths.get(datasetsBasePath, project.name() + "_best.model").toString();

        final GitConnector git = new GitConnector(project.name(), project.gitUrl(), repoPath);
        git.cloneOrOpenRepo(); 

        final JiraConnector jira = new JiraConnector(project.name());
        final List<ProjectRelease> releases = jira.getProjectReleases();
        final Map<String, RevCommit> releaseCommits = git.getReleaseCommits(releases);
        
        ProjectContext context = new ProjectContext(config, project.name(), datasetsBasePath, originalCsvPath, processedArffPath, modelPath, git, jira, releases, releaseCommits);
        
        if (log.isDebugEnabled()) {
            log.debug("Created project context for pipeline: {}", context);
        }

        generateDatasetIfNotExists(context);
        preprocessData(context);
        runAnalysisAndSimulation(context);
        
        log.info("--- FINISHED PIPELINE FOR: {} ---", project.name());
    }

    private static void runAnalysisAndSimulation(ProjectContext context) {
        log.debug("Entering analysis and simulation phase...");
        try {
            final ClassifierRunner runner = new ClassifierRunner(context.config(), context.processedArffPath(), context.modelPath());
            final Classifier bestModel = runner.getBestClassifier();
            
            final DataAnalyzer analyzer = new DataAnalyzer(context.config(), context.originalCsvPath(), context.processedArffPath(), context.git(), context.releaseCommits());
            final String aFeature = analyzer.findAndSaveActionableMethod();

            final WhatIfSimulator simulator = new WhatIfSimulator(context.processedArffPath(), bestModel, aFeature);
            simulator.runFullDatasetSimulation();

            final String originalMethodPath = Paths.get(context.datasetsBasePath(), context.projectName() + "_AFMethod.txt").toString();
            final Path projectParentPath = Paths.get(context.datasetsBasePath()).getParent();
            final String refactoredMethodPath = projectParentPath.resolve("AFMethod_refactored").resolve(context.projectName() + "_AFMethod_refactored.txt").toString();
            
            if (log.isDebugEnabled()){
                log.debug("Comparing original method at '{}' with refactored method at '{}'", originalMethodPath, refactoredMethodPath);
            }
            final FeatureComparer comparer = new FeatureComparer();
            comparer.compareMethods(originalMethodPath, refactoredMethodPath);

        } catch (Exception e) {
            log.error("An error occurred during analysis and simulation for project {}", context.projectName(), e);
        }
        log.debug("Analysis and simulation phase finished.");
    }

    private static void generateDatasetIfNotExists(ProjectContext context) {
        log.info("[Milestone 1, Step 1] Checking for Dataset...");
        final File datasetFile = new File(context.originalCsvPath());
        log.debug("Checking for dataset file at: {}", context.originalCsvPath());
        
        if (datasetFile.exists() && datasetFile.length() > 0) {
            log.info("Dataset already exists. Skipping generation.");
        } else {
            log.info("Dataset not found or is empty. Generating...");
            final DatasetGenerator generator = new DatasetGenerator(context.config(), context.projectName(), context.git(), context.jira(), context.releases(), context.releaseCommits());
            generator.generateCsv(context.datasetsBasePath());
        }
    }

    private static void preprocessData(ProjectContext context) throws IOException {
        log.info("[...] Preprocessing data for analysis...");
        final File arffFile = new File(context.processedArffPath());
        log.debug("Checking for preprocessed file at: {}", context.processedArffPath());
        if (arffFile.exists() && arffFile.length() > 0) {
            log.info("Processed ARFF file already exists. Skipping preprocessing.");
        } else {
            log.info("ARFF file not found. Starting preprocessing...");
            try {
                final DataPreprocessor processor = new DataPreprocessor(context.config(), context.originalCsvPath(), context.processedArffPath());
                processor.processData();
                log.info("Data preprocessing complete. Output: {}", context.processedArffPath());
            } catch (Exception e) {
                throw new IOException("Failed to preprocess data", e);
            }
        }
    }
}