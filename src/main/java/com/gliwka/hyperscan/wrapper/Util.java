package com.gliwka.hyperscan.wrapper;

import java.security.InvalidParameterException;
import java.util.EnumSet;
import java.util.Arrays;

class Util {
    static int bitEnumSetToInt(EnumSet enumSet) {
        int bitValue = 0;
        for(Object e : enumSet) {
            if(e instanceof BitFlag) {
                bitValue = ((BitFlag) e).getBits() | bitValue;
            }
            else {
                throw new InvalidParameterException();
            }
        }

        return bitValue;
    }

    static int[] utf8ByteIndexesMapping(String s, int bytesLength) {
        int[] byteIndexes = new int[bytesLength];
        int currentByte = 0;

        for (int stringPosition = 0; stringPosition < s.length(); stringPosition++) {
            int c = s.codePointAt(stringPosition);

            int unicodeCharLength;

            if (c <=     0x7F) unicodeCharLength = 1; else
            if (c <=    0x7FF) unicodeCharLength = 2; else
            if (c <=   0xFFFF) unicodeCharLength = 3; else
            if (c <= 0x1FFFFF) unicodeCharLength = 4; else
                throw new Error();

            Arrays.fill(byteIndexes, currentByte, currentByte + unicodeCharLength, stringPosition);

            currentByte += unicodeCharLength;

            if (Character.charCount(c) == 2) {
                stringPosition++;
            }
        }

        return byteIndexes;
    }

    static Throwable hsErrorIntToException(int hsError) {
        switch (hsError) {
            case -1: return new InvalidParameterException("An invalid parameter has been passed. Is scratch allocated?");
            case -2: return new OutOfMemoryError("Hyperscan was unable to allocate memory");
            case -3: return new Exception("The engine was terminated by callback.");
            case -4: return new Exception("The pattern compiler failed.");
            case -5: return new Exception("The given database was built for a different version of Hyperscan.");
            case -6: return new Exception("The given database was built for a different platform.");
            case -7: return new Exception("The given database was built for a different mode of operation.");
            case -8: return new Exception("A parameter passed to this function was not correctly aligned.");
            case -9: return new Exception("The allocator did not return memory suitably aligned for the largest representable data type on this platform.");
            case -10: return new Exception("The scratch region was already in use.");
            case -11: return new UnsupportedOperationException("Unsupported CPU architecture. At least SSE3 is needed");
            case -12: return new Exception("Provided buffer was too small.");
            default: return new Exception("Unexpected error: " + hsError);
        }
    }
}
