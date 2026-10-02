package com.gliwka.hyperscan.util;

import com.gliwka.hyperscan.wrapper.CompileErrorException;
import com.gliwka.hyperscan.wrapper.Database;
import com.gliwka.hyperscan.wrapper.Expression;
import lombok.AccessLevel;
import lombok.Getter;

import java.io.Closeable;
import java.lang.ref.ReferenceQueue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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


    // --- Instance-specific fields ---
    private final ReferenceQueue<ScopedPatternFilter<?>> referenceQueue = new ReferenceQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // Guards the shared database's lifecycle: filter creation (which allocates scratch over the
    // database) takes the read lock, while close() takes the write lock to free it. This prevents
    // a thread from allocating scratch over a database that close() is concurrently releasing.
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();

    @Getter(AccessLevel.PACKAGE)
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final Set<PatternFilterCleaner> refKeeper = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    private final ScheduledFuture<?> cleanerTaskFuture; // Handle to this instance's cleanup task.
    private final Database database;
    private final List<T> filterable;
    private final List<T> notFilterable;

    @Getter(AccessLevel.PACKAGE)
    private final ThreadLocal<ScopedPatternFilter<T>> threadLocalFilters = ThreadLocal.withInitial(this::createFilter);

    private ScopedPatternFilterFactory(Database database, List<T> filterable, List<T> notFilterable) {
        this.database = database;
        this.filterable = filterable;
        this.notFilterable = notFilterable;
        // Schedule this instance's cleanup task on the shared executor.
        this.cleanerTaskFuture = ExecutorHolder.CLEANER_SERVICE.scheduleWithFixedDelay(this::cleanUp, 1, 1, TimeUnit.SECONDS);
    }

    private ScopedPatternFilterFactory(List<T> notFilterable) {
        this.database = null;
        this.filterable = Collections.emptyList();
        this.notFilterable = notFilterable;
        // Schedule this instance's cleanup task on the shared executor.
        this.cleanerTaskFuture = null; // No cleanup needed since there are no filter instances.
    }

    /**
     * Creates a factory for the given collection of arbitrary objects, deriving a
     * {@link Pattern} from each via {@code patternMapper}.
     *
     * <p>Each pattern is classified as either <i>filterable</i> (compatible with Hyperscan's
     * prefilter mode) or <i>not filterable</i> (e.g. it uses unsupported constructs such as
     * lookarounds). The filterable patterns are compiled once into a single shared Hyperscan
     * {@link Database}; the non-filterable patterns are always returned as candidates by
     * {@link ScopedPatternFilter#filter(String)}, since this filter cannot rule them out.
     *
     * @param patterns      the source objects to filter; must not be {@code null} and must
     *                      yield at least one element
     * @param patternMapper maps each source object to the {@link Pattern} used for matching;
     *                      must not be {@code null} and must not return {@code null}
     * @param <T>           the type of the source objects
     * @return a factory ready to dispense thread-safe filters for the given patterns
     * @throws NullPointerException     if {@code patterns} or {@code patternMapper} is
     *                                  {@code null}, or if {@code patternMapper} returns
     *                                  {@code null} for any element
     * @throws IllegalArgumentException if {@code patterns} is empty
     * @throws RuntimeException         if the filterable patterns fail to compile into a
     *                                  Hyperscan database
     */
    public static <T> ScopedPatternFilterFactory<T> create(Iterable<T> patterns, Function<? super T, ? extends Pattern> patternMapper) {
        Objects.requireNonNull(patternMapper, "The patternMapper function must not be null.");
        Objects.requireNonNull(patterns, "The patterns iterable must not be null.");

        List<Expression> expressions = new ArrayList<>();
        List<T> notFilterable = new ArrayList<>();
        List<T> filterable = new ArrayList<>();

        for (T pattern : patterns) {
            Pattern p = patternMapper.apply(pattern);
            Objects.requireNonNull(p, "The patternMapper returned null for pattern: " + pattern + ".");
            Expression expression = ExpressionUtil.mapToExpression(p, filterable.size());

            if (expression == null) {
                // can't be compiled to expression -> not filterable
                notFilterable.add(pattern);
            } else {
                expressions.add(expression);
                filterable.add(pattern);
            }
        }

        if (filterable.isEmpty() && notFilterable.isEmpty()) {
            throw new IllegalArgumentException("At least one pattern must be provided; the patterns iterable was empty.");
        }

        if (!filterable.isEmpty()) {
            try {
                Database database = Database.compile(expressions);
                return new ScopedPatternFilterFactory<>(database, filterable, notFilterable);
            } catch (CompileErrorException e) {
                throw new RuntimeException("Failed to compile the provided patterns into a Hyperscan database.", e);
            }
        } else {
            // No filterable patterns, so we can skip creating a Database and just return a factory with
            // notFilterable patterns.
            return new ScopedPatternFilterFactory<>(notFilterable);
        }
    }

    /**
     * Convenience factory for a collection of {@link Pattern} objects. Equivalent to
     * {@link #create(Iterable, Function)} with the identity mapper.
     *
     * @param patterns the patterns to filter; must not be {@code null} and must not be empty
     * @return a factory ready to dispense thread-safe filters for the given patterns
     * @throws NullPointerException     if {@code patterns} is {@code null}
     * @throws IllegalArgumentException if {@code patterns} is empty
     * @throws RuntimeException         if the patterns fail to compile into a Hyperscan database
     */
    public static ScopedPatternFilterFactory<Pattern> ofPatterns(Iterable<Pattern> patterns) {
        return create(patterns, Function.identity());
    }


    // This is an instance method that knows about this instance's queue and refKeeper.
    // Holds the read lock so its scanner closes and refKeeper removals cannot overlap close()'s
    // teardown (which holds the write lock and frees the shared database).
    private void cleanUp() {
        lifecycleLock.readLock().lock();
        try {
            PatternFilterCleaner ref;
            while ((ref = (PatternFilterCleaner) referenceQueue.poll()) != null) {
                refKeeper.remove(ref);
                ref.clean();
            }
        } catch (Exception e) {
            // Log or handle exception
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    private ScopedPatternFilter<T> createFilter() {
        if (database == null) {
            return null;
        }
        // Hold the read lock so the database cannot be freed by close() while we allocate scratch
        // over it. Re-check closed under the lock: if close() won the race, refuse to create a
        // filter over the now-defunct database instead of crashing in allocScratch.
        lifecycleLock.readLock().lock();
        try {
            if (closed.get()) {
                throw new IllegalStateException("This ScopedPatternFilterFactory has already been closed.");
            }
            ScopedPatternFilterImpl<T> filter = new ScopedPatternFilterImpl<>(database, closed, filterable, notFilterable);
            // Use this instance's referenceQueue.
            PatternFilterCleaner cleaner = new PatternFilterCleaner(filter, referenceQueue);
            refKeeper.add(cleaner);
            return filter;
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    /**
     * Returns a {@link ScopedPatternFilter} bound to the calling thread. The first call on a
     * given thread lazily creates that thread's filter (allocating its own Hyperscan scanner and
     * scratch space over the shared database); subsequent calls on the same thread reuse it.
     *
     * <p>The returned value is a lightweight, non-closeable proxy: calling {@link
     * ScopedPatternFilter#close()} on it is a no-op, so it is safe to use in a
     * try-with-resources block without accidentally tearing down the shared thread-local
     * instance. The underlying filter's lifetime is managed by this factory — explicitly via
     * {@link #close()}, or automatically once the owning thread dies and is garbage collected.
     *
     * @return a thread-safe filter for the current thread
     * @throws IllegalStateException if this factory has already been closed
     */
    @Override
    public ScopedPatternFilter<T> get() {
        if (closed.get()) {
            throw new IllegalStateException("This ScopedPatternFilterFactory has already been closed.");
        }
        if (database == null) {
            // No filterable patterns, so just return a simple filter that returns notFilterable patterns.
            return new PatternOnlyFilter<>(notFilterable);
        }
        ScopedPatternFilter<T> filter = threadLocalFilters.get();
        return new ScopedPatternFilterProxy<>(filter);
    }

    /**
     * Releases all native resources held by this factory: it cancels the background cleanup
     * task, closes every live thread-local scanner (waiting out any in-flight scan), and frees
     * the shared Hyperscan database. After this call, {@link #get()} throws and any previously
     * dispensed filter throws on use.
     *
     * <p>This method is idempotent; only the first invocation performs work. Failing to close a
     * factory that compiled a database leaks native memory, so it <b>must</b> be called when the
     * factory is no longer needed.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            if (cleanerTaskFuture != null) {
                this.cleanerTaskFuture.cancel(false);
            }
            // Take the write lock so teardown cannot overlap with createFilter()'s allocScratch over
            // the shared database. Any in-flight creation finishes first; any creation that starts
            // after this sees closed == true and bails out.
            lifecycleLock.writeLock().lock();
            try {
                // The write lock excludes createFilter() and cleanUp() (both read-lock holders), so
                // refKeeper is structurally stable here and can be iterated directly. Close every
                // live filter's scanner before freeing the shared database: each close action
                // synchronizes on its scanner, so it waits out any in-flight scan (and the closed
                // flag set above blocks new ones) — guaranteeing no scan can touch the database once
                // we free it.
                for (PatternFilterCleaner cleaner : refKeeper) {
                    cleaner.clean();
                }
                // All scanners (and their scratch) are now closed; release the shared native database.
                if (database != null) {
                    database.close();
                }
                refKeeper.clear();
            } finally {
                lifecycleLock.writeLock().unlock();
            }
        }
    }

    private enum ExecutorHolder {
        ;
        // A single, shared, daemon cleaner thread for all factory instances.
        static final ScheduledExecutorService CLEANER_SERVICE = Executors.newSingleThreadScheduledExecutor(new NamedDaemonThreadFactory());
    }

    private static final class NamedDaemonThreadFactory implements ThreadFactory {
        private static final String NAME_FORMAT = "ScopedPatternFilter-Shared-Cleaner-%d";
        private final ThreadFactory delegate = Executors.defaultThreadFactory();
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = delegate.newThread(r);
            t.setName(String.format(NAME_FORMAT, counter.getAndIncrement()));
            t.setDaemon(true);
            return t;
        }
    }
}

