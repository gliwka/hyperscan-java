package com.gliwka.hyperscan.wrapper;

import com.gliwka.hyperscan.jni.hs_database_t;
import com.gliwka.hyperscan.jni.hs_scratch_t;
import com.gliwka.hyperscan.jni.match_event_handler;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.SizeTPointer;

import java.io.Closeable;
import java.io.IOException;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import static com.gliwka.hyperscan.jni.hyperscan.*;
import static java.util.Collections.emptyList;

/**
 * Scanner, can be used with databases to scan for expressions in input string
 * Not thread-safe, so no concurrent usage. Ideally create one per thread.
 *
 * @see Database
 * @see Expression
 * @see Match
 */
public class Scanner implements Closeable {
    private static final ThreadLocal<RawMatchEventHandler> activeCallback = new ThreadLocal<>();

    private static class NativeScratch extends hs_scratch_t {
        void registerDeallocator() {
            if (deallocator() != null) {
                hs_scratch_t p = new hs_scratch_t(this);
                deallocator(() -> hs_free_scratch(p));
            }
        }
    }

    private NativeScratch scratch = new NativeScratch();

    /**
     * Creates a new Scanner instance.
     * Each scanner maintains its own scratch space which needs to be allocated
     * with the {@link #allocScratch(Database)} method before scanning.
     */
    public Scanner() {
        // Default constructor with initialized scratch space
    }

    /**
     * Check if the hardware platform is supported
     * @return true if supported, otherwise false
     */
    public static boolean getIsValidPlatform() {
        return hs_valid_platform() == 0;
    }


    /**
     * Get the version in  formation for the underlying hyperscan library
     * @return version string
     */
    public static String getVersion() {
        return hs_version().getString();
    }

    /**
     * Get the scratch space size in bytes
     * @return count of bytes
     */
    public long getSize() {
        if(scratch == null) {
            throw new IllegalStateException("Scratch space has alredy been deallocated");
        }

        try(SizeTPointer size = new SizeTPointer(1)) {
            hs_scratch_size(scratch, size);
            return size.get();
        }
    }

    /**
     * Allocate a scratch space.  Must be called at least once with each
     * database that will be used before scan is called.
     *
     * @param db Database containing expressions to use for matching
     */
    public void allocScratch(final Database db) {
        if(scratch == null) {
            throw new IllegalStateException("Scratch space has already been deallocated");
        }

        hs_database_t dbPointer = db.getDatabase();
        int hsError = hs_alloc_scratch(dbPointer, scratch);
        scratch.registerDeallocator();

        if(hsError != 0) {
            throw HyperscanException.hsErrorToException(hsError);
        }
    }

    private static final match_event_handler matchHandler = new match_event_handler() {
        public int call(int id, long from, long to, int flags, Pointer context) {
            RawMatchEventHandler handler = activeCallback.get();
            // terminate further matching on false (negative return value in hs)
            return handler.onMatch(id, from, to, flags) ? 0 : -1;
        }
    };

    /**
     * Scans a  string for matches using a compiled expression database and returns a list of matches.
     * Can only be executed one at a time on a per-instance basis.
     *
     * @param db    Database containing expressions to use for matching.
     * @param input String to match against.
     * @return List of Matches
     */
    public List<Match> scan(final Database db, final String input) {
        final LinkedList<Match> matches = new LinkedList<>();

        scan(db, input, (expression, fromStringIndexLong, toStringIndexLong) -> {
            String match = "";
            if(expression.getFlags().contains(ExpressionFlag.SOM_LEFTMOST)) {
                match = input.substring((int)  fromStringIndexLong, (int) toStringIndexLong + 1);
            }

            matches.add(new Match((int)fromStringIndexLong, (int)toStringIndexLong, match, expression));
            return true;
        });

        return matches.isEmpty() ? emptyList() : matches;
    }

    /**
     * Scans a string for matches using a compiled expression database and reports
     * matches to the provided event handler using string character indices.
     * Can only be executed one at a time on a per-instance basis.
     *
     * @param db           Database containing expressions to use for matching.
     * @param input        String to match against.
     * @param eventHandler Handler to receive match events with string indices.
     */
    public void scan(final Database db, final String input, StringMatchEventHandler eventHandler) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(input.length() * 4);
        final int[] mapping = Utf8Encoder.encodeToBufferAndMap(byteBuffer, input);

        scan(db, byteBuffer, (expressionId, fromByteIdx, toByteIdx, flags) -> {
            Expression expression = db.getExpression(expressionId);
            long fromStringIndex = mapping.length > 0 ? mapping[(int)fromByteIdx] : 0;
            long toStringIndex = 0;

            if(toByteIdx > 0) {
                toStringIndex = mapping.length > 0 ? mapping[(int)toByteIdx - 1] : 0;
            }

            return eventHandler.onMatch(expression, fromStringIndex, toStringIndex);
        });
    }


    /**
     * Scans raw bytes for matches using a compiled expression database and reports
     * matches to the provided event handler using byte indices.
     * Can only be executed one at a time on a per-instance basis.
     *
     * @param db           Database containing expressions to use for matching.
     * @param input        Bytes to match against.
     * @param eventHandler Handler to receive match events with byte indices.
     */
    public void scan(final Database db, final byte[] input, ByteMatchEventHandler eventHandler) {
        scan(db, ByteBuffer.wrap(input),
            (expressionId, fromByteIdx, toByteIdx, expressionFlags) ->
                    eventHandler.onMatch(db.getExpression(expressionId), fromByteIdx, toByteIdx)
        );
    }

    /**
     * Core scanning logic. Sets the thread-local callback and invokes the native hs_scan function.
     *
     * @param db           The database to use for scanning.
     * @param input        The raw byte array to scan.
     * @param eventHandler The raw handler to process matches reported by the native layer.
     */
    private int scan(final Database db, final ByteBuffer input, RawMatchEventHandler eventHandler) {
        if (scratch == null) {
            throw new IllegalStateException("Scratch space has already been deallocated");
        }

        if (activeCallback.get() != null) {
            throw new IllegalStateException("Recursive scanning is not supported.");
        }

        activeCallback.set(eventHandler);

        int hsError = 0;
        try {
            hs_database_t database = db.getDatabase();
            try (final BytePointer bytePointer = new BytePointer(input)) {
                hsError = hs_scan(database, bytePointer.position(input.position()), input.remaining(), 0, scratch, matchHandler, null);

                if (hsError != 0 && hsError != HS_SCAN_TERMINATED) {
                     throw HyperscanException.hsErrorToException(hsError);
                }
            }
        } finally {
            activeCallback.remove(); // Ensure the thread-local is cleared
        }
        return hsError;
    }

    /**
     * Check if there is at least one match in the given input ByteBuffer.
     * Scanning terminates immediately after the first match is found.
     *
     * @param db    Database containing expressions to use for matching.
     * @param input Bytes to match against.
     * @return true if at least one match is found, false otherwise.
     */
    public boolean hasMatch(final Database db, final ByteBuffer input) {
        // This handler returns false immediately upon the first match, terminating the scan.
        RawMatchEventHandler terminationHandler = (expressionId, fromByteIdx, toByteIdx, flags) -> false; // Request scan termination

        int hsError = scan(db, input, terminationHandler);
        // hsError == 0 means scan completed without matches.
        // hsError == HS_SCAN_TERMINATED means scan terminated early due to callback returning false (match found).
        return hsError == HS_SCAN_TERMINATED;
    }

    /**
     * Check if there is at least one match in the given input byte array.
     * Scanning terminates immediately after the first match is found.
     *
     * @param db    Database containing expressions to use for matching.
     * @param input Bytes to match against.
     * @return true if at least one match is found, false otherwise.
     */
    public boolean hasMatch(final Database db, final byte[] input) {
        // Allocate a direct buffer and copy data
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(input.length);
        directBuffer.put(input);
        ((Buffer)directBuffer).flip();
        return hasMatch(db, directBuffer);
    }

    /**
     * Check if there is at least one match in the given input String.
     * Scanning terminates immediately after the first match is found.
     *
     * @param db    Database containing expressions to use for matching.
     * @param input String to match against.
     * @return true if at least one match is found, false otherwise.
     */
    public boolean hasMatch(final Database db, final String input) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(input.length() * 4);
        Utf8Encoder.encodeToBufferAndMap(byteBuffer, input);
        return hasMatch(db, byteBuffer);
    }

    @Override
    public void close() throws IOException {
        if(scratch != null) {
            hs_free_scratch(scratch);
            scratch = null;
        }
    }
}
