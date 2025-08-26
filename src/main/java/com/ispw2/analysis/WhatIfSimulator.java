package com.ispw2.analysis;

import com.ispw2.preprocessing.DataHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.Instances;

public class WhatIfSimulator {
    private static final Logger log = LoggerFactory.getLogger(WhatIfSimulator.class);
    
    // --- MODIFICHE QUI ---
    // Costanti definite per la formattazione della tabella
    private static final String TABLE_SEPARATOR = "------------------------------------------------------------------";
    private static final String TABLE_HEADER_FORMAT = "| %-20s | %-15s | %-15s |";
    private static final String TABLE_ROW_FORMAT = "| %-20s | %-15d | %-15d |";

    private final String processedArffPath;
    private Instances datasetA;
    private final Classifier bClassifier;
    private final String aFeatureName;

    public WhatIfSimulator(final String processedArffPath, final Classifier bClassifier, final String aFeatureName) {
        this.processedArffPath = processedArffPath;
        this.bClassifier = bClassifier;
        this.aFeatureName = aFeatureName;
    }

    public void runFullDatasetSimulation() throws Exception {
        this.datasetA = DataHelper.loadArff(processedArffPath);
        this.datasetA.setClassIndex(this.datasetA.numAttributes() - 1);

        log.info("[Milestone 2, Step 10] Creating Defect Prediction Datasets using AFeature: {}", this.aFeatureName);
        
        final Attribute aFeatureAttribute = datasetA.attribute(this.aFeatureName);
        if (aFeatureAttribute == null) {
            log.error("Could not find AFeature '{}' in the dataset. Aborting simulation.", this.aFeatureName);
            return;
        }

        final Instances datasetBplus = DataHelper.filterInstances(datasetA, this.aFeatureName, ">", 0);
        final Instances datasetC = DataHelper.filterInstances(datasetA, this.aFeatureName, "<=", 0);
        final Instances datasetB = createSyntheticDatasetB(datasetBplus, this.aFeatureName);

        log.info("[Milestone 2, Step 11] Using pre-trained and tuned BClassifier.");

        printResultsTable(datasetBplus, datasetB, datasetC);
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

    private void printResultsTable(final Instances bPlus, final Instances b, final Instances c) throws Exception {
        log.info("[Milestone 2, Step 12] Predicting Defectiveness...");
        final int defectsInA = DataHelper.countDefective(this.bClassifier, this.datasetA);
        final int defectsInBplus = DataHelper.countDefective(this.bClassifier, bPlus);
        final int defectsInB = DataHelper.countDefective(this.bClassifier, b);
        final int defectsInC = DataHelper.countDefective(this.bClassifier, c);

        log.info(TABLE_SEPARATOR);
        log.info("                   DEFECT PREDICTION ANALYSIS RESULTS                   ");
        log.info(TABLE_SEPARATOR);
        log.info(String.format(TABLE_HEADER_FORMAT, "Dataset", "Total Instances", "Predicted Defects"));
        log.info(TABLE_SEPARATOR);
        log.info(String.format(TABLE_ROW_FORMAT, "A (Full Dataset)", this.datasetA.numInstances(), defectsInA));
        log.info(String.format(TABLE_ROW_FORMAT, "B+ (" + aFeatureName + " > 0)", bPlus.numInstances(), defectsInBplus));
        log.info(String.format(TABLE_ROW_FORMAT, "B (B+ with " + aFeatureName + "=0)", b.numInstances(), defectsInB));
        log.info(String.format(TABLE_ROW_FORMAT, "C (" + aFeatureName + " <= 0)", c.numInstances(), defectsInC));
        log.info(TABLE_SEPARATOR);
    }

    private void analyzeResults(final Instances bPlus, final Instances b) throws Exception {
        log.info("[Milestone 2, Step 13] Final Analysis...");
        final int defectsInBplus = DataHelper.countDefective(bClassifier, bPlus);
        final int defectsInB = DataHelper.countDefective(bClassifier, b);
        
        if (defectsInBplus > 0) {
            final double preventable = defectsInBplus - defectsInB;
            final double reduction = (preventable / (double) defectsInBplus) * 100;
            log.info("Simulating code smell reduction dropped predicted defects from {} to {} (a {}% reduction).", defectsInBplus, defectsInB, String.format("%.1f", reduction));
            log.info("ANSWER: An estimated {} buggy methods could have been prevented by reducing {}.", String.format("%.0f", preventable), aFeatureName);
        } else {
            log.info("No defects were predicted in the 'at-risk' group (B+).");
        }
    }
}