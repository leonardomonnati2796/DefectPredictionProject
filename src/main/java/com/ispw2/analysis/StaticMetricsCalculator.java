package com.ispw2.analysis;

import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class StaticMetricsCalculator {

    private StaticMetricsCalculator() {
        // Sonar: Aggiungere un costruttore privato per le classi di utilità
    }
    
    public static Map<String, Number> calculateAll(final CallableDeclaration<?> callable) {
        final Map<String, Number> features = new HashMap<>();
        features.put("LOC", calculateLOC(callable));
        features.put("CyclomaticComplexity", calculateCyclomaticComplexity(callable));
        features.put("ParameterCount", callable.getParameters().size());
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