package com.gliwka.hyperscan.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.ref.ReferenceQueue;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PatternFilterCleanerTest {

    @Test
    void clean_shouldExecuteTheCloseAction() {
        // Setup
        final AtomicBoolean closeActionWasRun = new AtomicBoolean(false);
        Runnable thunk = () -> closeActionWasRun.set(true);

        FakeScopedPatternFilter referent = new FakeScopedPatternFilter(thunk);
        ReferenceQueue<ScopedPatternFilter<?>> queue = new ReferenceQueue<>();
        PatternFilterCleaner cleaner = new PatternFilterCleaner(referent, queue);

        // Execute
        cleaner.clean();

        // Verify
        assertThat(closeActionWasRun.get()).isTrue();
    }

    @Test
    void clean_shouldSwallowExceptionsThrownByTheCloseAction() {
        // Setup: A close action that always throws an exception
        Runnable thunk = () -> {
            throw new IllegalStateException("Test exception");
        };
        FakeScopedPatternFilter referent = new FakeScopedPatternFilter(thunk);
        ReferenceQueue<ScopedPatternFilter<?>> queue = new ReferenceQueue<>();
        PatternFilterCleaner cleaner = new PatternFilterCleaner(referent, queue);

        // Execute & Verify
        // The test passes if clean() does not throw an exception
        assertDoesNotThrow(cleaner::clean);
    }

    /**
     * A simple, concrete implementation of ScopedPatternFilter for testing purposes.
     */
    private static class FakeScopedPatternFilter implements ScopedPatternFilter<Object> {
        private final Runnable closeAction;

        FakeScopedPatternFilter(Runnable closeAction) {
            this.closeAction = closeAction;
        }

        @Override
        public List<Object> filter(String input) {
            return Collections.emptyList();
        }


        @Override
        public void close() throws IOException {
            // No-op for this fake
        }

        @Override
        public Runnable getCloseAction() {
            return closeAction;
        }
    }
}