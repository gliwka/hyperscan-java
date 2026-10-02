package com.gliwka.hyperscan.util;

import java.io.Closeable;
import java.util.List;
import java.util.function.Function;

/**
 * Represents a pre-compiled filter for a set of regular expression patterns, optimized
 * for high-performance scanning using the Hyperscan library.
 *
 * <p>This interface is designed as an optimization layer to quickly eliminate non-matching
 * patterns from a large collection before performing more expensive, full regex matches.
 * It functions as a "pre-filter" or "candidate selection" tool.
 *
 * <h3>Usage Pattern and Contract</h3>
 * The core method, {@link #filter(String)}, takes an input string and returns a list
 * of "potential matches". This list includes:
 * <ol>
 *   <li>All patterns that were successfully matched by the high-performance Hyperscan engine.</li>
 *   <li>All patterns that could not be compiled by Hyperscan (e.g., those containing
 *       lookarounds or other unsupported features). These are always included as they
 *       cannot be definitively ruled out by this filter.</li>
 * </ol>
 *
 * <p><b>Crucially, the caller is responsible for performing a final, precise match on the
 * returned candidates using a standard regex engine like {@link java.util.regex.Matcher}.</b>
 *
 * <h3>Resource Management</h3>
 * As this interface extends {@link Closeable}, it holds native resources (a compiled
 * Hyperscan database and scratch space) that must be released. It is intended for use
 * within a try-with-resources statement to ensure proper cleanup.
 *
 * <p>Example usage:
 * <pre>{@code
 * ScopedPatternFilterFactory<Pattern> factory = ...;
 *
 * try (ScopedPatternFilter<Pattern> filter = factory.get()) {
 *     List<Pattern> potentialMatches = filter.filter("Some input string to test");
 *     for (Pattern candidate : potentialMatches) {
 *         if (candidate.matcher("Some input string to test").find()) {
 *             // This is a confirmed match.
 *         }
 *     }
 * }
 * }</pre>
 *
 * @param <T> The type of the original object associated with a pattern. This allows the
 *            filter to be used with custom classes, not just {@link java.util.regex.Pattern} objects.
 * @see ScopedPatternFilterFactory
 * @see java.util.regex.Pattern
 */
public interface ScopedPatternFilter<T> extends Closeable, Function<String, List<T>> {

    /**
     * Filters the provided input and returns a list of potentially matching patterns. This method
     * uses the high-performance Hyperscan library for compatible patterns. Any patterns that could
     * not be compiled for Hyperscan are always included in the returned list, as they are considered
     * potential matches that require further checking.
     *
     * @param input Input to be filtered
     * @return A list of patterns that either matched via Hyperscan or could not be filtered by it.
     */
    List<T> filter(String input);

    @Override
    default List<T> apply(String s) {
        return filter(s);
    }

    default Runnable getCloseAction() {
        return () -> {
        };
    }
}

