package com.gliwka.hyperscan.jna;

import com.sun.jna.*;

public class SizeT extends IntegerType {
    public SizeT() { this(0); }
    public SizeT(long value) { super(Native.SIZE_T_SIZE, value, true); }
}