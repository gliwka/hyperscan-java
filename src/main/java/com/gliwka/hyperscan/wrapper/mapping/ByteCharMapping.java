package com.gliwka.hyperscan.wrapper.mapping;

/**
 * Interface for mapping byte indices to character indices.
 * Implementations can use different underlying array types for memory use optimization.
 */
public interface ByteCharMapping {

    /**
     * Sets the character index for a given byte index.
     *
     * @param byteIndex The byte index.
     * @param charIndex The character index.
     */
    void setCharIndex(int byteIndex, int charIndex);

    /**
     * Gets the character index corresponding to the given byte index.
     *
     * @param byteIndex The byte index.
     * @return The character index.
     */
    int getCharIndex(int byteIndex);

    /**
     * Returns the size of the underlying mapping (number of byte indices mapped).
     *
     * @return The size of the mapping.
     */
    int getMappingSize();

    /**
     * Factory method to create the most memory-efficient mapping based on the maximum
     * character index needed and the total number of bytes in the encoded string.
     *
     * @param bufferSize   The size of the byte buffer (total number of bytes).
     * @param maxCharIndex The maximum character index that needs to be stored (typically string.length() - 1).
     * @return An appropriate ByteCharMapping implementation.
     */
    static ByteCharMapping create(int bufferSize, int maxCharIndex) {
        if (maxCharIndex < 0) {
            throw new IllegalArgumentException("Character limit can't be negative");
        }

        if (maxCharIndex <= 255) { // Unsigned byte range
            return new ByteMapping(bufferSize);
        } else if (maxCharIndex <= 65535) { // Unsigned short range
            return new ShortMapping(bufferSize);
        } else {
            return new IntMapping(bufferSize);
        }
    }
}
