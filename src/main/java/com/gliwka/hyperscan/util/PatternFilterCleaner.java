package com.gliwka.hyperscan.util;


import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;

/**
 * A {@link PhantomReference} to a thread-local {@link ScopedPatternFilter} that releases the
 * filter's native resources once the filter becomes unreachable (typically when its owning thread
 * dies).
 *
 * <p>It captures the filter's close action ({@link ScopedPatternFilter#getCloseAction()}) at
 * construction rather than holding the filter itself, so the referent can be reclaimed. When the
 * reference is enqueued, {@link ScopedPatternFilterFactory} polls the queue and invokes {@link
 * #clean()} to run that action. The factory also retains a strong reference to each cleaner so the
 * phantom reference is not itself collected before it can be enqueued.
 */
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

