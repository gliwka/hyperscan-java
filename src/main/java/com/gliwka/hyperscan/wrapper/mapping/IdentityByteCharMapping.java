package com.gliwka.hyperscan.wrapper.mapping;

/**
 * Shared identity mapping for pure-ASCII input, where byte index == char index.
 * Allocates nothing; {@link #getMappingSize()} returns 0 to indicate that no
 * table exists.
 */
public final class IdentityByteCharMapping implements ByteCharMapping {
    private static final IdentityByteCharMapping INSTANCE = new IdentityByteCharMapping();

    private IdentityByteCharMapping() {
    }

    public static IdentityByteCharMapping instance() {
        return INSTANCE;
    }

    @Override
    public void setCharIndex(int byteIndex, int charIndex) {
        throw new UnsupportedOperationException("Identity mapping is immutable");
    }

    @Override
    public int getCharIndex(int byteIndex) {
        return byteIndex;
    }

    @Override
    public int getMappingSize() {
        return 0;
    }
}
