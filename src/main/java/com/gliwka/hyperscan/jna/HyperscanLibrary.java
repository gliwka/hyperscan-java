package com.gliwka.hyperscan.jna;

import java.util.Map;
import java.util.HashMap;
import com.sun.jna.*;
import com.sun.jna.ptr.PointerByReference;


public interface HyperscanLibrary extends Library {
    Map opts = new HashMap() { {
        put(OPTION_STRING_ENCODING, "UTF-8");
    }};

    HyperscanLibrary INSTANCE = (HyperscanLibrary) Native.loadLibrary("hs", HyperscanLibrary.class, opts);

    String hs_version();

    int hs_valid_platform();

    int hs_free_database(Pointer database);

    int hs_expression_info(String expression, int flags, PointerByReference info, PointerByReference error);

    int hs_serialize_database(Pointer database, PointerByReference bytes, SizeTByReference length);

    int hs_desezialize_database(Byte[] bytes, SizeT length, PointerByReference db);

    int hs_database_size(Pointer database, SizeTByReference database_size);

    int hs_database_info(Pointer database, PointerByReference info);

    int hs_compile(String expression, int flags, int mode, Pointer platform, PointerByReference database,
                   PointerByReference error);

    int hs_compile_multi(String[] expressions, int[] flags, int[] ids, int elements, int mode, Pointer platform,
                         PointerByReference database, PointerByReference error);

    int hs_compile_ext_multi(String[] expressions, int[] flags, int[] ids, PatternBehaviourStruct[] ext, int elements,
                             int mode, Pointer platform, PointerByReference database, PointerByReference error);

    int hs_free_compile_error(CompileErrorStruct error);

    int hs_alloc_scratch(Pointer database, PointerByReference scratch);

    int hs_free_scratch(Pointer scratch);

    int hs_scratch_size(Pointer scratch, SizeTByReference scratch_size);

    interface match_event_handler extends Callback {
        int invoke(int id, long from, long to, int flags, Pointer context);
    }

    int hs_scan(Pointer database, String data, int length, int flags, Pointer scratch, match_event_handler callback, Pointer context);
}