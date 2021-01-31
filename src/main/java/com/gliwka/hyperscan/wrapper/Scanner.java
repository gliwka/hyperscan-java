package com.gliwka.hyperscan.wrapper;

import com.sun.jna.*;
import com.gliwka.hyperscan.jna.*;
import com.sun.jna.ptr.PointerByReference;
import java.io.*;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Scanner, can be used with databases to scan for expressions in input string
 * In case of multithreaded scanning, you need one scanner instance per thread.
 */
public class Scanner implements Closeable {
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
        if(scratch == null) {
            throw new IllegalStateException("Scratch space has alredy been deallocated");
        }
        
        SizeTByReference size = new SizeTByReference();
        HyperscanLibrary.INSTANCE.hs_scratch_size(scratch, size);
        return size.getValue().longValue();
    }

    /**
     * Allocate a scratch space.  Must be called at least once with each
     * database that will be used before scan is called.
     * @param db Database containing expressions to use for matching
     */
    public void allocScratch(final Database db) {
        Pointer dbPointer = db.getPointer();

        if(scratchReference == null) {
            scratchReference = new PointerByReference();
        }

        int hsError = HyperscanLibrary.INSTANCE.hs_alloc_scratch(dbPointer, scratchReference);

        if(hsError != 0)
            throw Util.hsErrorIntToException(hsError);

        scratch = scratchReference.getValue();
    }

    private LinkedList<long[]> matchedIds = new LinkedList<>();
    private List<Match> noMatches = Collections.emptyList();

    private HyperscanLibrary.match_event_handler matchHandler = new HyperscanLibrary.match_event_handler() {
        public int invoke(int id, long from, long to, int flags, Pointer context) {
            long[] tuple = { id, from, to };
            matchedIds.add(tuple);
            return 0;
        }
    };

    /**
     * scan for a match in a string using a compiled expression database
     * Can only be executed one at a time on a per instance basis
     * @param db Database containing expressions to use for matching
     * @param input String to match against
     * @return List of Matches
     */
    public List<Match> scan(final Database db, final String input) {
        Pointer dbPointer = db.getPointer();

        final byte[] utf8bytes = input.getBytes(StandardCharsets.UTF_8);
        final int bytesLength = utf8bytes.length;

        matchedIds.clear();
        int hsError = HyperscanLibraryDirect.hs_scan(dbPointer, input, bytesLength,
                0, scratch, matchHandler, Pointer.NULL);

        if(hsError != 0)
            throw Util.hsErrorIntToException(hsError);

        if(matchedIds.isEmpty())
            return noMatches;

        final int[] byteToIndex = Util.utf8ByteIndexesMapping(input, bytesLength);
        final LinkedList<Match> matches = new LinkedList<Match>();
        matchedIds.forEach( tuple -> {
            int id = (int)tuple[0];
            long from = tuple[1];
            long to = tuple[2] < 1 ? 1 : tuple[2]; //prevent index out of bound exception later
            String match = "";
            Expression matchingExpression = db.getExpression(id);

            if(matchingExpression.getFlags().contains(ExpressionFlag.SOM_LEFTMOST)) {
                int startIndex = byteToIndex[(int)from];
                int endIndex = byteToIndex[(int)to - 1];
                match = input.substring(startIndex, endIndex + 1);
            }

            if (byteToIndex.length > 0) {
                matches.add(new Match(byteToIndex[(int) from], byteToIndex[(int) to - 1], match, matchingExpression));
            } else {
                matches.add(new Match(0, 0, match, matchingExpression));
            }
        });

        return matches;
    }

    @Override
    protected void finalize() {
        //check and setting scratch pointer to null to avoid double free
        if(scratch != null) {
            HyperscanLibrary.INSTANCE.hs_free_scratch(scratch);
            scratch = null;
            scratchReference = null;
        }
    }

    @Override
    public void close() throws IOException {
        this.finalize();
    }
}
