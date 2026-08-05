package com.copilot.llm;

/** Wraps upstream LLM/embedding provider failures (timeouts, rate limits, bad responses)
 *  so callers get one consistent exception type instead of reaching into HTTP internals. */
public class LlmException extends RuntimeException {
    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
    public LlmException(String message) {
        super(message);
    }
}
