package com.ispw2.classification;

import com.ispw2.ConfigurationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.CVParameterSelection;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.SerializationHelper;
import weka.core.converters.ArffLoader;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class ClassifierRunner {
    private static final Logger log = LoggerFactory.getLogger(ClassifierRunner.class);
    private static final int NUM_FOLDS = 10;
    private static final int NUM_REPEATS = 10;

    // --- MODIFICA QUI ---
    // Costante definita per il separatore della tabella.
    private static final String TABLE_SEPARATOR = "----------------------------------------------------------------------";

    private final String processedArffPath;
    private final String modelPath;
    private Instances data;

    public ClassifierRunner(final String processedArffPath, final String modelPath) {
        this.processedArffPath = processedArffPath;
        this.modelPath = modelPath;
    }
    
    public Classifier getBestClassifier() throws IOException {
        final File modelFile = new File(modelPath);
        if (modelFile.exists() && modelFile.length() > 0) {
            log.info("\n[Milestone 2, Step 2-3] Found saved model. Loading from file...");
            try {
                return (Classifier) SerializationHelper.read(modelPath);
            } catch (Exception e) {
                throw new IOException("Failed to load the saved model.", e);
            }
        }
        
        log.info("\n[Milestone 2, Step 2] No saved model found. Starting evaluation and tuning process...");
        loadData();
        
        if (!isDataSufficientForClassification()) {
            return new RandomForest();
        }

        try {
            final Classifier bestBaseClassifier = findBestBaseClassifier();
            final Classifier tunedClassifier = tuneClassifier(bestBaseClassifier);
            
            log.info("\nSaving tuned model to: {}", modelPath);
            SerializationHelper.write(modelPath, tunedClassifier);

            return tunedClassifier;
        } catch (Exception e) {
            throw new IOException("An error occurred during classifier training or tuning.", e);
        }
    }

    private void loadData() throws IOException {
        final ArffLoader loader = new ArffLoader();
        loader.setSource(new File(processedArffPath));
        this.data = loader.getDataSet();
        this.data.setClassIndex(this.data.numAttributes() - 1);
        log.info("Loaded {} instances from {}", this.data.numInstances(), processedArffPath);
    }

    private boolean isDataSufficientForClassification() {
        if (this.data.numInstances() < NUM_FOLDS) {
            log.error("The dataset has fewer than {} instances ({}), not enough for {}-fold cross-validation. Skipping evaluation.", NUM_FOLDS, this.data.numInstances(), NUM_FOLDS);
            return false;
        }
        final Attribute classAttribute = this.data.classAttribute();
        if (classAttribute.numValues() < 2) {
            log.error("The dataset contains only one class value ('{}'). Cannot perform classification.", classAttribute.value(0));
            return false;
        }
        return true;
    }

    private Classifier findBestBaseClassifier() throws Exception {
        log.info("--- Evaluating base classifiers on the original (imbalanced) dataset ---");
        final List<Classifier> classifiers = Arrays.asList(new RandomForest(), new NaiveBayes(), new IBk());
        Classifier bestClassifier = null;
        double bestAuc = 0.0;

        log.info(TABLE_SEPARATOR);
        log.info(String.format("%-20s | %-10s | %-10s | %-10s | %-10s", "Classifier", "AUC", "Precision", "Recall", "Kappa"));
        log.info(TABLE_SEPARATOR);

        for (final Classifier classifier : classifiers) {
            final Evaluation eval = evaluateModel(classifier);
            final double auc = eval.weightedAreaUnderROC();
            final double precision = eval.weightedPrecision();
            final double recall = eval.weightedRecall();
            final double kappa = eval.kappa();
            
            log.info(String.format("%-20s | %-10.3f | %-10.3f | %-10.3f | %-10.3f", classifier.getClass().getSimpleName(), auc, precision, recall, kappa));

            if (auc > bestAuc) {
                bestAuc = auc;
                bestClassifier = classifier;
            }
        }
        log.info(TABLE_SEPARATOR);
        log.info("Best base classifier selected: {}", bestClassifier.getClass().getSimpleName());
        return bestClassifier;
    }

    private Evaluation evaluateModel(final Classifier classifier) throws Exception {
        final Evaluation eval = new Evaluation(this.data);
        for (int i = 0; i < NUM_REPEATS; i++) {
            eval.crossValidateModel(classifier, this.data, NUM_FOLDS, new Random(i));
        }
        return eval;
    }
    
    private Classifier tuneClassifier(final Classifier baseClassifier) throws Exception {
        log.info("--- Tuning hyperparameters for {} ---", baseClassifier.getClass().getSimpleName());
        
        if (!(baseClassifier instanceof RandomForest) && !(baseClassifier instanceof IBk)) {
            log.info("No parameters to tune for {}.", baseClassifier.getClass().getSimpleName());
            baseClassifier.buildClassifier(data);
            return baseClassifier;
        }

        final CVParameterSelection tuner = new CVParameterSelection();
        tuner.setClassifier(baseClassifier);
        tuner.setNumFolds(NUM_FOLDS);

        if (baseClassifier instanceof RandomForest) {
            final String[] params = ConfigurationManager.getInstance().getRandomForestTuningParams();
            tuner.addCVParameter(String.join(" ", params));
        } else if (baseClassifier instanceof IBk) {
            final String[] params = ConfigurationManager.getInstance().getIbkTuningParams();
            tuner.addCVParameter(String.join(" ", params));
        }
        
        tuner.buildClassifier(data);
        log.info("Tuning complete. Best parameters: {}", String.join(" ", tuner.getBestClassifierOptions()));
        return tuner;
    }
}