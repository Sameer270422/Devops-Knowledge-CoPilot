package com.copilot.common;

/** Thrown when an authenticated user tries to touch a resource they don't own. */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}
