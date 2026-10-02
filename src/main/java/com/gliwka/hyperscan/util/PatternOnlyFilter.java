package com.gliwka.hyperscan.util;


import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * A trivial {@link ScopedPatternFilter} used when none of the supplied patterns are filterable by
 * Hyperscan (e.g. they all use unsupported constructs such as lookarounds).
 *
 * <p>With no patterns to prefilter, there is nothing to compile and no native state to manage, so
 * this filter holds no Hyperscan database or scanner. {@link #filter(String)} ignores the input
 * and always returns every pattern as a candidate — they cannot be ruled out — and {@link
 * #close()} is a no-op. {@link ScopedPatternFilterFactory#get()} serves this implementation
 * directly (without the thread-local machinery) for such factories.
 *
 * @param <T> the type of the original object associated with each pattern
 */
@RequiredArgsConstructor
final class PatternOnlyFilter<T> implements ScopedPatternFilter<T> {

    private final List<T> patterns;

    @Override
    public List<T> filter(String input) {
        return Collections.unmodifiableList(patterns);
    }

    @Override
    public void close() {
    }
}

