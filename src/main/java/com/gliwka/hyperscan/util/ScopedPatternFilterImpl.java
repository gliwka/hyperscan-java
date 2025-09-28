package com.gliwka.hyperscan.util;


import com.gliwka.hyperscan.wrapper.CompileErrorException;
import com.gliwka.hyperscan.wrapper.Database;
import com.gliwka.hyperscan.wrapper.Expression;
import com.gliwka.hyperscan.wrapper.Match;
import com.gliwka.hyperscan.wrapper.Scanner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Pattern;

final class ScopedPatternFilterImpl<T> implements ScopedPatternFilter<T> {

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Database database;
    private final Scanner scanner;
    private final List<T> filterable;
    private final List<T> notFilterable;

    /**
     * Create a pattern filter for the provided patterns
     *
     * @param patterns Patterns to be filtered
     * @throws CompileErrorException in case the compilation of the hyperscan representation fails
     */
    ScopedPatternFilterImpl(List<T> patterns, Function<? super T, ? extends Pattern> patternMapper) throws CompileErrorException {
        List<Expression> expressions = new ArrayList<>();
        List<T> notFilterable = new ArrayList<>();
        List<T> filterable = new ArrayList<>();

        for (T pattern : patterns) {
            Pattern p = patternMapper.apply(pattern);
            Objects.requireNonNull(p, "a patternMapper returned null for " + pattern);
            Expression expression = ExpressionUtil.mapToExpression(p, filterable.size());

            if (expression == null) {
                // can't be compiled to expression -> not filterable
                notFilterable.add(pattern);
            } else {
                expressions.add(expression);
                filterable.add(pattern);
            }
        }

        this.database = Database.compile(expressions);
        this.scanner = new Scanner();
        this.scanner.allocScratch(database);
        this.filterable = ImmutableList.copyOf(filterable);
        this.notFilterable = ImmutableList.copyOf(notFilterable);
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    private static void close(AtomicBoolean closed, Scanner scanner, Database database) throws IOException {
        if (closed.compareAndSet(false, true)) {
            // Ensure scanner and database are closed in a thread-safe manner
            synchronized (scanner) {
                scanner.close();
                database.close();
            }
        }
    }

    @Override
    public List<T> filter(String input) {
        Preconditions.checkNotNull(input);
        Preconditions.checkState(!closed.get(), "Pattern filter is closed");
        List<Match> matches;
        // Close is performed by another thread, so we need to synchronize access to the scanner
        // In a single-threaded context because of the lite locking mechanism by JVM, the performance
        // impact should be minimal
        synchronized (scanner) {
            Preconditions.checkState(!closed.get(), "Pattern filter is closed");
            matches = scanner.scan(database, input);
        }
        List<T> potentialMatches = new ArrayList<>(matches.size() + notFilterable.size());
        for (Match match : matches) {
            potentialMatches.add(filterable.get(match.getMatchedExpression().getId()));
        }
        potentialMatches.addAll(notFilterable);
        return potentialMatches;
    }

    @Override
    public void close() throws IOException {
        close(closed, scanner, database);
    }

    @Override
    public Runnable getCloseAction() {
        AtomicBoolean closed = this.closed;
        Database database = this.database;
        Scanner scanner = this.scanner;
        // Use local copies to avoid lambda capturing the whole instance, which could prevent GC
        return () -> {
            try {
                close(closed, scanner, database);
            } catch (IOException e) {
                // Log or handle exception if needed
            }
        };
    }
}

