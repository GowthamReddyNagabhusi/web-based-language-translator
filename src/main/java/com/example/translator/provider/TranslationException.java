package com.example.translator.provider;

/** Typed exception for translation failures with retry/no-retry semantics. */
public class TranslationException extends Exception {

    private final boolean retryable;

    public TranslationException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public TranslationException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() { return retryable; }
}
