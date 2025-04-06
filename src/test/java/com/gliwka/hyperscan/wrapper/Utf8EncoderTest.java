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
        
        // Verify the replacement character was used for the high surrogate
        byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);
        assertEquals('?', bytes[1]);
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
        
        // Verify the replacement character was used for the low surrogate
        byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);
        assertEquals('?', bytes[1]);
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
        
        // Verify the replacement character was used for the high surrogate
        byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);
        assertEquals('?', bytes[3]);
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
        assertEquals(8, mapping.getCharIndex(12)); // Replacement char for low surrogate
        
        // " End" (ASCII characters: 1 byte each)
        assertEquals(9, mapping.getCharIndex(13)); // ' '
        assertEquals(10, mapping.getCharIndex(14)); // 'E'
        assertEquals(11, mapping.getCharIndex(15)); // 'n'
        assertEquals(12, mapping.getCharIndex(16)); // 'd'
    }
}
