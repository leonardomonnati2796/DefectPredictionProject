package com.ispw2.util;

import com.ispw2.model.AnalyzedMethod;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Utility class for common method operations to reduce code duplication.
 * Provides standardized method handling methods used across the application.
 */
public final class MethodUtils {

    private MethodUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Creates a method identifier string from file path and signature.
     * 
     * @param filePath The file path of the method
     * @param signature The method signature
     * @return The method identifier string
     */
    public static String createMethodIdentifier(final String filePath, final String signature) {
        return filePath + "/" + signature;
    }

    /**
     * Parses a method identifier string into file path and signature.
     * 
     * @param methodIdentifier The method identifier string
     * @return An array with [filePath, signature] or null if parsing fails
     */
    public static String[] parseMethodIdentifier(final String methodIdentifier) {
        if (methodIdentifier == null || methodIdentifier.isEmpty()) {
            return null;
        }
        
        final int lastSlashIndex = methodIdentifier.lastIndexOf('/');
        if (lastSlashIndex == -1) {
            return null;
        }
        
        return new String[]{
            methodIdentifier.substring(0, lastSlashIndex),
            methodIdentifier.substring(lastSlashIndex + 1)
        };
    }

    /**
     * Checks if a method is trivial (accessor/toString-like).
     * 
     * @param method The method to check
     * @return true if method is considered trivial
     */
    public static boolean isTrivialMethod(final AnalyzedMethod method) {
        if (method == null) {
            return true;
        }
        
        final Map<String, Number> features = method.getFeatures();
        final int cyclomaticComplexity = DataUtils.getNumberOrDefault(features, "CyclomaticComplexity", 0).intValue();
        final int parameterCount = DataUtils.getNumberOrDefault(features, "ParameterCount", 0).intValue();
        final int nestingDepth = DataUtils.getNumberOrDefault(features, "NestingDepth", 0).intValue();
        
        return cyclomaticComplexity <= 1 && parameterCount <= 1 && nestingDepth <= 1;
    }

    /**
     * Logs method information for debugging purposes.
     * 
     * @param logger The logger instance
     * @param method The method to log information about
     */
    public static void logMethodInfo(final Logger logger, final AnalyzedMethod method) {
        if (logger.isDebugEnabled() && method != null) {
            logger.debug("Method: {} - Features: {}", 
                createMethodIdentifier(method.filepath(), method.signature()), 
                method.getFeatures());
        }
    }

    /**
     * Logs a list of methods for debugging purposes.
     * 
     * @param logger The logger instance
     * @param methodListName The name of the method list for logging
     * @param methods The list of methods to log
     */
    public static void logMethodList(final Logger logger, final String methodListName, final List<AnalyzedMethod> methods) {
        if (logger.isDebugEnabled()) {
            final int size = methods != null ? methods.size() : 0;
            logger.debug("{} contains {} methods", methodListName, size);
        }
    }

    /**
     * Checks if two methods are equal based on their identifiers.
     * 
     * @param method1 The first method
     * @param method2 The second method
     * @return true if methods have the same identifier
     */
    public static boolean areMethodsEqual(final AnalyzedMethod method1, final AnalyzedMethod method2) {
        if (method1 == null || method2 == null) {
            return Objects.equals(method1, method2);
        }
        
        return Objects.equals(method1.id(), method2.id());
    }
}
