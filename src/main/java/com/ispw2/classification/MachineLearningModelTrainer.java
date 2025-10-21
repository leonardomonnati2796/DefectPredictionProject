package com.ispw2.classification;

import com.ispw2.ConfigurationManager;
import com.ispw2.util.LoggingUtils;
import com.ispw2.util.LoggingPatterns;
import com.ispw2.util.FormattingUtils;
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
import java.security.SecureRandom; // Import per il generatore sicuro
import java.util.Arrays;
import java.util.List;

public class MachineLearningModelTrainer {
    private static final Logger log = LoggerFactory.getLogger(MachineLearningModelTrainer.class);
    private static final int NUM_FOLDS = 10;
    private static final int NUM_REPEATS = 10;
    
    private static final String TABLE_SEPARATOR = "----------------------------------------------------------------------";
    private static final String TABLE_HEADER_FORMAT = "%-20s | %-10s | %-10s | %-10s | %-10s";
    private static final String TABLE_ROW_FORMAT = "%-20s | %-10.3f | %-10.3f | %-10.3f | %-10.3f";

    private final ConfigurationManager config;
    private final String processedArffPath;
    private final String modelPath;
    private Instances data;

    /**
     * Constructs a new MachineLearningModelTrainer for training and evaluating classifiers.
     * 
     * @param config Configuration manager with system settings
     * @param processedArffPath Path to the processed ARFF dataset
     * @param modelPath Path where the best trained model will be saved
     */
    public MachineLearningModelTrainer(ConfigurationManager config, final String processedArffPath, final String modelPath) {
        this.config = config;
        this.processedArffPath = processedArffPath;
        this.modelPath = modelPath;
    }
    
    /**
     * Gets the best trained classifier, either by loading from file or training a new one.
     * Evaluates multiple base classifiers (RandomForest, NaiveBayes, IBk) and selects
     * the best one based on AUC score, then applies hyperparameter tuning.
     * 
     * @return The best trained classifier
     * @throws IOException If training or loading fails
     */
    public Classifier getBestClassifier() throws IOException {
        LoggingPatterns.info(log, "Checking for model file at path: {}", modelPath);
        final File modelFile = new File(modelPath);
        
        if (modelFile.exists() && modelFile.length() > 0) {
            LoggingPatterns.logMilestone(log, 2, "Found saved model. Loading from file: " + modelPath);
            try {
                return (Classifier) SerializationHelper.read(modelPath);
            } catch (Exception e) {
                throw new IOException("Failed to load the saved model.", e);
            }
        }
        
        LoggingPatterns.logMilestone(log, 2, 2, "No saved model found. Starting evaluation and tuning process...");
        loadData();
        
        if (!isDataSufficientForClassification()) {
            LoggingPatterns.warn(log, "Data is not sufficient for classification. Returning default RandomForest classifier.");
            return new RandomForest();
        }

        try {
            LoggingUtils.debugIfEnabled(log, "Starting base classifier evaluation...");
            final Classifier bestBaseClassifier = findBestBaseClassifier();
            
            if (bestBaseClassifier == null) {
                LoggingPatterns.error(log, "No base classifier could be selected. Aborting.");
                throw new IOException("Classifier selection failed.");
            }
            
            LoggingUtils.debugIfEnabled(log, "Starting hyperparameter tuning for the best classifier...");
            final Classifier tunedClassifier = tuneClassifier(bestBaseClassifier);
            
            LoggingPatterns.logFileOperation(log, "Saving tuned model to", modelPath);
            SerializationHelper.write(modelPath, tunedClassifier);

            return tunedClassifier;
        } catch (Exception e) {
            throw new IOException("An error occurred during classifier training or tuning.", e);
        }
    }

    /**
     * Loads the ARFF dataset from the specified file path.
     * 
     * @throws IOException If loading the dataset fails
     */
    private void loadData() throws IOException {
        LoggingUtils.debugIfEnabled(log, "Loading data from ARFF file: {}", processedArffPath);
        final ArffLoader loader = new ArffLoader();
        loader.setSource(new File(processedArffPath));
        this.data = loader.getDataSet();
        this.data.setClassIndex(this.data.numAttributes() - 1);
        
        LoggingUtils.debugIfEnabled(log, "Loaded {} instances from {}", this.data.numInstances(), processedArffPath);
    }

    /**
     * Checks if the loaded dataset is sufficient for classification tasks.
     * 
     * @return true if the dataset is sufficient, false otherwise
     */
    private boolean isDataSufficientForClassification() {
        LoggingUtils.debugIfEnabled(log, "Checking if data is sufficient for classification...");
        if (this.data.numInstances() < NUM_FOLDS) {
            LoggingPatterns.error(log, "The dataset has fewer than {} instances ({}), not enough for {}-fold cross-validation. Skipping evaluation.", NUM_FOLDS, this.data.numInstances(), NUM_FOLDS);
            return false;
        }
        final Attribute classAttribute = this.data.classAttribute();
        if (classAttribute.numValues() < 2) {
            LoggingPatterns.error(log, "The dataset contains only one class value ('{}'). Cannot perform classification.", classAttribute.value(0));
            return false;
        }
        LoggingUtils.debugIfEnabled(log, "Data is sufficient.");
        return true;
    }

    /**
     * Finds the best base classifier by evaluating multiple algorithms.
     * 
     * @return The best performing classifier
     * @throws Exception If evaluation fails
     */
    private Classifier findBestBaseClassifier() throws Exception {
        LoggingPatterns.info(log, "--- Evaluating base classifiers on the original (imbalanced) dataset ---");
        final List<Classifier> classifiers = Arrays.asList(new RandomForest(), new NaiveBayes(), new IBk());
        Classifier bestClassifier = null;
        double bestAuc = 0.0;
        
        LoggingPatterns.info(log, TABLE_SEPARATOR);
        LoggingPatterns.info(log, String.format(TABLE_HEADER_FORMAT, "Classifier", "AUC", "Precision", "Recall", "Kappa"));
        LoggingPatterns.info(log, TABLE_SEPARATOR);

        for (final Classifier classifier : classifiers) {
            LoggingUtils.debugIfEnabled(log, "Evaluating classifier: {}", classifier.getClass().getSimpleName());

            final Evaluation eval = evaluateModel(classifier);
            final double auc = eval.weightedAreaUnderROC();
            final double precision = eval.weightedPrecision();
            final double recall = eval.weightedRecall();
            final double kappa = eval.kappa();
            
            LoggingPatterns.info(log, String.format(TABLE_ROW_FORMAT, classifier.getClass().getSimpleName(), auc, precision, recall, kappa));

            if (auc > bestAuc) {
                LoggingUtils.debugIfEnabled(log, "New best classifier found: {} with AUC = {}", classifier.getClass().getSimpleName(), FormattingUtils.formatDecimal(auc, 3));
                bestAuc = auc;
                bestClassifier = classifier;
            }
        }
        
        LoggingPatterns.info(log, TABLE_SEPARATOR);
        if (bestClassifier != null) {
            LoggingPatterns.info(log, "Best base classifier selected: {}", bestClassifier.getClass().getSimpleName());
        }
        return bestClassifier;
    }

    /**
     * Evaluates a classifier using cross-validation with multiple repeats.
     * 
     * @param classifier The classifier to evaluate
     * @return The evaluation results
     * @throws Exception If evaluation fails
     */
    private Evaluation evaluateModel(final Classifier classifier) throws Exception {
        LoggingUtils.debugIfEnabled(log, "Starting evaluation for {} with {} repeats of {}-fold cross-validation.", classifier.getClass().getSimpleName(), NUM_REPEATS, NUM_FOLDS);
        final Evaluation eval = new Evaluation(this.data);
        for (int i = 0; i < NUM_REPEATS; i++) {
            LoggingUtils.debugIfEnabled(log, "Running cross-validation repeat {}/{} with random seed {}.", i + 1, NUM_REPEATS, i);
            eval.crossValidateModel(classifier, this.data, NUM_FOLDS, new SecureRandom());
        }
        return eval;
    }
    
    /**
     * Tunes hyperparameters for the given base classifier using cross-validation.
     * 
     * @param baseClassifier The classifier to tune
     * @return The tuned classifier
     * @throws Exception If tuning fails
     */
    private Classifier tuneClassifier(final Classifier baseClassifier) throws Exception {
        LoggingPatterns.info(log, "--- Tuning hyperparameters for {} ---", baseClassifier.getClass().getSimpleName());
        
        if (!(baseClassifier instanceof RandomForest) && !(baseClassifier instanceof IBk)) {
            LoggingPatterns.info(log, "No parameters to tune for {}.", baseClassifier.getClass().getSimpleName());
            baseClassifier.buildClassifier(data);
            return baseClassifier;
        }

        final CVParameterSelection tuner = new CVParameterSelection();
        tuner.setClassifier(baseClassifier);
        tuner.setNumFolds(NUM_FOLDS);

        if (baseClassifier instanceof RandomForest) {
            final String[] params = config.getRandomForestTuningParams();
            LoggingUtils.debugIfEnabled(log, "Tuning RandomForest with CVParameter: {}", (Object) params);
            tuner.addCVParameter(String.join(" ", params));
        } else if (baseClassifier instanceof IBk) {
            final String[] params = config.getIbkTuningParams();
            LoggingUtils.debugIfEnabled(log, "Tuning IBk with CVParameter: {}", (Object) params);
            tuner.addCVParameter(String.join(" ", params));
        }
        
        LoggingUtils.debugIfEnabled(log, "Building classifier with tuner...");
        tuner.buildClassifier(data);
        LoggingPatterns.info(log, "Tuning complete. Best parameters: {}", String.join(" ", tuner.getBestClassifierOptions()));
        return tuner;
    }
}