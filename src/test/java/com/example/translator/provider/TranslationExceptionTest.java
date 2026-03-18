package com.example.translator.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationExceptionTest {

    @Test
    void retryableException() {
        TranslationException ex = new TranslationException("timeout", true);
        assertThat(ex.getMessage()).isEqualTo("timeout");
        assertThat(ex.isRetryable()).isTrue();
    }

    @Test
    void nonRetryableException() {
        TranslationException ex = new TranslationException("bad input", false);
        assertThat(ex.isRetryable()).isFalse();
    }

    @Test
    void exceptionWithCause() {
        RuntimeException cause = new RuntimeException("root cause");
        TranslationException ex = new TranslationException("wrapped", cause, true);
        assertThat(ex.getCause()).isEqualTo(cause);
        assertThat(ex.isRetryable()).isTrue();
    }
}
