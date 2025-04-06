package com.gliwka.hyperscan.wrapper;

import com.gliwka.hyperscan.wrapper.mapping.ByteCharMapping;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

public class Utf8EncoderTest {

    @Test
    public void testAsciiEncoding() {
        String input = "Hello, world!";
        ByteBuffer buffer = ByteBuffer.allocate(100);
        
        ByteCharMapping mapping = Utf8Encoder.encodeToBufferAndMap(buffer, input);
        
        // ASCII characters are 1 byte each in UTF-8, so byte index equals char index
        assertEquals(input.length(), buffer.limit());
        assertEquals(0, mapping.getCharIndex(0));  // 'H'
        assertEquals(6, mapping.getCharIndex(6));  // ','
        assertEquals(12, mapping.getCharIndex(12)); // '!'
        
        // Verify actual byte values
        byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);
        
        // Check that each byte matches the expected ASCII value
        for (int i = 0; i < input.length(); i++) {
            assertEquals((byte)input.charAt(i), bytes[i], 
                "Byte at index " + i + " should match ASCII value for '" + input.charAt(i) + "'");
        }
    }

    @Test
    public void testThreeByteEncoding() {
        // Japanese character "あ" (HIRAGANA LETTER A) - requires 3 bytes in UTF-8
        String input = "あいう";
        ByteBuffer buffer = ByteBuffer.allocate(100);
        
        ByteCharMapping mapping = Utf8Encoder.encodeToBufferAndMap(buffer, input);
        
        // Each character takes 3 bytes
        assertEquals(9, buffer.limit());
        
        // First character 'あ' - each byte maps back to character index 0
        assertEquals(0, mapping.getCharIndex(0));
        assertEquals(0, mapping.getCharIndex(1));
        assertEquals(0, mapping.getCharIndex(2));
        
        // Second character 'い' - each byte maps back to character index 1
        assertEquals(1, mapping.getCharIndex(3));
        assertEquals(1, mapping.getCharIndex(4));
        assertEquals(1, mapping.getCharIndex(5));
        
        // Third character 'う' - each byte maps back to character index 2
        assertEquals(2, mapping.getCharIndex(6));
        assertEquals(2, mapping.getCharIndex(7));
        assertEquals(2, mapping.getCharIndex(8));
        
        // Verify the actual byte values for UTF-8 encoding
        byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);
        
        // For Japanese 'あ' (U+3042) - UTF-8 bytes should be [0xE3, 0x81, 0x82]
        assertEquals((byte)0xE3, bytes[0], "First byte of 'あ' should be 0xE3");
        assertEquals((byte)0x81, bytes[1], "Second byte of 'あ' should be 0x81");
        assertEquals((byte)0x82, bytes[2], "Third byte of 'あ' should be 0x82");
        
        // For Japanese 'い' (U+3044) - UTF-8 bytes should be [0xE3, 0x81, 0x84]
        assertEquals((byte)0xE3, bytes[3], "First byte of 'い' should be 0xE3");
        assertEquals((byte)0x81, bytes[4], "Second byte of 'い' should be 0x81");
        assertEquals((byte)0x84, bytes[5], "Third byte of 'い' should be 0x84");
        
        // For Japanese 'う' (U+3046) - UTF-8 bytes should be [0xE3, 0x81, 0x86]
        assertEquals((byte)0xE3, bytes[6], "First byte of 'う' should be 0xE3");
        assertEquals((byte)0x81, bytes[7], "Second byte of 'う' should be 0x81");
        assertEquals((byte)0x86, bytes[8], "Third byte of 'う' should be 0x86");
    }

    @Test
    public void testValidSurrogatePair() {
        // "𝄞" (MUSICAL SYMBOL G CLEF) requires a surrogate pair in Java and 4 bytes in UTF-8
        String input = "𝄞";
        ByteBuffer buffer = ByteBuffer.allocate(100);
        
        ByteCharMapping mapping = Utf8Encoder.encodeToBufferAndMap(buffer, input);
        
        // Surrogate pair encodes to 4 bytes in UTF-8
        assertEquals(4, buffer.limit());
        
        // First two bytes map to the high surrogate (char index 0)
        assertEquals(0, mapping.getCharIndex(0));
        assertEquals(0, mapping.getCharIndex(1));
        
        // Last two bytes map to the low surrogate (char index 1)
        assertEquals(1, mapping.getCharIndex(2));
        assertEquals(1, mapping.getCharIndex(3));
        
        // Verify the actual byte values for the 4-byte UTF-8 encoding
        byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);
        
        // Unicode codepoint for 𝄞 is U+1D11E
        // UTF-8 encoding should be [0xF0, 0x9D, 0x84, 0x9E]
        assertEquals((byte)0xF0, bytes[0], "First byte of '𝄞' should be 0xF0");
        assertEquals((byte)0x9D, bytes[1], "Second byte of '𝄞' should be 0x9D");
        assertEquals((byte)0x84, bytes[2], "Third byte of '𝄞' should be 0x84");
        assertEquals((byte)0x9E, bytes[3], "Fourth byte of '𝄞' should be 0x9E");
    }

    @Test
    public void testLonelyHighSurrogateInMiddle() {
        // Create a string with a high surrogate without a matching low surrogate
        String input = "A" + new String(new char[]{0xD800}) + "B"; // 0xD800 is a high surrogate
        ByteBuffer buffer = ByteBuffer.allocate(100);
        
        ByteCharMapping mapping = Utf8Encoder.encodeToBufferAndMap(buffer, input);
        
        // 'A' (1 byte) + '?' (1 byte) + 'B' (1 byte) = 3 bytes
        assertEquals(3, buffer.limit());
        
        // First byte is 'A' at index 0
        assertEquals(0, mapping.getCharIndex(0));
        
        // Second byte is the replacement '?' for the high surrogate at index 1
        assertEquals(1, mapping.getCharIndex(1));
        
        // Third byte is 'B' at index 2
        assertEquals(2, mapping.getCharIndex(2));
        
        // Verify the actual byte values
        byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);
        assertEquals((byte)'A', bytes[0], "First byte should be 'A'");
        assertEquals((byte)'?', bytes[1], "Second byte should be the replacement character '?'");
        assertEquals((byte)'B', bytes[2], "Third byte should be 'B'");
    }

    @Test
    public void testLonelyLowSurrogateInMiddle() {
        // Create a string with a low surrogate without a matching high surrogate
        String input = "A" + new String(new char[]{0xDC00}) + "B"; // 0xDC00 is a low surrogate
        ByteBuffer buffer = ByteBuffer.allocate(100);
        
        ByteCharMapping mapping = Utf8Encoder.encodeToBufferAndMap(buffer, input);
        
        // 'A' (1 byte) + '?' (1 byte) + 'B' (1 byte) = 3 bytes
        assertEquals(3, buffer.limit());
        
        // First byte is 'A' at index 0
        assertEquals(0, mapping.getCharIndex(0));
        
        // Second byte is the replacement '?' for the low surrogate at index 1
        assertEquals(1, mapping.getCharIndex(1));
        
        // Third byte is 'B' at index 2
        assertEquals(2, mapping.getCharIndex(2));
        
        // Verify the actual byte values
        byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);
        assertEquals((byte)'A', bytes[0], "First byte should be 'A'");
        assertEquals((byte)'?', bytes[1], "Second byte should be the replacement character '?'");
        assertEquals((byte)'B', bytes[2], "Third byte should be 'B'");
    }

    @Test
    public void testLonelySurrogateAtEnd() {
        // Create a string with a high surrogate at the end
        String input = "ABC" + new String(new char[]{0xD800}); // 0xD800 is a high surrogate
        ByteBuffer buffer = ByteBuffer.allocate(100);
        
        ByteCharMapping mapping = Utf8Encoder.encodeToBufferAndMap(buffer, input);
        
        // 'A' (1 byte) + 'B' (1 byte) + 'C' (1 byte) + '?' (1 byte) = 4 bytes
        assertEquals(4, buffer.limit());
        
        // First three bytes are 'A', 'B', 'C'
        assertEquals(0, mapping.getCharIndex(0));
        assertEquals(1, mapping.getCharIndex(1));
        assertEquals(2, mapping.getCharIndex(2));
        
        // Fourth byte is the replacement '?' for the high surrogate at index 3
        assertEquals(3, mapping.getCharIndex(3));
        
        // Verify the actual byte values
        byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);
        assertEquals((byte)'A', bytes[0], "First byte should be 'A'");
        assertEquals((byte)'B', bytes[1], "Second byte should be 'B'");
        assertEquals((byte)'C', bytes[2], "Third byte should be 'C'");
        assertEquals((byte)'?', bytes[3], "Fourth byte should be the replacement character '?'");
    }

    @Test
    public void testTwoByteEncoding() {
        // "ñ" (Spanish character) requires 2 bytes in UTF-8
        String input = "ñáé";
        ByteBuffer buffer = ByteBuffer.allocate(100);
        
        ByteCharMapping mapping = Utf8Encoder.encodeToBufferAndMap(buffer, input);
        
        // Each character takes 2 bytes in UTF-8
        assertEquals(6, buffer.limit());
        
        // Verify the mapping - each character spans 2 bytes
        assertEquals(0, mapping.getCharIndex(0)); // First byte of 'ñ'
        assertEquals(0, mapping.getCharIndex(1)); // Second byte of 'ñ'
        assertEquals(1, mapping.getCharIndex(2)); // First byte of 'á'
        assertEquals(1, mapping.getCharIndex(3)); // Second byte of 'á'
        assertEquals(2, mapping.getCharIndex(4)); // First byte of 'é'
        assertEquals(2, mapping.getCharIndex(5)); // Second byte of 'é'
        
        // Verify the actual byte values
        byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);
        
        // For 'ñ' (U+00F1) - UTF-8 bytes should be [0xC3, 0xB1]
        assertEquals((byte)0xC3, bytes[0], "First byte of 'ñ' should be 0xC3");
        assertEquals((byte)0xB1, bytes[1], "Second byte of 'ñ' should be 0xB1");
        
        // For 'á' (U+00E1) - UTF-8 bytes should be [0xC3, 0xA1]
        assertEquals((byte)0xC3, bytes[2], "First byte of 'á' should be 0xC3");
        assertEquals((byte)0xA1, bytes[3], "Second byte of 'á' should be 0xA1");
        
        // For 'é' (U+00E9) - UTF-8 bytes should be [0xC3, 0xA9]
        assertEquals((byte)0xC3, bytes[4], "First byte of 'é' should be 0xC3");
        assertEquals((byte)0xA9, bytes[5], "Second byte of 'é' should be 0xA9");
    }

    @Test
    public void testCombinedString() {
        // String that combines all cases:
        // - ASCII: "Hi "
        // - 3-byte BMP: "世"
        // - Valid surrogate pair: "𝄞"
        // - Lonely high surrogate: 0xD800
        // - ASCII: " "
        // - Lonely low surrogate: 0xDC00
        // - ASCII: " End"
        
        String input = "Hi 世𝄞" + new String(new char[]{0xD800}) + " " + 
                       new String(new char[]{0xDC00}) + " End";
        
        ByteBuffer buffer = ByteBuffer.allocate(100);
        ByteCharMapping mapping = Utf8Encoder.encodeToBufferAndMap(buffer, input);
        
        // Expected byte counts:
        // "Hi " - 3 bytes
        // "世" - 3 bytes
        // "𝄞" - 4 bytes
        // High surrogate - 1 byte (replacement char)
        // " " - 1 byte
        // Low surrogate - 1 byte (replacement char) 
        // " End" - 4 bytes
        // Total: 17 bytes
        assertEquals(17, buffer.limit());
        
        // Check a few key points in the mapping:
        
        // "Hi " (ASCII characters: 1 byte each)
        assertEquals(0, mapping.getCharIndex(0)); // 'H'
        assertEquals(1, mapping.getCharIndex(1)); // 'i'
        assertEquals(2, mapping.getCharIndex(2)); // ' '
        
        // "世" (3 bytes for one character)
        assertEquals(3, mapping.getCharIndex(3)); // First byte of '世'
        assertEquals(3, mapping.getCharIndex(4)); // Second byte of '世'
        assertEquals(3, mapping.getCharIndex(5)); // Third byte of '世'
        
        // "𝄞" (4 bytes total for surrogate pair at indices 4-5)
        assertEquals(4, mapping.getCharIndex(6)); // First byte, maps to high surrogate
        assertEquals(4, mapping.getCharIndex(7)); // Second byte, maps to high surrogate
        assertEquals(5, mapping.getCharIndex(8)); // Third byte, maps to low surrogate
        assertEquals(5, mapping.getCharIndex(9)); // Fourth byte, maps to low surrogate
        
        // Lone high surrogate at index 6 (1 byte for replacement char)
        assertEquals(6, mapping.getCharIndex(10)); // Replacement char for high surrogate
        
        // " " (ASCII space: 1 byte)
        assertEquals(7, mapping.getCharIndex(11));
        
        // Lone low surrogate at index 8 (1 byte for replacement char)
        assertEquals(8, mapping.getCharIndex(12));
        
        // " End" (ASCII: 1 byte each)
        assertEquals(9, mapping.getCharIndex(13));
        assertEquals(10, mapping.getCharIndex(14));
        assertEquals(11, mapping.getCharIndex(15));
        assertEquals(12, mapping.getCharIndex(16));
        
        // Verify the actual byte values
        byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);
        
        // ASCII bytes
        assertEquals((byte)'H', bytes[0]);
        assertEquals((byte)'i', bytes[1]);
        assertEquals((byte)' ', bytes[2]);
        
        // '世' (U+4E16) - UTF-8 bytes should be [0xE4, 0xB8, 0x96]
        assertEquals((byte)0xE4, bytes[3]);
        assertEquals((byte)0xB8, bytes[4]);
        assertEquals((byte)0x96, bytes[5]);
        
        // '𝄞' (U+1D11E) - UTF-8 bytes should be [0xF0, 0x9D, 0x84, 0x9E]
        assertEquals((byte)0xF0, bytes[6]);
        assertEquals((byte)0x9D, bytes[7]);
        assertEquals((byte)0x84, bytes[8]);
        assertEquals((byte)0x9E, bytes[9]);
        
        // Replacement char for high surrogate
        assertEquals((byte)'?', bytes[10]);
        
        // ASCII space
        assertEquals((byte)' ', bytes[11]);
        
        // Replacement char for low surrogate
        assertEquals((byte)'?', bytes[12]);
        
        // ASCII " End"
        assertEquals((byte)' ', bytes[13]);
        assertEquals((byte)'E', bytes[14]);
        assertEquals((byte)'n', bytes[15]);
        assertEquals((byte)'d', bytes[16]);
    }
}
