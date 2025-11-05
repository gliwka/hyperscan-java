package com.gliwka.hyperscan.wrapper.mapping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

class ByteCharMappingTest {

    @Test
    void testFactoryCreation() {
        // Test ByteMapping creation (0-255 range)
        ByteCharMapping byteMap = ByteCharMapping.create(10, 255);
        assertTrue(byteMap instanceof ByteMapping);

        // Test ShortMapping creation (256-65535 range)
        ByteCharMapping shortMap = ByteCharMapping.create(10, 256);
        assertTrue(shortMap instanceof ShortMapping);

        // Test IntMapping creation (>65535 range)
        ByteCharMapping intMap = ByteCharMapping.create(10, 65536);
        assertTrue(intMap instanceof IntMapping);
    }

    @Test
    void testByteMapping() {
        ByteCharMapping mapping = ByteCharMapping.create(5, 255);
        
        // Test valid range
        mapping.setCharIndex(0, 0);     // Min value
        mapping.setCharIndex(1, 127);   // Middle value
        mapping.setCharIndex(2, 255);   // Max value

        assertEquals(0, mapping.getCharIndex(0));
        assertEquals(127, mapping.getCharIndex(1));
        assertEquals(255, mapping.getCharIndex(2));
        assertEquals(5, mapping.getMappingSize());

        // Test out of bounds
        assertThrows(IllegalArgumentException.class, () -> mapping.setCharIndex(0, 256));
        assertThrows(IllegalArgumentException.class, () -> mapping.setCharIndex(0, -1));
    }

    @Test
    void testShortMapping() {
        ByteCharMapping mapping = ByteCharMapping.create(5, 65535);
        
        // Test valid range
        mapping.setCharIndex(0, 0);      // Min value
        mapping.setCharIndex(1, 256);    // Above byte range
        mapping.setCharIndex(2, 32767);  // Middle value
        mapping.setCharIndex(3, 65535);  // Max value

        assertEquals(0, mapping.getCharIndex(0));
        assertEquals(256, mapping.getCharIndex(1));
        assertEquals(32767, mapping.getCharIndex(2));
        assertEquals(65535, mapping.getCharIndex(3));
        assertEquals(5, mapping.getMappingSize());

        // Test out of bounds
        assertThrows(IllegalArgumentException.class, () -> mapping.setCharIndex(0, 65536));
        assertThrows(IllegalArgumentException.class, () -> mapping.setCharIndex(0, -1));
    }

    @Test
    void testIntMapping() {
        ByteCharMapping mapping = ByteCharMapping.create(5, Integer.MAX_VALUE);
        
        // Test valid range
        mapping.setCharIndex(0, 0);                  // Min value
        mapping.setCharIndex(1, 65536);             // Above short range
        mapping.setCharIndex(2, Integer.MAX_VALUE);  // Max value

        assertEquals(0, mapping.getCharIndex(0));
        assertEquals(65536, mapping.getCharIndex(1));
        assertEquals(Integer.MAX_VALUE, mapping.getCharIndex(2));
        assertEquals(5, mapping.getMappingSize());

        // Test out of bounds
        assertThrows(IllegalArgumentException.class, () -> mapping.setCharIndex(0, -1));
    }

    @Test
    void testArrayBounds() {
        ByteCharMapping mapping = ByteCharMapping.create(3, 255);

        // Test valid index
        mapping.setCharIndex(0, 42);
        mapping.setCharIndex(2, 42);

        // Test invalid indices
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> mapping.setCharIndex(-1, 42));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> mapping.setCharIndex(3, 42));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> mapping.getCharIndex(-1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> mapping.getCharIndex(3));
    }

    @Test
    void testInvalidFactoryParameters() {
        // Test negative character index
        assertThrows(IllegalArgumentException.class, () -> ByteCharMapping.create(10, -1));
    }
}