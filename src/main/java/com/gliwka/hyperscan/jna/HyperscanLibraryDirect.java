package com.gliwka.hyperscan.jna;

import com.sun.jna.*;

public class HyperscanLibraryDirect {

    public static native int hs_scan(Pointer database, String data, int length, int flags, Pointer scratch, HyperscanLibrary.match_event_handler callback, Pointer context);

    static {
        Native.register("hs");
    }
}