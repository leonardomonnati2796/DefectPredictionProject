package com.ispw2.analysis;

/**
 * Exception thrown when dataset creation operations fail.
 * This is a dedicated exception for dataset-related errors.
 */
public class DatasetCreationException extends Exception {
    
    public DatasetCreationException(String message) {
        super(message);
    }
    
    public DatasetCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}
