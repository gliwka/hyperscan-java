package com.gliwka.hyperscan.jna;

import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;

public class PatternBehaviourStruct extends Structure {

    public long flags;
    public long min_offset;
    public long max_offset;
    public long min_length;

    protected List<String> getFieldOrder() {
        return Arrays.asList("flags", "min_offset", "max_offset", "min_length");
    }

}