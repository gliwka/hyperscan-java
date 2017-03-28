package com.gliwka.hyperscan.jna;

import com.sun.jna.*;
import com.sun.jna.ptr.ByReference;

public class SizeTByReference extends ByReference {
    public SizeTByReference() {
        this(new SizeT());
    }

    public SizeTByReference(SizeT value) {
        super(Native.SIZE_T_SIZE);
        setValue(value);
    }

    public void setValue(SizeT value) {
        Pointer p = getPointer();
        if (Native.SIZE_T_SIZE == 8) {
            p.setLong(0, value.longValue());
        } else {
            p.setInt(0, value.intValue());
        }
    }

    public SizeT getValue() {
        Pointer p = getPointer();
        return new SizeT(Native.SIZE_T_SIZE == 8 ? p.getLong(0) : p.getInt(0));
    }
}