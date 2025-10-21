package com.ispw2.analysis;

import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import com.ispw2.util.CollectionUtils;

/**
 * Utility class for calculating code quality metrics from Java method AST nodes.
 * Provides static methods for computing various code metrics like cyclomatic complexity and code smells.
 */
public final class CodeMetricsCalculator {

    private CodeMetricsCalculator() {
        // Utility class with a private constructor
    }
    
    /**
     * Calculates all available code metrics for a given method declaration.
     * 
     * @param callable The method declaration to analyze
     * @return Map containing all calculated metrics
     */
    public static Map<String, Number> calculateAll(final CallableDeclaration<?> callable) {
        final Map<String, Number> features = CollectionUtils.createHashMap();
        features.put(CodeQualityMetrics.CODE_SMELLS, calculateCodeSmells(callable));
        features.put(CodeQualityMetrics.CYCLOMATIC_COMPLEXITY, calculateCyclomaticComplexity(callable));
        features.put(CodeQualityMetrics.PARAMETER_COUNT, callable.getParameters().size());
        return features;
    }

    /**
     * Calculates the number of code smells present in a method.
     * Detects common code smells like long methods, too many parameters, and high complexity.
     * 
     * @param callable The method declaration to analyze
     * @return The number of code smells found
     */
    public static int calculateCodeSmells(final CallableDeclaration<?> callable) {
        // Simple code smell detection based on method characteristics
        int codeSmells = 0;
        
        // Long Method smell (more than 20 lines)
        if (callable.getBegin().isPresent() && callable.getEnd().isPresent()) {
            int lines = callable.getEnd().get().line - callable.getBegin().get().line + 1;
            if (lines > 20) codeSmells++;
        }
        
        // Too Many Parameters smell (more than 4 parameters)
        if (callable.getParameters().size() > 4) codeSmells++;
        
        // High Cyclomatic Complexity smell (more than 10)
        int complexity = calculateCyclomaticComplexity(callable);
        if (complexity > 10) codeSmells++;
        
        return codeSmells;
    }

    /**
     * Calculates the cyclomatic complexity of a method by counting decision points.
     * 
     * @param callable The method declaration to analyze
     * @return The cyclomatic complexity value
     */
    public static int calculateCyclomaticComplexity(final CallableDeclaration<?> callable) {
        final AtomicInteger complexity = new AtomicInteger(1);
        callable.walk(node -> {
            if (node instanceof IfStmt || node instanceof ForStmt || node instanceof WhileStmt ||
                node instanceof DoStmt || node instanceof SwitchEntry || node instanceof CatchClause ||
                node instanceof ConditionalExpr) {
                complexity.incrementAndGet();
            }
        });
        return complexity.get();
    }
}