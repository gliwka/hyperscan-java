package com.gliwka.hyperscan.wrapper.mapping;

/**
 * ByteCharMapping implementation using a short array.
 * Stores character indices as unsigned shorts (0-65535).
 */
public class ShortMapping implements ByteCharMapping {
    private final short[] mapping;

    ShortMapping(int bufferSize) {
        this.mapping = new short[bufferSize];
    }

    @Override
    public void setCharIndex(int byteIndex, int charIndex) {
        if (charIndex > 65535 || charIndex < 0) {
            throw new IllegalArgumentException("Character index " + charIndex + " out of bounds for short mapping (0-65535)");
        }
        // Store as unsigned short
        this.mapping[byteIndex] = (short) charIndex;
    }

    @Override
    public int getCharIndex(int byteIndex) {
        // Interpret stored short as unsigned
        return Short.toUnsignedInt(this.mapping[byteIndex]);
    }

     @Override
    public int getMappingSize() {
        return mapping.length;
    }
}
