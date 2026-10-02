package com.gliwka.hyperscan.util;


import com.gliwka.hyperscan.wrapper.Database;
import com.gliwka.hyperscan.wrapper.Match;
import com.gliwka.hyperscan.wrapper.Scanner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The default {@link ScopedPatternFilter} implementation, owning one thread's worth of mutable
 * Hyperscan state.
 *
 * <p>It holds a reference to the shared, immutable {@link Database} compiled by the owning
 * {@link ScopedPatternFilterFactory} together with a private {@link Scanner} and scratch space —
 * the part of Hyperscan that is not safe to share between threads. Because the factory may close
 * an instance from its cleanup thread while the owning thread is mid-scan, both the per-instance
 * close and the scan synchronize on the scanner, and the shared {@code databaseClosed} flag lets
 * the instance detect when the factory has released the database underneath it.
 *
 * <p>Instances are created and tracked exclusively by {@link ScopedPatternFilterFactory}; callers
 * never construct or close one directly (they interact with a {@link ScopedPatternFilterProxy}).
 *
 * @param <T> the type of the original object associated with each pattern
 * @see ScopedPatternFilterFactory
 */
final class ScopedPatternFilterImpl<T> implements ScopedPatternFilter<T> {

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean databaseClosed;
    private final Database database;
    private final List<T> filterable;
    private final List<T> notFilterable;
    private final Scanner scanner;

    ScopedPatternFilterImpl(Database database, AtomicBoolean databaseClosed, List<T> filterable, List<T> notFilterable) {
        this.database = database;
        this.databaseClosed = databaseClosed;
        this.filterable = filterable;
        this.notFilterable = notFilterable;
        this.scanner = new Scanner();
        this.scanner.allocScratch(database);
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    private static void close(AtomicBoolean closed, Scanner scanner) throws IOException {
        if (closed.compareAndSet(false, true)) {
            // Ensure scanner and database are closed in a thread-safe manner
            synchronized (scanner) {
                scanner.close();
            }
        }
    }

    private void ensureNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("This pattern filter has already been closed.");
        }
        if (databaseClosed.get()) {
            throw new IllegalStateException("The backing ScopedPatternFilterFactory has already been closed.");
        }
    }

    @Override
    public List<T> filter(String input) {
        Objects.requireNonNull(input, "The input string must not be null.");
        ensureNotClosed();
        List<Match> matches;
        // Close is performed by another thread, so we need to synchronize access to the scanner
        // In a single-threaded context because of the lite locking mechanism by JVM, the performance
        // impact should be minimal
        synchronized (scanner) {
            ensureNotClosed();
            matches = scanner.scan(database, input);
        }
        if (matches.isEmpty()) {
            return Collections.unmodifiableList(notFilterable);
        }
        List<T> result = new ArrayList<>(matches.size() + notFilterable.size());
        result.addAll(notFilterable);
        for (Match match : matches) {
            result.add(filterable.get(match.getMatchedExpression().getId()));
        }
        return result;
    }

    @Override
    public void close() throws IOException {
        close(closed, scanner);
    }

    @Override
    public Runnable getCloseAction() {
        AtomicBoolean closed = this.closed;
        Scanner scanner = this.scanner;
        // Use local copies to avoid lambda capturing the whole instance, which could prevent GC
        return () -> {
            try {
                close(closed, scanner);
            } catch (IOException e) {
                // Log or handle exception if needed
            }
        };
    }
}

