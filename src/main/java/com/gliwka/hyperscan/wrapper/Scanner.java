package com.gliwka.hyperscan.wrapper;

import com.sun.jna.*;
import com.gliwka.hyperscan.jna.*;
import com.sun.jna.ptr.PointerByReference;

import java.util.LinkedList;
import java.util.List;

/**
 * Scanner, can be used with databases to scan for expressions in input string
 * In case of multithreaded scanning, you need one scanner instance per thread.
 */
public class Scanner {
    private PointerByReference scratch = new PointerByReference();


    /**
     * Check if the hardware platform is supported
     * @return true if supported, otherwise false
     */
    public static boolean getIsValidPlatform() {
        return HyperscanLibrary.INSTANCE.hs_valid_platform() == 0;
    }


    /**
     * Get the version information for the underlying hyperscan library
     * @return version string
     */
    public static String getVersion() {
        return HyperscanLibrary.INSTANCE.hs_version();
    }

    /**
     * Get the scratch space size in bytes
     * @return count of bytes
     */
    public long getSize() {
        SizeTByReference size = new SizeTByReference();
        HyperscanLibrary.INSTANCE.hs_scratch_size(scratch.getValue(), size);
        return size.getValue().longValue();
    }

    /**
     * Scan for a match in a string using a compiled expression database
     * Can only be executed one at a time on a per instance basis
     * @param db Database containing expressions to use for matching
     * @param input String to match against
     * @return List of Matches
     * @throws Throwable Throws if out of memory or platform not supported
     */
    public synchronized List<Match> Scan(final Database db, final String input) throws Throwable {
        Pointer dbPointer = db.getPointer();

        int hsError = HyperscanLibrary.INSTANCE.hs_alloc_scratch(dbPointer, scratch);

        if(hsError != 0)
            throw new OutOfMemoryError("Not enough memory to allocate scratch space");

        final LinkedList<Match> matches = new LinkedList<Match>();

        HyperscanLibrary.match_event_handler matchHandler = new HyperscanLibrary.match_event_handler() {
            public int invoke(int id, long from, long to, int flags, Pointer context) {
                String match = "";
                Expression matchingExpression = db.getExpression(id);

                if(matchingExpression.getFlags().contains(ExpressionFlag.SOM_LEFTMOST)) {
                    match = input.substring((int)from, (int)to);
                }

                matches.add(new Match(from, to, match, matchingExpression));
                return 0;
            }
        };

        hsError = HyperscanLibrary.INSTANCE.hs_scan(dbPointer, input, input.getBytes().length,
                0, scratch.getValue(), matchHandler, Pointer.NULL);

        if(hsError != 0)
            throw Util.hsErrorIntToException(hsError);

        return matches;
    }

    @Override
    protected void finalize() {
        HyperscanLibrary.INSTANCE.hs_free_scratch(scratch.getValue());
    }
}
