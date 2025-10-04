package com.mcgeecahill.astro.processor.exception;

/**
 * Exception thrown when FITS file processing fails
 */
public class FitsProcessingException extends RuntimeException {

    public FitsProcessingException(String message) {
        super(message);
    }

    public FitsProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public FitsProcessingException(Throwable cause) {
        super(cause);
    }
}