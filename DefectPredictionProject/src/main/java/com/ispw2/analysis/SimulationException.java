package com.ispw2.analysis;

/**
 * Exception thrown when simulation operations fail.
 * This is a dedicated exception for simulation-related errors.
 */
public class SimulationException extends Exception {
    
    public SimulationException(String message) {
        super(message);
    }
    
    public SimulationException(String message, Throwable cause) {
        super(message, cause);
    }
}
