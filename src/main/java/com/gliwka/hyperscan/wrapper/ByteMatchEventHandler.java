package com.gliwka.hyperscan.wrapper;

@FunctionalInterface
public interface ByteMatchEventHandler {
    boolean onMatch(Expression id, long fromByteIdx, long toByteIdxExclusive);
}
