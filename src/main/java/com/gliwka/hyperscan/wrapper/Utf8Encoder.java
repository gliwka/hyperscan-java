/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

// Encoding ripped of from here https://github.com/netty/netty/blob/fa94493e4d9809f3d489edbc0bd1c28eb6a4d71c/buffer/src/main/java/io/netty/buffer/ByteBufUtil.java#L960

package com.gliwka.hyperscan.wrapper;

import com.gliwka.hyperscan.wrapper.mapping.ByteCharMapping;

import java.nio.Buffer;
import java.nio.ByteBuffer;

import static java.lang.Character.isSurrogate;

/**
 * Encode UTF-8 string and create a mapping from bytes back to string
 * at the same time. UTF-8 is being done manually to be able to do it in a single pass and
 * have a reliable mapping back to the string position
 */
class Utf8Encoder {
    private static final byte WRITE_UTF_UNKNOWN = '?';
    private static final int UTF8_1_BYTE_LIMIT = 0x80;  // Max char code for 1-byte UTF-8 (exclusive)
    private static final int UTF8_2_BYTE_LIMIT = 0x800; // Max char code for 2-byte UTF-8 (exclusive)

    /**
     * Encodes a Java String to a direct ByteBuffer containing UTF-8 bytes
     * and creates a mapping from byte index to character index.
     *
     * @param buffer The ByteBuffer to write the UTF-8 bytes to
     * @param string The Java String to encode
     * @return An array of int values representing the mapping from byte index to character index
     */
    static ByteCharMapping encodeToBufferAndMap(ByteBuffer buffer, String string) {
        ByteCharMapping mapping = ByteCharMapping.create(buffer.capacity(), string.length());

        int writerIndex = 0;
        int end = string.length();
        for (int i = 0; i < end; i++) {
            char c = string.charAt(i);
            if (c < UTF8_1_BYTE_LIMIT) {
                mapping.setCharIndex(writerIndex++, i);
                buffer.put((byte) c);
            } else if (c < UTF8_2_BYTE_LIMIT) {
                mapping.setCharIndex(writerIndex++, i);
                buffer.put((byte) (0xc0 | (c >> 6)));
                mapping.setCharIndex(writerIndex++, i);
                buffer.put((byte) (0x80 | (c & 0x3f)));
            } else if (isSurrogate(c)) {
                if (!Character.isHighSurrogate(c)) {
                    mapping.setCharIndex(writerIndex++, i);
                    buffer.put(WRITE_UTF_UNKNOWN);
                    continue;
                }
                // Surrogate Pair consumes 2 characters.
                int firstSurrogate = i; // Save original position for mapping
                if (++i == end) {
                    mapping.setCharIndex(writerIndex++, firstSurrogate);
                    buffer.put(WRITE_UTF_UNKNOWN);
                    break;
                }
                // Extra method is copied here to NOT allow inlining of writeUtf8
                // and increase the chance to inline CharSequence::charAt instead
                char c2 = string.charAt(i);
                if (!Character.isLowSurrogate(c2)) {
                    mapping.setCharIndex(writerIndex++, firstSurrogate);
                    buffer.put(WRITE_UTF_UNKNOWN);
                    mapping.setCharIndex(writerIndex++, i);
                    buffer.put(Character.isHighSurrogate(c2)? WRITE_UTF_UNKNOWN : (byte) c2);
                } else {
                    int codePoint = Character.toCodePoint(c, c2);
                    // See https://www.unicode.org/versions/Unicode7.0.0/ch03.pdf#G2630.
                    mapping.setCharIndex(writerIndex++, firstSurrogate);
                    buffer.put((byte) (0xf0 | (codePoint >> 18)));
                    mapping.setCharIndex(writerIndex++, firstSurrogate);
                    buffer.put((byte) (0x80 | ((codePoint >> 12) & 0x3f)));
                    mapping.setCharIndex(writerIndex++, i);
                    buffer.put((byte) (0x80 | ((codePoint >> 6) & 0x3f)));
                    mapping.setCharIndex(writerIndex++, i);
                    buffer.put((byte) (0x80 | (codePoint & 0x3f)));
                }
            } else {
                // 3-byte UTF-8 encoding for non-surrogate characters
                mapping.setCharIndex(writerIndex++, i);
                buffer.put((byte) (0xe0 | ((c >> 12) & 0xf))); // Mask high bits
                mapping.setCharIndex(writerIndex++, i);
                buffer.put((byte) (0x80 | ((c >> 6) & 0x3f)));
                mapping.setCharIndex(writerIndex++, i);
                buffer.put((byte) (0x80 | (c & 0x3f)));
            }
        }

        // Make JDK9+ compile for JDK 8
        ((Buffer)buffer).flip();
        return mapping;
    }
}