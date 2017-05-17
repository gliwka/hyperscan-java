package com.gliwka.hyperscan.wrapper;

import java.security.InvalidParameterException;
import java.util.EnumSet;

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
        int sum = 0;
        for (int i = 0; i < s.length(); i++) {
            byteIndexes[sum] = i;
            int c = s.codePointAt(i);
            if (Character.charCount(c) == 2) {
                i++;
            }
            if (c <=     0x7F) sum += 1; else
            if (c <=    0x7FF) sum += 2; else
            if (c <=   0xFFFF) sum += 3; else
            if (c <= 0x1FFFFF) sum += 4; else
                throw new Error();
        }
        return byteIndexes;
    }

    static Throwable hsErrorIntToException(int hsError) {
        switch (hsError) {
            case -1: return new InvalidParameterException();
            case -2: return new OutOfMemoryError("Hyperscan was unable to allocate memory");
            case -11: return new UnsupportedOperationException("Unsupported CPU architecture. At least SSE3 is needed");
            default: return new Exception("Unexpected exception");
        }
    }
}
