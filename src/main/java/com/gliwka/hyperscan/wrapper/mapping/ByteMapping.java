package com.gliwka.hyperscan.wrapper.mapping;

/**
 * ByteCharMapping implementation using a byte array.
 * Stores character indices as unsigned bytes (0-255).
 */
public class ByteMapping implements ByteCharMapping {
    private final byte[] mapping;

    ByteMapping(int bufferSize) {
        this.mapping = new byte[bufferSize];
    }

    @Override
    public void setCharIndex(int byteIndex, int charIndex) {
        if (charIndex > 255 || charIndex < 0) {
            throw new IllegalArgumentException("Character index " + charIndex + " out of bounds for byte mapping (0-255)");
        }
        // Store as unsigned byte
        this.mapping[byteIndex] = (byte) charIndex;
    }

    @Override
    public int getCharIndex(int byteIndex) {
        // Interpret stored byte as unsigned
        return Byte.toUnsignedInt(this.mapping[byteIndex]);
    }

    @Override
    public int getMappingSize() {
        return mapping.length;
    }
}
