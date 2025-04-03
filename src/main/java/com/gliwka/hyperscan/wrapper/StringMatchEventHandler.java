package com.gliwka.hyperscan.wrapper;

/**
 * A functional interface for handling Hyperscan match events with string character indices.
 * Implementations of this interface are called by the {@link Scanner} for each
 * match found during a scan operation when using the String-based scanning methods.
 *
 * @see Scanner#scan(Database, String, StringMatchEventHandler)
 */
@FunctionalInterface
public interface StringMatchEventHandler {

    /**
     * Callback method invoked when a pattern matches.
     *
     * @param expression      The {@link Expression} that matched.
     * @param fromStringIndex The starting character index (inclusive) of the match in the original string.
     * @param toStringIndex   The ending character index (inclusive) of the match in the original string.
     * @return {@code true} to continue scanning, {@code false} to stop scanning immediately.
     *         Returning {@code false} provides a way to halt the scan early if a desired match
     *         is found or a condition is met.
     */
    boolean onMatch(Expression expression, long fromStringIndex, long toStringIndex);
}