package com.ispw2.analysis;

import com.ispw2.preprocessing.DataHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.Instances;
import java.io.IOException;

public class WhatIfSimulator {
    private static final Logger log = LoggerFactory.getLogger(WhatIfSimulator.class);
    
    private final String processedArffPath;
    private Instances datasetA;
    private final Classifier bClassifier;
    private final String aFeatureName;

    public WhatIfSimulator(final String processedArffPath, final Classifier bClassifier, final String aFeatureName) {
        this.processedArffPath = processedArffPath;
        this.bClassifier = bClassifier;
        this.aFeatureName = aFeatureName;
    }

    public void runFullDatasetSimulation() throws IOException {
        this.datasetA = DataHelper.loadArff(processedArffPath);
        this.datasetA.setClassIndex(this.datasetA.numAttributes() - 1);
        log.info("[Milestone 2, Step 10] Starting What-If Simulation using AFeature: {}", this.aFeatureName);
        
        final Attribute aFeatureAttribute = datasetA.attribute(this.aFeatureName);
        if (aFeatureAttribute == null) {
            log.error("Could not find AFeature '{}' in the dataset. Aborting simulation.", this.aFeatureName);
            return;
        }

        log.debug("Splitting dataset into B+ (at-risk) and C (safe) subsets.");
        final Instances datasetBplus = DataHelper.filterInstances(datasetA, this.aFeatureName, ">", 0);
        final Instances datasetC = DataHelper.filterInstances(datasetA, this.aFeatureName, "<=", 0);
        if (log.isDebugEnabled()) {
            log.debug("Dataset B+ ({} > 0) contains {} instances.", aFeatureName, datasetBplus.numInstances());
            log.debug("Dataset C ({} <= 0) contains {} instances.", aFeatureName, datasetC.numInstances());
        }

        log.debug("Creating synthetic dataset B by simulating refactoring on B+ (setting {} = 0).", aFeatureName);
        final Instances datasetB = createSyntheticDatasetB(datasetBplus, this.aFeatureName);

        // --- MODIFICA QUI: La tabella riassuntiva è stata reinserita ---
        logSimulationSummaryTable(datasetA, datasetBplus, datasetB, datasetC);
        
        analyzeResults(datasetBplus, datasetB);
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
    
    // --- NUOVO METODO PER STAMPARE LA TABELLA RIASSUNTIVA ---
    private void logSimulationSummaryTable(final Instances dataA, final Instances bPlus, final Instances b, final Instances c) {
        log.info("[Milestone 2, Step 12] Defect Prediction Summary Table:");

        if (log.isInfoEnabled()) {
            // Calcola tutti i difetti predetti necessari per la tabella
            int defectsInA = DataHelper.countDefective(bClassifier, dataA);
            int defectsInBplus = DataHelper.countDefective(bClassifier, bPlus);
            int defectsInB = DataHelper.countDefective(bClassifier, b);
            int defectsInC = DataHelper.countDefective(bClassifier, c);

            // Definisce la formattazione della tabella
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

    private void analyzeResults(final Instances bPlus, final Instances b) {
        log.info("[Milestone 2, Step 13] Calculating final metrics based on custom formulas...");

        // Calcolo dei componenti per le formule
        final int actualDefectsInA = DataHelper.countActualDefective(this.datasetA);
        final int actualDefectsInBplus = DataHelper.countActualDefective(bPlus);
        final int predictedDefectsInB = DataHelper.countDefective(bClassifier, b);

        if(log.isDebugEnabled()){
            log.debug("--- Formula Components ---");
            log.debug("Actual Defects in B+ (actual B+) = {}", actualDefectsInBplus);
            log.debug("Predicted Defects in B (expected B) = {}", predictedDefectsInB);
            log.debug("Actual Defects in A (actual A) = {}", actualDefectsInA);
            log.debug("--------------------------");
        }

        double numerator = (double) actualDefectsInBplus - predictedDefectsInB;

        // Formula 1: DROP
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

        // Formula 2: REDUCTION
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