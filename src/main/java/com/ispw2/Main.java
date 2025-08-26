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
import weka.classifiers.Classifier;

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

    public static void main(final String[] args) {
        log.info("Starting Defect Prediction Analysis...");
        
        final ConfigurationManager config = new ConfigurationManager();

        try {
            final String executionDir = System.getProperty("user.dir");
            final Path projectRoot = Paths.get(executionDir).getParent();
            
            if (projectRoot == null) {
                log.error("Cannot determine parent directory. Please run from within the project folder.");
                return;
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
        for (final Project project : PROJECTS_TO_ANALYZE) {
            try {
                runPipelineFor(config, project, datasetsPath.toString(), gitProjectsPath.toString());
            } catch (Exception e) {
                log.error("A fatal error occurred during the pipeline for project {}. Moving to the next one.", project.name(), e);
            }
        }
    }

    private static void runPipelineFor(ConfigurationManager config, final Project project, final String datasetsBasePath, final String gitProjectsPath) throws IOException {
        log.info("---------------------------------------------------------");
        log.info("--- STARTING PIPELINE FOR: {} ---", project.name());
        log.info("---------------------------------------------------------");

        final String originalCsvPath = Paths.get(datasetsBasePath, project.name() + ".csv").toString();
        final String processedArffPath = Paths.get(datasetsBasePath, project.name() + "_processed.arff").toString();
        final String repoPath = Paths.get(gitProjectsPath, project.name()).toString();
        // Aggiunto nuovamente il modelPath, ora necessario.
        final String modelPath = Paths.get(datasetsBasePath, project.name() + "_best.model").toString();

        final GitConnector git = new GitConnector(project.name(), project.gitUrl(), repoPath);
        git.cloneOrOpenRepo(); 

        final JiraConnector jira = new JiraConnector(project.name());
        final List<ProjectRelease> releases = jira.getProjectReleases();
        final Map<String, RevCommit> releaseCommits = git.getReleaseCommits(releases);

        generateDatasetIfNotExists(config, project.name(), datasetsBasePath, git, jira, releases, releaseCommits);
        preprocessData(config, originalCsvPath, processedArffPath);
        
        // La chiamata ora include anche modelPath
        runAnalysisAndSimulation(config, project.name(), originalCsvPath, processedArffPath, modelPath, datasetsBasePath, git, releaseCommits);
        
        log.info("--- FINISHED PIPELINE FOR: {} ---", project.name());
    }

    // --- MODIFICA QUI ---
    // Il metodo ora orchestra l'intero flusso di analisi, classificazione e simulazione.
    private static void runAnalysisAndSimulation(ConfigurationManager config, String projectName, String originalCsvPath, String processedArffPath, String modelPath, String datasetsBasePath, GitConnector git, Map<String, RevCommit> releaseCommits) {
        try {
            // Fase di Classificazione
            final ClassifierRunner runner = new ClassifierRunner(config, processedArffPath, modelPath);
            final Classifier bestModel = runner.getBestClassifier();
            
            // Fase di Analisi per trovare il metodo critico
            final DataAnalyzer analyzer = new DataAnalyzer(config, originalCsvPath, processedArffPath, git, releaseCommits);
            final String aFeature = analyzer.findAndSaveActionableMethod();

            // Fase di Simulazione "What-If"
            final WhatIfSimulator simulator = new WhatIfSimulator(processedArffPath, bestModel, aFeature);
            simulator.runFullDatasetSimulation();

            // Fase di Comparazione pre e post refactoring
            final String originalMethodPath = Paths.get(datasetsBasePath, projectName + "_AFMethod.txt").toString();
            final String refactoredMethodPath = Paths.get(datasetsBasePath, "AFMethod_refactored", projectName + "_AFMethod_refactored.txt").toString();
            
            final FeatureComparer comparer = new FeatureComparer();
            comparer.compareMethods(originalMethodPath, refactoredMethodPath);

        } catch (Exception e) {
            log.error("An error occurred during analysis and simulation for project {}", projectName, e);
        }
    }

    private static void generateDatasetIfNotExists(ConfigurationManager config, final String projectName, final String datasetsBasePath, final GitConnector git, final JiraConnector jira, final List<ProjectRelease> releases, final Map<String, RevCommit> releaseCommits) {
        log.info("[Milestone 1, Step 1] Checking for Dataset...");
        final String originalCsvPath = Paths.get(datasetsBasePath, projectName + ".csv").toString();
        final File datasetFile = new File(originalCsvPath);
        
        if (datasetFile.exists() && datasetFile.length() > 0) {
            log.info("Dataset already exists. Skipping generation.");
        } else {
            log.info("Dataset not found or is empty. Generating...");
            final DatasetGenerator generator = new DatasetGenerator(config, projectName, git, jira, releases, releaseCommits);
            generator.generateCsv(datasetsBasePath);
            log.info("Dataset generation complete.");
        }
    }

    private static void preprocessData(ConfigurationManager config, final String originalCsvPath, final String processedArffPath) throws IOException {
        log.info("[...] Preprocessing data for analysis...");
        final File arffFile = new File(processedArffPath);
        if (arffFile.exists() && arffFile.length() > 0) {
            log.info("Processed ARFF file already exists. Skipping preprocessing.");
        } else {
            try {
                final DataPreprocessor processor = new DataPreprocessor(config, originalCsvPath, processedArffPath);
                processor.processData();
                log.info("Data preprocessing complete. Output: {}", processedArffPath);
            } catch (Exception e) {
                throw new IOException("Failed to preprocess data", e);
            }
        }
    }
}