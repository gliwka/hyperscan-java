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
    private PointerByReference scratchReference = new PointerByReference();
    private Pointer scratch;


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
        HyperscanLibrary.INSTANCE.hs_scratch_size(scratch, size);
        return size.getValue().longValue();
    }

    /**
     * scan for a match in a string using a compiled expression database
     * Can only be executed one at a time on a per instance basis
     * @param db Database containing expressions to use for matching
     * @param input String to match against
     * @return List of Matches
     * @throws Throwable Throws if out of memory or platform not supported
     */
    public synchronized List<Match> scan(final Database db, final String input) throws Throwable {
        Pointer dbPointer = db.getPointer();

        int hsError = HyperscanLibrary.INSTANCE.hs_alloc_scratch(dbPointer, scratchReference);

        if(hsError != 0)
            throw new OutOfMemoryError("Not enough memory to allocate scratch space");

        scratch = scratchReference.getValue();

        final LinkedList<Match> matches = new LinkedList<Match>();

        final int[] byteToIndex = Util.utf8ByteIndexesMapping(input);

        HyperscanLibrary.match_event_handler matchHandler = new HyperscanLibrary.match_event_handler() {
            public int invoke(int id, long from, long to, int flags, Pointer context) {
                String match = "";
                Expression matchingExpression = db.getExpression(id);

                if(matchingExpression.getFlags().contains(ExpressionFlag.SOM_LEFTMOST)) {
                    int startIndex = byteToIndex[(int)from];
                    int endIndex = byteToIndex[(int)to - 1];
                    match = input.substring(startIndex, endIndex + 1);
                }

                matches.add(new Match(byteToIndex[(int)from], byteToIndex[(int)to - 1], match, matchingExpression));
                return 0;
            }
        };

        hsError = HyperscanLibrary.INSTANCE.hs_scan(dbPointer, input, input.getBytes().length,
                0, scratch, matchHandler, Pointer.NULL);

        if(hsError != 0)
            throw Util.hsErrorIntToException(hsError);

        return matches;
    }

    @Override
    protected void finalize() {
        HyperscanLibrary.INSTANCE.hs_free_scratch(scratch);
    }
}
