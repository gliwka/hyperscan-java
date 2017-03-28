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

    static Throwable hsErrorIntToException(int hsError) {
        switch (hsError) {
            case -1: return new InvalidParameterException();
            case -2: return new OutOfMemoryError("Hyperscan was unable to allocate memory");
            case -11: return new UnsupportedOperationException("Unsupported CPU architecture. At least SSE3 is needed");
            default: return new Exception("Unexpected exception");
        }
    }
}
