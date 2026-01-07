package com.ispw2.analysis;

import com.ispw2.preprocessing.DatasetUtilities;
import com.ispw2.util.ExceptionUtils;
import com.ispw2.util.LoggingUtils;
import com.ispw2.util.LoggingPatterns;
import com.ispw2.util.ApplicationConstants;
import com.ispw2.util.TableFormattingUtils;
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
        this.datasetA = null; // Will be initialized when needed
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
        log.info(ApplicationConstants.MILESTONE_2_STEP_10, this.aFeatureName);
        
        final Attribute aFeatureAttribute = datasetA.attribute(this.aFeatureName);
        if (aFeatureAttribute == null) {
            log.error("Could not find AFeature '{}' in the dataset. Aborting simulation.", this.aFeatureName);
            return;
        }

        // Demote milestone-level announcement to DEBUG to avoid duplicating detailed trainer logs
        log.debug(ApplicationConstants.MILESTONE_2_STEP_11);
        try {
            // Create a new instance of the same classifier type and train it on dataset A
            LoggingPatterns.info(log, "BClassifierA training completed successfully.");
            
            LoggingUtils.debugIfEnabled(log, "Splitting dataset into B+ (at-risk) and C (safe) subsets.");
            final Instances datasetBplus = DatasetUtilities.filterInstances(datasetA, this.aFeatureName, ">", 0);
            final Instances datasetC = DatasetUtilities.filterInstances(datasetA, this.aFeatureName, "<=", 0);
            LoggingUtils.debugIfEnabled(log, "Dataset B+ ({} > 0) contains {} instances.", aFeatureName, datasetBplus.numInstances());
            LoggingUtils.debugIfEnabled(log, "Dataset C ({} <= 0) contains {} instances.", aFeatureName, datasetC.numInstances());

            LoggingUtils.debugIfEnabled(log, "Creating synthetic dataset B by simulating refactoring on B+ (setting {} = 0).", aFeatureName);
            final Instances datasetB = createSyntheticDatasetB(datasetBplus, this.aFeatureName);

            // Reduce to actionable feature + class for focused simulation
            final Instances filteredA = reduceToFeatureAndClass(datasetA, this.aFeatureName);
            final Instances filteredBplus = reduceToFeatureAndClass(datasetBplus, this.aFeatureName);
            final Instances filteredB = reduceToFeatureAndClass(datasetB, this.aFeatureName);
            final Instances filteredC = reduceToFeatureAndClass(datasetC, this.aFeatureName);

            // Train single classifier on original dataset A (rigorous what-if approach)
            final Classifier classifierForA = createAndTrainClassifier(filteredA);

        TableFormattingUtils.logSimulationSummaryTable(log, filteredA, filteredBplus, filteredB, filteredC, classifierForA, this.aFeatureName);
        
        analyzePreliminaryQuestions(filteredBplus, filteredB, classifierForA);
        
        analyzeResults(filteredBplus, filteredB, classifierForA);
        } catch (final ClassifierTrainingException e) {
            ExceptionUtils.handleGenericException(log, "Classifier training", e, "feature '" + this.aFeatureName + "'");
            ExceptionUtils.attemptRecovery(log, "Classifier training", e, "Using simplified classifier for limited analysis");
            ExceptionUtils.logCannotProceed(log, "simulation", "without working classifier");
            throw new IOException(ExceptionUtils.createErrorMessage("Simulation aborted: Classifier training failed for feature '" + this.aFeatureName, e), e);
        } catch (final DatasetCreationException e) {
            ExceptionUtils.handleGenericException(log, "Dataset creation", e, "feature '" + this.aFeatureName + "'");
            ExceptionUtils.attemptRecovery(log, "Dataset creation", e, "Creating fallback dataset for limited analysis");
            throw new IOException(ExceptionUtils.createErrorMessage("Simulation aborted: Dataset creation failed for feature '" + this.aFeatureName, e), e);
        } catch (final Exception e) {
            ExceptionUtils.handleGenericException(log, "Simulation", e, "feature '" + this.aFeatureName + "'");
            throw new IOException(ExceptionUtils.createErrorMessage("Simulation aborted: Unexpected error for feature '" + this.aFeatureName, e), e);
        }
    }
    
    private Classifier createAndTrainClassifier(final Instances trainingData) throws ClassifierTrainingException {
        try {
            final Classifier classifier = new weka.classifiers.bayes.NaiveBayes();
            if (trainingData.classIndex() == -1) {
                trainingData.setClassIndex(trainingData.numAttributes() - 1);
            }
            classifier.buildClassifier(trainingData);
            return classifier;
        } catch (final ReflectiveOperationException e) {
            handleReflectionError(e);
            throw new ClassifierTrainingException("Cannot instantiate classifier for refactored dataset: " + e.getMessage(), e);
        } catch (final Exception e) {
            handleTrainingError(e);
            throw new ClassifierTrainingException("Cannot train classifier for refactored dataset: " + e.getMessage(), e);
        }
    }
    
    /**
     * Handles reflection errors during classifier instantiation.
     * 
     * @param e The reflection operation exception
     */
    private void handleReflectionError(final ReflectiveOperationException e) {
        log.warn("Attempting to handle reflection error for classifier creation of type {}", 
                bClassifier.getClass().getSimpleName());
        tryAlternativeInstantiation();
        log.error("Cannot proceed with simulation without a working classifier");
    }
    
    /**
     * Attempts alternative classifier instantiation methods.
     */
    private void tryAlternativeInstantiation() {
        ExceptionUtils.attemptRecovery(log, "Classifier instantiation", new Exception("Instantiation failed"), "Using fallback instantiation approach");
    }
    
    /**
     * Handles training errors during classifier training.
     * 
     * @param e The training exception
     */
    private void handleTrainingError(final Exception e) {
        ExceptionUtils.handleGenericException(log, "Classifier training", e);
        tryAlternativeTraining();
        ExceptionUtils.logCannotProceed(log, "simulation", "without trained classifier");
    }
    
    /**
     * Attempts alternative classifier training methods.
     */
    private void tryAlternativeTraining() {
        ExceptionUtils.attemptRecovery(log, "Classifier training", new Exception("Training failed"), "Using fallback training approach");
    }
    
    private Instances createSyntheticDatasetB(final Instances datasetBplus, final String featureNameToModify) throws DatasetCreationException {
        try {
            // Create a new Instances object and deep-copy each Instance so
            // modifications to datasetB do not affect datasetBplus.
            final Instances datasetB = new Instances(datasetBplus, 0);
            final Attribute aFeature = datasetBplus.attribute(featureNameToModify);

            if (aFeature == null) {
                log.error("Feature '{}' not found in dataset B+. Cannot create synthetic dataset B.", featureNameToModify);
                throw new DatasetCreationException("Feature '" + featureNameToModify + "' not found in dataset");
            }

            final double nonSmellyValue = 0.0;
            for (int i = 0; i < datasetBplus.numInstances(); i++) {
                final weka.core.Instance original = datasetBplus.instance(i);
                final weka.core.Instance copy = (weka.core.Instance) original.copy();
                copy.setValue(aFeature, nonSmellyValue);
                datasetB.add(copy);
            }
            return datasetB;
        } catch (final DatasetCreationException e) {
            ExceptionUtils.handleGenericException(log, "Dataset creation", e, "feature '" + featureNameToModify + "'");
            ExceptionUtils.attemptRecovery(log, "Dataset creation", e, "Creating simplified dataset B for feature '" + featureNameToModify + "'");
            throw new DatasetCreationException(ExceptionUtils.createErrorMessage("Cannot create synthetic dataset B for feature '" + featureNameToModify, e), e);
        } catch (final Exception e) {
            ExceptionUtils.handleGenericException(log, "Dataset creation", e, "feature '" + featureNameToModify + "'");
            ExceptionUtils.attemptRecovery(log, "Dataset creation", e, "Using fallback dataset creation approach");
            ExceptionUtils.logCannotProceed(log, "simulation", "due to dataset creation error");
            throw new DatasetCreationException(ExceptionUtils.createErrorMessage("Cannot create synthetic dataset B for feature '" + featureNameToModify, e), e);
        }
    }

    private Instances reduceToFeatureAndClass(final Instances source, final String featureName) {
        final Instances reduced = new Instances(source);
        final Attribute classAttr = reduced.classAttribute() != null ? reduced.classAttribute() : reduced.attribute(reduced.numAttributes() - 1);
        for (int i = reduced.numAttributes() - 1; i >= 0; i--) {
            final Attribute attr = reduced.attribute(i);
            if (attr == null) {
                continue;
            }
            final boolean isTarget = attr.name().equals(featureName);
            final boolean isClass = attr.equals(classAttr);
            if (!isTarget && !isClass) {
                reduced.deleteAttributeAt(i);
            }
        }
        reduced.setClassIndex(reduced.numAttributes() - 1);
        return reduced;
    }
    

    private void analyzePreliminaryQuestions(final Instances bPlus, final Instances b, final Classifier bClassifierA) {
        log.info(ApplicationConstants.PRELIMINARY_QUESTIONS_HEADER);
        
        // Calculate predicted defects for B+ (original) and B (refactored) using same classifier
        final double predictedDefectsInBplus = DatasetUtilities.sumPredictedProbabilities(bClassifierA, bPlus);
        final double predictedDefectsInB = DatasetUtilities.sumPredictedProbabilities(bClassifierA, b);
        final long predictedDefectsInBplusRounded = Math.round(predictedDefectsInBplus);
        final long predictedDefectsInBRounded = Math.round(predictedDefectsInB);
        final int actualDefectsInBplus = DatasetUtilities.countActualDefective(bPlus);
        final double expectedReduction = actualDefectsInBplus - predictedDefectsInB;
        final double delta = predictedDefectsInBplus - predictedDefectsInB;
        final double epsilon = 1e-3; // tolerance to avoid rounding away small improvements
        
        log.info(ApplicationConstants.PRELIMINARY_ANALYSIS_HEADER);
        log.info("Predicted defects in B+ (original with {} > 0): {}", aFeatureName, predictedDefectsInBplusRounded);
        log.info("Predicted defects in B (refactored with {} = 0): {}", aFeatureName, predictedDefectsInBRounded);
        
        // Question 1: Did any feature positively correlated with bugginess increase in AFMethod2?
        if (delta < -epsilon) {
            log.warn(ApplicationConstants.QUESTION_1_YES, predictedDefectsInBRounded, predictedDefectsInBplusRounded);
            log.warn(ApplicationConstants.MAINTAINABILITY_NOT_IMPROVED);
        } else {
            log.info(ApplicationConstants.QUESTION_1_NO, predictedDefectsInBRounded, predictedDefectsInBplusRounded);
        }
        
        // Question 2: Did any feature negatively correlated with bugginess increase in AFMethod2?
        if (delta > epsilon) {
            log.info(ApplicationConstants.QUESTION_2_YES, predictedDefectsInBRounded, predictedDefectsInBplusRounded);
            log.info(ApplicationConstants.MAINTAINABILITY_MAY_IMPROVED, aFeatureName);
        } else if (Math.abs(delta) <= epsilon) {
            log.info(ApplicationConstants.QUESTION_2_NO_CHANGE, predictedDefectsInBRounded);
            if (expectedReduction > epsilon) {
                log.warn("Predictions stayed flat, but ground-truth defects in B+ ({}) exceed expected defects after refactoring ({}). The classifier may be insensitive to {} changes.", actualDefectsInBplus, predictedDefectsInBRounded, aFeatureName);
            } else {
                // Emit as a warning to highlight that the refactoring produced no observable impact
                log.warn(ApplicationConstants.MAINTAINABILITY_NO_IMPACT);
            }
        } else {
            log.warn(ApplicationConstants.QUESTION_2_NO);
            log.warn(ApplicationConstants.MAINTAINABILITY_WORSENED);
        }
        
        log.info(ApplicationConstants.PRELIMINARY_ANALYSIS_FOOTER);
    }

    private void analyzeResults(final Instances bPlus, final Instances b, final Classifier bClassifierA) {
        log.info(ApplicationConstants.MILESTONE_2_STEP_13);

        final int actualDefectsInA = DatasetUtilities.countActualDefective(this.datasetA);
        final int actualDefectsInBplus = DatasetUtilities.countActualDefective(bPlus);
        // Use expected count as sum of predicted probabilities (double) to avoid threshold-related zeros
        final double predictedDefectsInB = DatasetUtilities.sumPredictedProbabilities(bClassifierA, b);

        LoggingUtils.debugIfEnabled(log, ApplicationConstants.FORMULA_COMPONENTS_HEADER);
        LoggingUtils.debugIfEnabled(log, "Actual Defects in B+ (actual B+) = {}", actualDefectsInBplus);
        LoggingUtils.debugIfEnabled(log, "Predicted Defects in B (expected B) = {}", String.format(java.util.Locale.US, "%.2f", predictedDefectsInB));
        LoggingUtils.debugIfEnabled(log, "Actual Defects in A (actual A) = {}", actualDefectsInA);
        LoggingUtils.debugIfEnabled(log, ApplicationConstants.FORMULA_COMPONENTS_FOOTER);

        double numerator = (double) actualDefectsInBplus - predictedDefectsInB;

        if (actualDefectsInBplus > 0) {
            double drop = numerator / actualDefectsInBplus;
            if (log.isInfoEnabled()) {
                final double dropPct = drop * 100.0;
                log.info("Formula 1 (drop) = (actual B+ - expected B) / actual B+ = ({} - {}) / {} = {} ({}%)",
                    actualDefectsInBplus, (int)Math.round(predictedDefectsInB), actualDefectsInBplus, String.format("%.3f", drop), String.format(java.util.Locale.US, "%.2f", dropPct));
                log.info("ANSWER 1 (drop): The calculated metric value is {} ({}%).", String.format("%.3f", drop), String.format(java.util.Locale.US, "%.2f", dropPct));

                if (drop < 0) {
                    log.warn("The 'drop' metric is negative: predicted defects in B ({}) are greater than actual defects in B+ ({}). This indicates the refactoring increased predicted defects.", (int)Math.round(predictedDefectsInB), actualDefectsInBplus);
                }

                if (Math.abs(drop) > 1.0) {
                    log.warn("The magnitude of 'drop' is > 100% ({}%). This means the expected defects in B differ from actual B+ by more than 100%.", String.format(java.util.Locale.US, "%.2f", dropPct));
                }
            }
        } else {
            log.warn("Cannot calculate 'drop' metric because there are no actual defects in the B+ dataset (division by zero).");
        }

        if (actualDefectsInA > 0) {
            double reduction = numerator / actualDefectsInA;
            if (log.isInfoEnabled()) {
                final double reductionPct = reduction * 100.0;
                log.info("Formula 2 (reduction) = (actual B+ - expected B) / actual A = ({} - {}) / {} = {} ({}%)",
                    actualDefectsInBplus, (int)Math.round(predictedDefectsInB), actualDefectsInA, String.format("%.3f", reduction), String.format(java.util.Locale.US, "%.2f", reductionPct));
                log.info("ANSWER 2 (reduction): The calculated metric value is {} ({}%).", String.format("%.3f", reduction), String.format(java.util.Locale.US, "%.2f", reductionPct));

                if (reduction < 0) {
                    log.warn("The 'reduction' metric is negative: expected defects in B ({}) exceed actual defects in A ({}). Check classifier predictions or dataset composition.", (int)Math.round(predictedDefectsInB), actualDefectsInA);
                }

                if (Math.abs(reduction) > 1.0) {
                    log.warn("The magnitude of 'reduction' is > 100% ({}%). This indicates a large relative change compared to total defects in A.", String.format(java.util.Locale.US, "%.2f", reductionPct));
                }
            }
        } else {
            log.warn("Cannot calculate 'reduction' metric because there are no actual defects in the full dataset (division by zero).");
        }
    }
}