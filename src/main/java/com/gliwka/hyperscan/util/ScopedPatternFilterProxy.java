package com.gliwka.hyperscan.util;

import java.util.List;

/**
 * A non-closeable wrapper around a thread-local {@link ScopedPatternFilterImpl}, returned by
 * {@link ScopedPatternFilterFactory#get()}.
 *
 * <p>The underlying filter is shared and reused across every {@code get()} call on the same
 * thread, and its lifetime is owned by the factory. This proxy forwards {@link #filter(String)}
 * to the delegate but makes {@link #close()} a no-op, so callers can safely use the result in a
 * try-with-resources block without accidentally tearing down the shared instance.
 *
 * @param <T> the type of the original object associated with each pattern
 */
final class ScopedPatternFilterProxy<T> implements ScopedPatternFilter<T> {

    private final ScopedPatternFilter<T> delegate;

    ScopedPatternFilterProxy(ScopedPatternFilter<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void close() {
        // No operation performed on close
    }

    @Override
    public List<T> filter(String input) {
        return delegate.filter(input);
    }
}

