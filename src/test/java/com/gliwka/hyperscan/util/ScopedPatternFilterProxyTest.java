package com.gliwka.hyperscan.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ScopedPatternFilterProxy}: it must forward filtering to its delegate while
 * making {@link ScopedPatternFilterProxy#close()} a no-op, since the delegate's lifetime is owned
 * by the factory.
 */
class ScopedPatternFilterProxyTest {

    private FakeDelegateFilter<String> delegate;
    private ScopedPatternFilterProxy<String> proxy;

    @BeforeEach
    void setUp() {
        delegate = new FakeDelegateFilter<>();
        proxy = new ScopedPatternFilterProxy<>(delegate);
    }

    @Test
    void filter_shouldDelegateToWrappedInstance() {
        List<String> expected = Collections.singletonList("match");
        delegate.setNextResult(expected);

        List<String> actual = proxy.filter("input");

        assertThat(actual).isSameAs(expected);
        assertThat(delegate.getFilterCallCount()).isEqualTo(1);
        assertThat(delegate.getLastInput()).isEqualTo("input");
    }

    @Test
    void apply_shouldDelegateToFilter() {
        List<String> expected = Collections.singletonList("match");
        delegate.setNextResult(expected);

        List<String> actual = proxy.apply("input");

        assertThat(actual).isSameAs(expected);
        assertThat(delegate.getFilterCallCount()).isEqualTo(1);
        assertThat(delegate.getLastInput()).isEqualTo("input");
    }

    @Test
    void close_shouldBeNoOpAndNotCloseDelegate() throws IOException {
        proxy.close();

        assertThat(delegate.isClosed()).isFalse();
    }

    /** Records interactions for verification. */
    private static final class FakeDelegateFilter<T> implements ScopedPatternFilter<T> {
        private final AtomicInteger filterCallCount = new AtomicInteger(0);
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private List<T> nextResult = Collections.emptyList();
        private String lastInput;

        @Override
        public List<T> filter(String input) {
            this.lastInput = input;
            filterCallCount.incrementAndGet();
            return nextResult;
        }

        @Override
        public void close() {
            closed.set(true);
        }

        int getFilterCallCount() {
            return filterCallCount.get();
        }

        boolean isClosed() {
            return closed.get();
        }

        void setNextResult(List<T> nextResult) {
            this.nextResult = nextResult;
        }

        String getLastInput() {
            return lastInput;
        }
    }
}
