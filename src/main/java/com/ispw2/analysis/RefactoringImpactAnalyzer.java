package com.ispw2.analysis;

import com.ispw2.preprocessing.DatasetUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.Instances;
import java.io.IOException;

public class RefactoringImpactAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(RefactoringImpactAnalyzer.class);
    
    private final String processedArffPath;
    private Instances datasetA;
    private final Classifier bClassifier;
    private final String aFeatureName;

    /**
     * Constructs a new RefactoringImpactAnalyzer for simulating the impact of refactoring.
     * 
     * @param processedArffPath Path to the processed ARFF dataset
     * @param bClassifier The best trained classifier
     * @param aFeatureName Name of the actionable feature to simulate
     */
    public RefactoringImpactAnalyzer(final String processedArffPath, final Classifier bClassifier, final String aFeatureName) {
        this.processedArffPath = processedArffPath;
        this.bClassifier = bClassifier;
        this.aFeatureName = aFeatureName;
    }

    /**
     * Runs the complete what-if simulation to analyze the impact of refactoring.
     * Creates synthetic datasets, trains a new classifier, and calculates impact metrics.
     * 
     * @throws IOException If simulation fails
     */
    public void runFullDatasetSimulation() throws IOException {
        this.datasetA = DatasetUtilities.loadArff(processedArffPath);
        this.datasetA.setClassIndex(this.datasetA.numAttributes() - 1);
        log.info("[Milestone 2, Step 10] Starting What-If Simulation using AFeature: {}", this.aFeatureName);
        
        final Attribute aFeatureAttribute = datasetA.attribute(this.aFeatureName);
        if (aFeatureAttribute == null) {
            log.error("Could not find AFeature '{}' in the dataset. Aborting simulation.", this.aFeatureName);
            return;
        }

        log.info("[Milestone 2, Step 11] Training BClassifier on dataset A (BClassifierA)...");
        try {
            // Create a new instance of the same classifier type and train it on dataset A
            final Classifier bClassifierA = createAndTrainClassifierOnA();
            log.info("BClassifierA training completed successfully.");
            
            log.debug("Splitting dataset into B+ (at-risk) and C (safe) subsets.");
            final Instances datasetBplus = DatasetUtilities.filterInstances(datasetA, this.aFeatureName, ">", 0);
            final Instances datasetC = DatasetUtilities.filterInstances(datasetA, this.aFeatureName, "<=", 0);
            if (log.isDebugEnabled()) {
                log.debug("Dataset B+ ({} > 0) contains {} instances.", aFeatureName, datasetBplus.numInstances());
                log.debug("Dataset C ({} <= 0) contains {} instances.", aFeatureName, datasetC.numInstances());
            }

            log.debug("Creating synthetic dataset B by simulating refactoring on B+ (setting {} = 0).", aFeatureName);
            final Instances datasetB = createSyntheticDatasetB(datasetBplus, this.aFeatureName);

        logSimulationSummaryTable(datasetA, datasetBplus, datasetB, datasetC, bClassifierA);
        
        analyzePreliminaryQuestions(datasetBplus, datasetB, bClassifierA);
        
        analyzeResults(datasetBplus, datasetB, bClassifierA);
        } catch (Exception e) {
            log.error("Error during BClassifierA training or simulation for feature '{}': {}", 
                    this.aFeatureName, e.getMessage(), e);
            throw new IOException("Failed to complete what-if simulation for feature '" + 
                    this.aFeatureName + "' due to: " + e.getMessage(), e);
        }
    }
    
    private Classifier createAndTrainClassifierOnA() throws Exception {
        log.debug("Creating new instance of classifier type: {}", bClassifier.getClass().getSimpleName());
        
        // Create a new instance of the same classifier type
        final Classifier bClassifierA = bClassifier.getClass().getDeclaredConstructor().newInstance();
        
        // Train it on dataset A
        log.debug("Training BClassifierA on dataset A with {} instances...", datasetA.numInstances());
        bClassifierA.buildClassifier(datasetA);
        
        return bClassifierA;
    }
    
    private Instances createSyntheticDatasetB(final Instances datasetBplus, final String featureNameToModify) {
        final Instances datasetB = new Instances(datasetBplus);
        final Attribute aFeature = datasetB.attribute(featureNameToModify);
        final double nonSmellyValue = 0.0; 
        for (int i = 0; i < datasetB.numInstances(); i++) {
            datasetB.instance(i).setValue(aFeature, nonSmellyValue);
        }
        return datasetB;
    }
    
    private void logSimulationSummaryTable(final Instances dataA, final Instances bPlus, final Instances b, final Instances c, final Classifier bClassifierA) {
        log.info("[Milestone 2, Step 12] Defect Prediction Summary Table:");

        if (log.isInfoEnabled()) {
            int defectsInA = DatasetUtilities.countDefective(bClassifierA, dataA);
            int defectsInBplus = DatasetUtilities.countDefective(bClassifierA, bPlus);
            int defectsInB = DatasetUtilities.countDefective(bClassifierA, b);
            int defectsInC = DatasetUtilities.countDefective(bClassifierA, c);

            String separator = "------------------------------------------------------------------";
            String headerFormat = "| %-20s | %-15s | %-15s |";
            String rowFormat = "| %-20s | %-15d | %-15d |";

            log.info(separator);
            log.info(String.format(headerFormat, "Dataset", "Total Instances", "Predicted Defects"));
            log.info(separator);
            log.info(String.format(rowFormat, "A (Full Dataset)", dataA.numInstances(), defectsInA));
            log.info(String.format(rowFormat, "B+ (" + aFeatureName + " > 0)", bPlus.numInstances(), defectsInBplus));
            log.info(String.format(rowFormat, "B (B+ with " + aFeatureName + "=0)", b.numInstances(), defectsInB));
            log.info(String.format(rowFormat, "C (" + aFeatureName + " <= 0)", c.numInstances(), defectsInC));
            log.info(separator);
        }
    }

    private void analyzePreliminaryQuestions(final Instances bPlus, final Instances b, final Classifier bClassifierA) {
        log.info("[PRELIMINARY QUESTIONS] Analyzing feature changes in AFMethod2 vs AFMethod...");
        
        // Calculate predicted defects for B+ (original) and B (refactored)
        final int predictedDefectsInBplus = DatasetUtilities.countDefective(bClassifierA, bPlus);
        final int predictedDefectsInB = DatasetUtilities.countDefective(bClassifierA, b);
        
        log.info("--- PRELIMINARY ANALYSIS ---");
        log.info("Predicted defects in B+ (original with {} > 0): {}", aFeatureName, predictedDefectsInBplus);
        log.info("Predicted defects in B (refactored with {} = 0): {}", aFeatureName, predictedDefectsInB);
        
        // Question 1: Did any feature positively correlated with bugginess increase in AFMethod2?
        if (predictedDefectsInB > predictedDefectsInBplus) {
            log.warn("QUESTION 1: YES - Predicted defects INCREASED in AFMethod2 ({} vs {}).", 
                predictedDefectsInB, predictedDefectsInBplus);
            log.warn("This suggests we may NOT have improved maintainability in AFMethod2 compared to AFMethod.");
        } else {
            log.info("QUESTION 1: NO - Predicted defects did not increase in AFMethod2 ({} vs {}).", 
                predictedDefectsInB, predictedDefectsInBplus);
        }
        
        // Question 2: Did any feature negatively correlated with bugginess increase in AFMethod2?
        if (predictedDefectsInB < predictedDefectsInBplus) {
            log.info("QUESTION 2: YES - Predicted defects DECREASED in AFMethod2 ({} vs {}).", 
                predictedDefectsInB, predictedDefectsInBplus);
            log.info("This suggests we MAY have improved maintainability by reducing {} from >0 to 0.", aFeatureName);
        } else if (predictedDefectsInB == predictedDefectsInBplus) {
            log.info("QUESTION 2: NO CHANGE - Predicted defects remained the same in AFMethod2 ({}).", 
                predictedDefectsInB);
            log.info("This suggests the refactoring had no impact on predicted defect probability.");
        } else {
            log.warn("QUESTION 2: NO - Predicted defects increased in AFMethod2, suggesting maintainability may have worsened.");
        }
        
        log.info("--- END PRELIMINARY ANALYSIS ---");
    }

    private void analyzeResults(final Instances bPlus, final Instances b, final Classifier bClassifierA) {
        log.info("[Milestone 2, Step 13] Calculating final metrics based on custom formulas...");

        final int actualDefectsInA = DatasetUtilities.countActualDefective(this.datasetA);
        final int actualDefectsInBplus = DatasetUtilities.countActualDefective(bPlus);
        final int predictedDefectsInB = DatasetUtilities.countDefective(bClassifierA, b);

        if(log.isDebugEnabled()){
            log.debug("--- Formula Components ---");
            log.debug("Actual Defects in B+ (actual B+) = {}", actualDefectsInBplus);
            log.debug("Predicted Defects in B (expected B) = {}", predictedDefectsInB);
            log.debug("Actual Defects in A (actual A) = {}", actualDefectsInA);
            log.debug("--------------------------");
        }

        double numerator = (double) actualDefectsInBplus - predictedDefectsInB;

        if (actualDefectsInBplus > 0) {
            double drop = numerator / actualDefectsInBplus;
            if (log.isInfoEnabled()) {
                log.info("Formula 1 (drop) = (actual B+ - expected B) / actual B+ = ({} - {}) / {} = {}",
                    actualDefectsInBplus, predictedDefectsInB, actualDefectsInBplus, String.format("%.3f", drop));
                log.info("ANSWER 1 (drop): The calculated metric value is {}.", String.format("%.3f", drop));
            }
        } else {
            log.warn("Cannot calculate 'drop' metric because there are no actual defects in the B+ dataset (division by zero).");
        }

        if (actualDefectsInA > 0) {
            double reduction = numerator / actualDefectsInA;
            if (log.isInfoEnabled()) {
                log.info("Formula 2 (reduction) = (actual B+ - expected B) / actual A = ({} - {}) / {} = {}",
                    actualDefectsInBplus, predictedDefectsInB, actualDefectsInA, String.format("%.3f", reduction));
                log.info("ANSWER 2 (reduction): The calculated metric value is {}.", String.format("%.3f", reduction));
            }
        } else {
            log.warn("Cannot calculate 'reduction' metric because there are no actual defects in the full dataset (division by zero).");
        }
    }
}