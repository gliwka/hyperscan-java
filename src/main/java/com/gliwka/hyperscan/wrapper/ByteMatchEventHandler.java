package com.gliwka.hyperscan.wrapper;

/**
 * A functional interface for handling Hyperscan match events with raw byte offsets.
 * Implementations of this interface are called by the {@link Scanner} for each
 * match found during a scan operation when using the byte-offset based scanning methods.
 *
 * @see Scanner#scan(Database, byte[], ByteMatchEventHandler)
 */
@FunctionalInterface
public interface ByteMatchEventHandler {
    /**
     * Callback method invoked when a pattern matches.
     *
     * @param expression The expression that matched.
     * @param fromByteIdx The starting byte offset (inclusive) of the match in the input data.
     * @param toByteIdxExclusive The ending byte offset (exclusive) of the match in the input data.
     * @return {@code true} to continue scanning, {@code false} to stop scanning immediately.
     *         Returning {@code false} provides a way to halt the scan early if a desired match
     *         is found or a condition is met.
     */
    boolean onMatch(Expression expression, long fromByteIdx, long toByteIdxExclusive);
}