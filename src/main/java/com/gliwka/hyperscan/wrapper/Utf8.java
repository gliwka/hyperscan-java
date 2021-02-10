package com.gliwka.hyperscan.wrapper;

import java.util.Arrays;

class Utf8 {
    static int[] byteToStringPositionMap(String s, int bytesLength) {
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
}
