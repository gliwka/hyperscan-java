package com.gliwka.hyperscan.jna;

import com.sun.jna.*;

import java.util.Arrays;
import java.util.List;

public class CompileErrorStruct extends Structure {

    public String message;
    public int expression;

    public CompileErrorStruct() {
        super();
    }

    public CompileErrorStruct(Pointer p) {
        super(p);
        this.read();
    }

    protected List<String> getFieldOrder() {
        return Arrays.asList("message", "expression");
    }
}
