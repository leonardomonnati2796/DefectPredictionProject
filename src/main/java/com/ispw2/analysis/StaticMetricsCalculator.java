package com.ispw2.analysis;

import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class StaticMetricsCalculator {

    private StaticMetricsCalculator() {
        // Utility class with a private constructor
    }
    
    public static Map<String, Number> calculateAll(final CallableDeclaration<?> callable) {
        final Map<String, Number> features = new HashMap<>();
        features.put(Metrics.LOC, calculateLOC(callable));
        features.put(Metrics.CYCLOMATIC_COMPLEXITY, calculateCyclomaticComplexity(callable));
        features.put(Metrics.PARAMETER_COUNT, callable.getParameters().size());
        return features;
    }

    public static int calculateLOC(final CallableDeclaration<?> callable) {
        if (callable.getBegin().isPresent() && callable.getEnd().isPresent()) {
            return callable.getEnd().get().line - callable.getBegin().get().line + 1;
        }
        return 0;
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