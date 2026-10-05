package com.gliwka.hyperscan.util;

import org.junit.jupiter.api.Test;

import java.lang.ref.ReferenceQueue;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Tests for {@link PatternFilterCleaner}: it must run the referent's close action when cleaned and
 * never let an exception from that action escape (which would kill the shared cleaner thread).
 */
class PatternFilterCleanerTest {

    @Test
    void clean_shouldRunTheCloseAction() {
        AtomicBoolean wasRun = new AtomicBoolean(false);
        FakeFilter referent = new FakeFilter(() -> wasRun.set(true));
        PatternFilterCleaner cleaner =
                new PatternFilterCleaner(referent, new ReferenceQueue<>());

        cleaner.clean();

        assertThat(wasRun.get()).isTrue();
    }

    @Test
    void clean_shouldSwallowExceptionsFromTheCloseAction() {
        FakeFilter referent = new FakeFilter(() -> {
            throw new IllegalStateException("boom");
        });
        PatternFilterCleaner cleaner =
                new PatternFilterCleaner(referent, new ReferenceQueue<>());

        assertDoesNotThrow(cleaner::clean);
    }

    private static final class FakeFilter implements ScopedPatternFilter<Object> {
        private final Runnable closeAction;

        FakeFilter(Runnable closeAction) {
            this.closeAction = closeAction;
        }

        @Override
        public List<Object> filter(String input) {
            return Collections.emptyList();
        }

        @Override
        public void close() {
            // no-op
        }

        @Override
        public Runnable getCloseAction() {
            return closeAction;
        }
    }
}
