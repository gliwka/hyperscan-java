package com.gliwka.hyperscan.util;


import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;

final class PatternFilterCleaner extends PhantomReference<ScopedPatternFilter<?>> {

    private final Runnable thunk;

    PatternFilterCleaner(
            ScopedPatternFilter<?> referent, ReferenceQueue<? super ScopedPatternFilter<?>> q) {
        super(referent, q);
        this.thunk = referent.getCloseAction();
    }

    public void clean() {
        if (thunk != null) {
            try {
                thunk.run();
            } catch (Exception e) {
                // Swallow exceptions to avoid disrupting the cleaner thread
            }
        }
    }
}

