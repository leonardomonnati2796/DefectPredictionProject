package com.ispw2;

import com.ispw2.analysis.DataAnalyzer;
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
            
            log.info("Output for datasets: {}", datasetsPath);
            log.info("Output for git clones: {}", gitProjectsPath);

            for (final Project project : PROJECTS_TO_ANALYZE) {
                runPipelineFor(project, datasetsPath.toString(), gitProjectsPath.toString());
            }

        } catch (final IOException e) {
            log.error("A fatal I/O error occurred while setting up directories.", e);
        } catch (final Exception e) {
            log.error("A fatal unexpected error occurred during the pipeline execution.", e);
        }

        log.info("All projects processed and evaluated successfully.");
    }

    private static void runPipelineFor(final Project project, final String datasetsBasePath, final String gitProjectsBasePath) throws Exception {
        log.info("---------------------------------------------------------");
        log.info("--- STARTING PIPELINE FOR: {} ---", project.name());
        log.info("---------------------------------------------------------");

        final String originalCsvPath = Paths.get(datasetsBasePath, project.name() + ".csv").toString();
        final String processedArffPath = Paths.get(datasetsBasePath, project.name() + "_processed.arff").toString();
        final String repoPath = Paths.get(gitProjectsBasePath, project.name()).toString();
        final String modelPath = Paths.get(datasetsBasePath, project.name() + "_best.model").toString();

        final GitConnector git = new GitConnector(project.name(), project.gitUrl(), repoPath);
        git.cloneOrOpenRepo(); 

        final JiraConnector jira = new JiraConnector(project.name());
        final List<ProjectRelease> releases = jira.getProjectReleases();
        final Map<String, RevCommit> releaseCommits = git.getReleaseCommits(releases);

        generateDatasetIfNotExists(project, datasetsBasePath, git, jira, releases, releaseCommits);
        preprocessData(originalCsvPath, processedArffPath);
        
        final ClassifierRunner runner = new ClassifierRunner(processedArffPath, modelPath);
        final Classifier bestModel = runner.getBestClassifier();
        
        final DataAnalyzer analyzer = new DataAnalyzer(originalCsvPath, processedArffPath, git, releaseCommits);
        final String aFeature = analyzer.findAndSaveActionableMethod();
        
        final WhatIfSimulator simulator = new WhatIfSimulator(processedArffPath, bestModel, aFeature);
        simulator.runFullDatasetSimulation();
        
        log.info("--- FINISHED PIPELINE FOR: {} ---", project.name());
    }

    private static void generateDatasetIfNotExists(final Project project, final String datasetsBasePath, final GitConnector git, final JiraConnector jira, final List<ProjectRelease> releases, final Map<String, RevCommit> releaseCommits) throws Exception {
        log.info("[Milestone 1, Step 1] Checking for Dataset A...");
        final String originalCsvPath = Paths.get(datasetsBasePath, project.name() + ".csv").toString();
        final File datasetFile = new File(originalCsvPath);
        
        if (datasetFile.exists()) {
            log.info("Dataset already exists. Skipping generation.");
        } else {
            log.info("Dataset not found. Generating...");
            final DatasetGenerator generator = new DatasetGenerator(project.name(), git, jira, releases, releaseCommits);
            generator.generateCsv(datasetsBasePath);
            log.info("Dataset generation complete.");
        }
    }

    private static void preprocessData(final String originalCsvPath, final String processedArffPath) throws Exception {
        log.info("[...] Preprocessing data for analysis...");
        final File arffFile = new File(processedArffPath);
        if (arffFile.exists()) {
            log.info("Processed ARFF file already exists. Skipping preprocessing.");
        } else {
            final DataPreprocessor processor = new DataPreprocessor(originalCsvPath, processedArffPath);
            processor.processData();
            log.info("Data preprocessing complete. Output: {}", processedArffPath);
        }
    }
}