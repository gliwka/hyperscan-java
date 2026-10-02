package com.gliwka.hyperscan.wrapper;

/**
 * Database compilation mode, determining which scanning APIs the compiled
 * database can be used with.
 */
public enum Mode {
    /**
     * Block mode: the whole input is scanned in one call via
     * {@link Scanner#scan(Database, byte[], ByteMatchEventHandler)} and friends.
     */
    BLOCK,
    /**
     * Streaming mode: input is fed in chunks via
     * {@link Scanner#openStream(Database)}.
     */
    STREAM,
    /**
     * Vectored mode: input is presented as a list of segments via
     * {@link Scanner#scanVector(Database, byte[][], ByteMatchEventHandler)}.
     */
    VECTORED
}
