package com.gliwka.hyperscan.wrapper;

@FunctionalInterface
interface RawMatchEventHandler {
    boolean onMatch(int expressionId, long fromByteIdx, long toByteIdxExclusive, int expressionFlags);
}
