package com.gliwka.hyperscan.util;

import java.util.List;

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

