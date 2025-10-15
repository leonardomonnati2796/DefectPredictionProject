package com.ispw2.analysis;

/**
 * Exception thrown when classifier training operations fail.
 * This is a dedicated exception for classifier-related errors.
 */
public class ClassifierTrainingException extends Exception {
    
    public ClassifierTrainingException(String message) {
        super(message);
    }
    
    public ClassifierTrainingException(String message, Throwable cause) {
        super(message, cause);
    }
}
