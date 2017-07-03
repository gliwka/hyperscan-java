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

    static int[] utf8ByteIndexesMapping(String s) {
        int[] byteIndexes = new int[s.getBytes().length];
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
            case -11: return new UnsupportedOperationException("Unsupported CPU architecture. At least SSE3 is needed");
            default: return new Exception("Unexpected exception");
        }
    }
}
