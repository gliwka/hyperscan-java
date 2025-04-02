package com.gliwka.hyperscan.wrapper;

/**
 * Callback interface for receiving match events during a scan operation
 * using string character indices.
 */
@FunctionalInterface
public interface StringMatchEventHandler {

    /**
     * Called when a match occurs.
     *
     * @param expression      The {@link Expression} that matched.
     * @param fromStringIndex The starting character index (inclusive) of the match in the original string.
     * @param toStringIndexExclusive   The ending character index (exclusive) of the match in the original string.
     * @return {@code true} to continue scanning, {@code false} to stop.
     */
    boolean onMatch(Expression expression, long fromStringIndex, long toStringIndexExclusive);
}
