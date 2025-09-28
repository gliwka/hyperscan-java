package com.gliwka.hyperscan.util;

import com.gliwka.hyperscan.wrapper.CompileErrorException;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.AccessLevel;
import lombok.Getter;

import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.ReferenceQueue;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * A factory for creating and managing thread-local instances of {@link ScopedPatternFilter}.
 *
 * <p>This class is the primary entry point for using the Hyperscan filtering mechanism.
 * It is designed to be created once and shared across an application. It addresses two
 * key challenges:
 * <ol>
 *   <li><b>Performance:</b> The high cost of compiling Hyperscan databases is amortized
 *       by creating a single, thread-local filter instance that is reused for all
 *       subsequent operations on that thread.</li>
 *   <li><b>Thread Safety:</b> Hyperscan's scanning context (scratch space) is not
 *       thread-safe. This factory ensures each thread gets its own isolated instance,
 *       preventing concurrent access issues.</li>
 * </ol>
 *
 * <h3>Usage Pattern</h3>
 * A single factory instance should be created and retained for the lifetime of the
 * application. In methods that require filtering, {@link #get()} should be called within
 * a try-with-resources block to obtain a thread-safe filter instance.
 *
 * <p>Example usage:
 * <pre>{@code
 * // In application initialization:
 * List<Pattern> myPatterns = loadPatterns();
 * ScopedPatternFilterFactory<Pattern> filterFactory = ScopedPatternFilterFactory.ofPatterns(myPatterns);
 *
 * // In a service method (called by multiple threads):
 * public void processText(String text) {
 *     try (ScopedPatternFilter<Pattern> filter = filterFactory.get()) {
 *         List<Pattern> candidates = filter.filter(text);
 *         // ... perform final matching on candidates ...
 *     }
 * }
 *
 * // In application shutdown:
 * filterFactory.close();
 * }</pre>
 *
 * <h3>Lifecycle and Resource Management</h3>
 * The factory manages a complex lifecycle:
 * <ul>
 *   <li><b>Thread-Local Caching:</b> Calling {@link #get()} returns a lightweight proxy to a
 *       thread-local {@code ScopedPatternFilter} instance. The actual filter implementation is
 *       cached and reused for the lifetime of the thread. The proxy prevents callers from
 *       accidentally closing the shared, thread-local instance.</li>
 *   <li><b>Automatic Cleanup:</b> The factory automatically manages the cleanup of resources
 *       for threads that have terminated. It uses a background cleaner thread to release the
 *       native Hyperscan resources associated with a dead thread, preventing memory leaks.</li>
 *   <li><b>Factory Closure:</b> The factory itself is {@link Closeable}. When the factory is no
 *       longer needed (e.g., during application shutdown), its {@link #close()} method
 *       <b>must</b> be called. This will explicitly release all active filter resources it has
 *       created and shut down its background cleanup task. Failure to close the factory
 *       will result in resource leaks.</li>
 * </ul>
 *
 * @param <T> The type of the original object from which a pattern can be derived.
 * @see ScopedPatternFilter
 */
public final class ScopedPatternFilterFactory<T> implements Supplier<ScopedPatternFilter<T>>, Closeable {

    // A single, shared, daemon cleaner thread for all factory instances.
    private static final ScheduledExecutorService CLEANER_SERVICE = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setThreadFactory(Executors.defaultThreadFactory()).setNameFormat("ScopedPatternFilter-Shared-Cleaner-%d").setDaemon(true).build());

    // --- Instance-specific fields ---
    private final ReferenceQueue<ScopedPatternFilter<?>> referenceQueue = new ReferenceQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    @Getter(AccessLevel.PACKAGE)
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final Set<PatternFilterCleaner> refKeeper = ConcurrentHashMap.newKeySet();

    @Getter(AccessLevel.PACKAGE)
    private final ConcurrentMap<Thread, ScopedPatternFilter<T>> threadLocalFilters = new MapMaker().weakKeys().makeMap();

    private final ScheduledFuture<?> cleanerTaskFuture; // Handle to this instance's cleanup task.

    private final List<T> patterns;
    private final Function<? super T, ? extends Pattern> patternMapper;

    public ScopedPatternFilterFactory(Iterable<T> patterns, Function<? super T, ? extends Pattern> patternMapper) {
        Preconditions.checkNotNull(patternMapper, "patternMapper cannot be null");
        Preconditions.checkNotNull(patterns, "patterns cannot be null");
        this.patterns = ImmutableList.copyOf(patterns);
        Preconditions.checkArgument(!this.patterns.isEmpty(), "patterns cannot be empty");
        this.patternMapper = patternMapper;

        // Schedule this instance's cleanup task on the shared executor.
        this.cleanerTaskFuture = CLEANER_SERVICE.scheduleWithFixedDelay(this::cleanUp, 1, 1, TimeUnit.SECONDS);
    }

    public static ScopedPatternFilterFactory<Pattern> ofPatterns(Iterable<Pattern> patterns) {
        return new ScopedPatternFilterFactory<>(patterns, Function.identity());
    }

    // This is an instance method that knows about this instance's queue and refKeeper.
    private void cleanUp() {
        try {
            PatternFilterCleaner ref;
            while ((ref = (PatternFilterCleaner) referenceQueue.poll()) != null) {
                refKeeper.remove(ref);
                ref.clean();
            }
        } catch (Exception e) {
            // Log or handle exception
        }
    }

    private ScopedPatternFilter<T> createFilter() {
        Preconditions.checkState(!closed.get(), "ScopedPatternFilterFactory is closed.");
        try {
            ScopedPatternFilterImpl<T> filter = new ScopedPatternFilterImpl<>(patterns, patternMapper);
            // Use this instance's referenceQueue.
            PatternFilterCleaner cleaner = new PatternFilterCleaner(filter, referenceQueue);
            refKeeper.add(cleaner);
            return filter;
        } catch (CompileErrorException e) {
            throw new RuntimeException("Failed to compile patterns into ScopedPatternFilter", e);
        }
    }

    @Override
    public ScopedPatternFilter<T> get() {
        Preconditions.checkState(!closed.get(), "ScopedPatternFilterFactory is closed.");
        ScopedPatternFilter<T> filter = threadLocalFilters.computeIfAbsent(Thread.currentThread(), t -> createFilter());
        return new ScopedPatternFilterProxy<>(filter);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {

            // 1. Explicitly close all still-active filter instances.
            for (Map.Entry<Thread, ScopedPatternFilter<T>> entry : threadLocalFilters.entrySet()) {
                try {
                    entry.getValue().close();
                } catch (IOException e) {
                    // Log this error.
                }
            }

            // 2. Clear the map to release references.
            threadLocalFilters.clear();

            // 3. Cancel this instance's scheduled cleanup task.
            // Other factory instances' tasks on the shared executor are unaffected.
            this.cleanerTaskFuture.cancel(false);

            // 4. Perform a final cleanup pass and clear the reference keeper.
            cleanUp();
            refKeeper.clear();
        }
    }
}

