package com.gliwka.hyperscan.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ScopedPatternFilterProxyTest {

    private FakeDelegateFilter<String> delegate;
    private ScopedPatternFilterProxy<String> proxy;

    @BeforeEach
    void setUp() {
        delegate = new FakeDelegateFilter<>();
        proxy = new ScopedPatternFilterProxy<>(delegate);
    }

    @Test
    void filter_shouldDelegateCallToWrappedInstance() {
        String input = "test data";
        List<String> expectedResult = Collections.singletonList("match");
        delegate.setNextResult(expectedResult);

        List<String> actualResult = proxy.filter(input);

        assertThat(actualResult).isSameAs(expectedResult);
        assertThat(delegate.getFilterCallCount()).isEqualTo(1);
        assertThat(delegate.getLastFilteredInput()).isEqualTo(input);
    }

    @Test
    void apply_shouldDelegateToFilterMethod() {
        String input = "test data";
        List<String> expectedResult = Collections.singletonList("match");
        delegate.setNextResult(expectedResult);

        List<String> actualResult = proxy.apply(input);

        assertThat(actualResult).isSameAs(expectedResult);
        assertThat(delegate.getFilterCallCount()).isEqualTo(1);
        assertThat(delegate.getLastFilteredInput()).isEqualTo(input);
    }

    @Test
    void close_shouldBeANoOpAndNotCallCloseOnDelegate() {
        // The proxy's purpose is to prevent users from closing the underlying
        // thread-local filter instance, which is managed by the factory.
        proxy.close();

        assertThat(delegate.isClosed()).isFalse();
    }

    /**
     * A fake ScopedPatternFilter that records interactions for verification.
     */
    private static class FakeDelegateFilter<T> implements ScopedPatternFilter<T> {
        private final AtomicInteger filterCallCount = new AtomicInteger(0);
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private List<T> nextResult = Collections.emptyList();
        private String lastFilteredInput;

        @Override
        public List<T> filter(String input) {
            this.lastFilteredInput = input;
            filterCallCount.incrementAndGet();
            return nextResult;
        }

        @Override
        public void close() throws IOException {
            closed.set(true);
        }

        // --- Test helper methods ---
        public int getFilterCallCount() {
            return filterCallCount.get();
        }

        public boolean isClosed() {
            return closed.get();
        }

        public void setNextResult(List<T> nextResult) {
            this.nextResult = nextResult;
        }

        public String getLastFilteredInput() {
            return lastFilteredInput;
        }
    }
}
