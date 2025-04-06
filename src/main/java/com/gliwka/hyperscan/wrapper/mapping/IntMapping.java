package com.gliwka.hyperscan.wrapper.mapping;

/**
 * ByteCharMapping implementation using an int array.
 */
public class IntMapping implements ByteCharMapping {
    private final int[] mapping;

    IntMapping(int bufferSize) {
        this.mapping = new int[bufferSize];
    }

    @Override
    public void setCharIndex(int byteIndex, int charIndex) {
         if (charIndex < 0) {
            // Should not happen with standard String lengths, but good practice
            throw new IllegalArgumentException("Character index " + charIndex + " cannot be negative.");
        }
        this.mapping[byteIndex] = charIndex;
    }

    @Override
    public int getCharIndex(int byteIndex) {
        return this.mapping[byteIndex];
    }

    @Override
    public int getMappingSize() {
        return mapping.length;
    }
}
