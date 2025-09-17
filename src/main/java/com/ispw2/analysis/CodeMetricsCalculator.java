package com.ispw2.analysis;

import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class CodeMetricsCalculator {

    private CodeMetricsCalculator() {
        // Utility class with a private constructor
    }
    
    public static Map<String, Number> calculateAll(final CallableDeclaration<?> callable) {
        final Map<String, Number> features = new HashMap<>();
        features.put(CodeQualityMetrics.CODE_SMELLS, calculateCodeSmells(callable));
        features.put(CodeQualityMetrics.CYCLOMATIC_COMPLEXITY, calculateCyclomaticComplexity(callable));
        features.put(CodeQualityMetrics.PARAMETER_COUNT, callable.getParameters().size());
        return features;
    }

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