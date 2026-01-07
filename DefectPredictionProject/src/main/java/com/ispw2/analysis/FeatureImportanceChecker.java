package com.ispw2.analysis;

import weka.attributeSelection.InfoGainAttributeEval;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.converters.ArffLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FeatureImportanceChecker {
    private static final Logger log = LoggerFactory.getLogger(FeatureImportanceChecker.class);

    public static void main(String[] args) throws Exception {
        final String arffPath = (args != null && args.length > 0) ? args[0] : "datasets/BOOKKEEPER_processed.arff";
        final ArffLoader loader = new ArffLoader();
        loader.setSource(new File(arffPath));
        final Instances data = loader.getDataSet();
        data.setClassIndex(data.numAttributes() - 1);

        final InfoGainAttributeEval eval = new InfoGainAttributeEval();
        eval.buildEvaluator(data);

        final List<AttrScore> scores = new ArrayList<>();
        for (int i = 0; i < data.numAttributes(); i++) {
            final Attribute a = data.attribute(i);
            if (i == data.classIndex()) continue;
            final double score = eval.evaluateAttribute(i);
            scores.add(new AttrScore(a.name(), score));
        }

        scores.sort(Comparator.comparingDouble((AttrScore s) -> s.score).reversed());
        log.info("Attribute InfoGain ranking (desc):");
        for (final AttrScore s : scores) {
            log.info("{} : {}", s.name, String.format(java.util.Locale.US, "%.6f", s.score));
        }
    }

    private static class AttrScore {
        final String name;
        final double score;
        AttrScore(String name, double score) { this.name = name; this.score = score; }
    }
}
