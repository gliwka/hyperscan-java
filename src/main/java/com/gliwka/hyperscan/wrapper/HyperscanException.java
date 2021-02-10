package com.gliwka.hyperscan.wrapper;

import static com.gliwka.hyperscan.jni.hyperscan.*;

public class HyperscanException extends RuntimeException {
    public HyperscanException(String message) {
        super(message);
    }

    static HyperscanException hsErrorToException(int hsError) {
        switch (hsError) {
            case HS_INVALID:  return new HyperscanException("An invalid parameter has been passed. Is scratch allocated?");
            case HS_NOMEM:  return new HyperscanException("Hyperscan was unable to allocate memory");
            case HS_SCAN_TERMINATED:  return new HyperscanException("The engine was terminated by callback.");
            case HS_COMPILER_ERROR:  return new HyperscanException("The pattern compiler failed.");
            case HS_DB_VERSION_ERROR:  return new HyperscanException("The given database was built for a different version of Hyperscan.");
            case HS_DB_PLATFORM_ERROR:  return new HyperscanException("The given database was built for a different platform.");
            case HS_DB_MODE_ERROR:  return new HyperscanException("The given database was built for a different mode of operation.");
            case HS_BAD_ALIGN:  return new HyperscanException("A parameter passed to this function was not correctly aligned.");
            case HS_BAD_ALLOC:  return new HyperscanException("The allocator did not return memory suitably aligned for the largest representable data type on this platform.");
            case HS_SCRATCH_IN_USE: return new HyperscanException("The scratch region was already in use.");
            case HS_ARCH_ERROR: return new HyperscanException("Unsupported CPU architecture. At least SSE3 is needed");
            case HS_INSUFFICIENT_SPACE: return new HyperscanException("Provided buffer was too small.");
            default:  return new HyperscanException("Unexpected error: " + hsError);
        }
    }
}
