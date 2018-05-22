package com.gliwka.hyperscan.jna;

import com.sun.jna.*;

import java.util.HashMap;

public class HyperscanLibraryDirect {

    public static native int hs_scan(Pointer database, String data, int length, int flags, Pointer scratch, HyperscanLibrary.match_event_handler callback, Pointer context);

    static {
        HashMap<String,Object> opts = new HashMap<>();
        opts.put(Library.OPTION_STRING_ENCODING, "UTF-8");

        Native.register(NativeLibrary.getInstance("hs", opts));
    }
}