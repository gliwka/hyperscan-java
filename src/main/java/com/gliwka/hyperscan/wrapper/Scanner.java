package com.gliwka.hyperscan.wrapper;

import com.gliwka.hyperscan.jni.hs_database_t;
import com.gliwka.hyperscan.jni.hs_scratch_t;
import com.gliwka.hyperscan.jni.hs_stream_t;
import com.gliwka.hyperscan.jni.match_event_handler;
import com.gliwka.hyperscan.wrapper.mapping.ByteCharMapping;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.javacpp.SizeTPointer;

import java.io.Closeable;
import java.io.IOException;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Arrays;
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
    private static final ThreadLocal<ByteBuffer> scanBuffer = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(0));
    private static final ThreadLocal<ByteBuffer> hasMatchBuffer = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(0));
    private static final ThreadLocal<ByteBuffer> rawScanBuffer = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(0));

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
        int required = input.length() * 4;
        ByteBuffer byteBuffer = scanBuffer.get();
        if (byteBuffer.capacity() < required) {
            byteBuffer = ByteBuffer.allocateDirect(required);
            scanBuffer.set(byteBuffer);
        } else {
            ((Buffer) byteBuffer).clear();
        }
        final ByteCharMapping mapping = Utf8Encoder.encodeToBufferAndMap(byteBuffer, input);

        scan(db, byteBuffer, (expressionId, fromByteIdx, toByteIdx, flags) -> {
            Expression expression = db.getExpression(expressionId);
            long fromStringIndex = mapping.getCharIndex((int)fromByteIdx);
            long toStringIndex = 0;

            if(toByteIdx > 0) {
                toStringIndex = mapping.getCharIndex((int)toByteIdx - 1);
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
        RawMatchEventHandler rawHandler = input == null || input.length == 0
                ? (expressionId, fromByteIdx, toByteIdx, expressionFlags) -> true
                : (expressionId, fromByteIdx, toByteIdx, expressionFlags) ->
                    eventHandler.onMatch(db.getExpression(expressionId), fromByteIdx, toByteIdx);
        scanRaw(db, input, rawHandler);
    }

    /**
     * Scans a {@link ByteBuffer} for matches using a compiled expression database
     * and reports matches to the provided event handler using byte indices.
     * Bytes from the buffer's current position to its limit are scanned; the
     * position and limit are not modified.
     * Direct buffers are scanned zero-copy. Heap buffers are first copied into
     * a reused per-thread direct buffer.
     * Can only be executed one at a time on a per-instance basis.
     *
     * @param db           Database containing expressions to use for matching.
     * @param input        Buffer to match against.
     * @param eventHandler Handler to receive match events with byte indices.
     */
    public void scan(final Database db, final ByteBuffer input, ByteMatchEventHandler eventHandler) {
        RawMatchEventHandler rawHandler = (expressionId, fromByteIdx, toByteIdx, expressionFlags) ->
                eventHandler.onMatch(db.getExpression(expressionId), fromByteIdx, toByteIdx);
        if (input.isDirect()) {
            scan(db, input, rawHandler);
            return;
        }
        int remaining = input.remaining();
        ByteBuffer directBuffer = rawScanBuffer.get();
        if (directBuffer.capacity() < remaining) {
            directBuffer = ByteBuffer.allocateDirect(remaining);
            rawScanBuffer.set(directBuffer);
        } else {
            ((Buffer) directBuffer).clear();
        }
        directBuffer.put(input.duplicate());
        ((Buffer) directBuffer).flip();
        scan(db, directBuffer, rawHandler);
    }

    private int scanRaw(final Database db, final byte[] input, RawMatchEventHandler eventHandler) {
        if (input != null && input.length > 0) {
            ByteBuffer directBuffer = rawScanBuffer.get();
            if (directBuffer.capacity() < input.length) {
                directBuffer = ByteBuffer.allocateDirect(input.length);
                rawScanBuffer.set(directBuffer);
            } else {
                ((Buffer) directBuffer).clear();
            }
            directBuffer.put(input);
            ((Buffer) directBuffer).flip();
            return scan(db, directBuffer, eventHandler);
        }
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
                int length = input == null ? 4 : input.length;
                hsError = hs_scan(database, bytePointer, length, 0, scratch, matchHandler, null);
                if (hsError != 0 && hsError != HS_SCAN_TERMINATED) {
                    throw HyperscanException.hsErrorToException(hsError);
                }
            }
        } finally {
            activeCallback.remove();
        }
        return hsError;
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
        int required = input.length;
        ByteBuffer directBuffer = hasMatchBuffer.get();
        if (directBuffer.capacity() < required) {
            directBuffer = ByteBuffer.allocateDirect(required);
            hasMatchBuffer.set(directBuffer);
        } else {
            ((Buffer) directBuffer).clear();
        }
        directBuffer.put(input);
        ((Buffer) directBuffer).flip();
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
        int required = input.length() * 4;
        ByteBuffer byteBuffer = scanBuffer.get();
        if (byteBuffer.capacity() < required) {
            byteBuffer = ByteBuffer.allocateDirect(required);
            scanBuffer.set(byteBuffer);
        } else {
            ((Buffer) byteBuffer).clear();
        }
        Utf8Encoder.encodeToBufferAndMap(byteBuffer, input);
        return hasMatch(db, byteBuffer);
    }

    /**
     * Scans a sequence of byte arrays as one logical input using the vectored
     * scanning mode. The segments are matched as if concatenated, and reported
     * byte indices are relative to the start of the first segment.
     * Requires a database compiled with {@link Mode#VECTORED}.
     * Neither the arrays nor their contents may be null.
     *
     * @param db           Database containing expressions to use for matching.
     * @param inputs       Segments to match against.
     * @param eventHandler Handler to receive match events with byte indices.
     */
    public void scanVector(final Database db, final byte[][] inputs, ByteMatchEventHandler eventHandler) {
        RawMatchEventHandler rawHandler = (expressionId, fromByteIdx, toByteIdx, expressionFlags) ->
                eventHandler.onMatch(db.getExpression(expressionId), fromByteIdx, toByteIdx);
        int total = 0;
        for (byte[] input : inputs) {
            total += input.length;
        }
        ByteBuffer directBuffer = rawScanBuffer.get();
        if (directBuffer.capacity() < total) {
            directBuffer = ByteBuffer.allocateDirect(Math.max(total, 1));
            rawScanBuffer.set(directBuffer);
        } else {
            ((Buffer) directBuffer).clear();
        }
        for (byte[] input : inputs) {
            directBuffer.put(input);
        }
        ((Buffer) directBuffer).flip();
        int[] lengths = new int[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            lengths[i] = inputs[i].length;
        }
        scanVectorRaw(db, directBuffer, lengths, rawHandler);
    }

    /**
     * Scans a sequence of {@link ByteBuffer}s as one logical input using the
     * vectored scanning mode. For every segment, bytes from position to limit
     * are scanned; positions and limits are not modified.
     * Direct segments are scanned zero-copy; heap segments are first copied
     * into a reused per-thread direct buffer.
     * The segments are matched as if concatenated, and reported byte indices
     * are relative to the start of the first segment.
     * Requires a database compiled with {@link Mode#VECTORED}.
     * Neither the array nor its elements may be null.
     *
     * @param db           Database containing expressions to use for matching.
     * @param inputs       Segments to match against.
     * @param eventHandler Handler to receive match events with byte indices.
     */
    public void scanVector(final Database db, final ByteBuffer[] inputs, ByteMatchEventHandler eventHandler) {
        RawMatchEventHandler rawHandler = (expressionId, fromByteIdx, toByteIdx, expressionFlags) ->
                eventHandler.onMatch(db.getExpression(expressionId), fromByteIdx, toByteIdx);
        int heapTotal = 0;
        for (ByteBuffer input : inputs) {
            if (!input.isDirect()) {
                heapTotal += input.remaining();
            }
        }
        ByteBuffer bulk = null;
        if (heapTotal > 0) {
            bulk = rawScanBuffer.get();
            if (bulk.capacity() < heapTotal) {
                bulk = ByteBuffer.allocateDirect(heapTotal);
                rawScanBuffer.set(bulk);
            } else {
                ((Buffer) bulk).clear();
            }
        }
        int n = inputs.length;
        int[] lengths = new int[n];
        ByteBuffer[] segments = new ByteBuffer[n];
        int[] segmentStarts = new int[n];
        for (int i = 0; i < n; i++) {
            ByteBuffer input = inputs[i];
            lengths[i] = input.remaining();
            if (input.isDirect()) {
                segments[i] = input;
                segmentStarts[i] = input.position();
            } else {
                segmentStarts[i] = bulk.position();
                bulk.put(input.duplicate());
                segments[i] = bulk;
            }
        }
        scanVectorRaw(db, segments, segmentStarts, lengths, rawHandler);
    }

    private int scanVectorRaw(final Database db, final ByteBuffer packed, final int[] lengths,
                              RawMatchEventHandler eventHandler) {
        int n = lengths.length;
        ByteBuffer[] segments = new ByteBuffer[n];
        Arrays.fill(segments, packed);
        int[] starts = new int[n];
        int offset = 0;
        for (int i = 0; i < n; i++) {
            starts[i] = offset;
            offset += lengths[i];
        }
        return scanVectorRaw(db, segments, starts, lengths, eventHandler, packed);
    }

    private int scanVectorRaw(final Database db, final ByteBuffer[] segments, final int[] starts,
                              final int[] lengths, RawMatchEventHandler eventHandler) {
        return scanVectorRaw(db, segments, starts, lengths, eventHandler, null);
    }

    private int scanVectorRaw(final Database db, final ByteBuffer[] segments, final int[] starts,
                              final int[] lengths, RawMatchEventHandler eventHandler, ByteBuffer packed) {
        if (scratch == null) {
            throw new IllegalStateException("Scratch space has already been deallocated");
        }
        if (db.getMode() != null && db.getMode() != Mode.VECTORED) {
            throw new IllegalArgumentException("Vectored scanning requires a database compiled with Mode.VECTORED");
        }
        if (activeCallback.get() != null) {
            throw new IllegalStateException("Recursive scanning is not supported.");
        }
        activeCallback.set(eventHandler);
        int hsError;
        int n = lengths.length;
        ByteBuffer keepAlive = packed != null ? packed : segments[0];
        try {
            hs_database_t database = db.getDatabase();
            try (PointerPointer<BytePointer> data = new PointerPointer<>(n);
                 IntPointer lengthPtr = new IntPointer(lengths)) {
                BytePointer base = new BytePointer(keepAlive);
                for (int i = 0; i < n; i++) {
                    BytePointer element = new BytePointer(segments[i] == keepAlive ? base : new BytePointer(segments[i]));
                    data.put(i, element.position(starts[i]));
                }
                hsError = hs_scan_vector(database, data, lengthPtr, n, 0, scratch, matchHandler, null);
                if (hsError != 0 && hsError != HS_SCAN_TERMINATED) {
                    throw HyperscanException.hsErrorToException(hsError);
                }
            }
        } finally {
            activeCallback.remove();
        }
        return hsError;
    }

    /**
     * Opens a streaming scan session over the given database. The returned
     * stream shares this scanner's scratch space and must be closed after use,
     * preferably with try-with-resources. Closing without a handler discards
     * pending matches; use {@link Stream#close(ByteMatchEventHandler)} to
     * receive them.
     * Not thread-safe, just like the owning scanner.
     *
     * @param db Database containing expressions to use for matching.
     * @return Open stream ready for scanning.
     */
    public Stream openStream(final Database db) {
        if (scratch == null) {
            throw new IllegalStateException("Scratch space has already been deallocated");
        }
        if (db.getMode() != null && db.getMode() != Mode.STREAM) {
            throw new IllegalArgumentException("Streaming requires a database compiled with Mode.STREAM");
        }
        hs_stream_t nativeStream;
        try (PointerPointer<hs_stream_t> streamOut = new PointerPointer<>(1)) {
            streamOut.put(0, new hs_stream_t());
            int hsError = hs_open_stream(db.getDatabase(), 0, streamOut);
            if (hsError != 0) {
                throw HyperscanException.hsErrorToException(hsError);
            }
            nativeStream = streamOut.get(hs_stream_t.class);
        }
        return new Stream(db, nativeStream);
    }

    private int scanStreamRaw(final hs_stream_t stream, final ByteBuffer input, RawMatchEventHandler eventHandler) {
        if (scratch == null) {
            throw new IllegalStateException("Scratch space has already been deallocated");
        }
        if (activeCallback.get() != null) {
            throw new IllegalStateException("Recursive scanning is not supported.");
        }
        activeCallback.set(eventHandler);
        int hsError = 0;
        try {
            try (final BytePointer bytePointer = new BytePointer(input)) {
                hsError = hs_scan_stream(stream, bytePointer.position(input.position()), input.remaining(), 0, scratch, matchHandler, null);
                if (hsError != 0 && hsError != HS_SCAN_TERMINATED) {
                    throw HyperscanException.hsErrorToException(hsError);
                }
            }
        } finally {
            activeCallback.remove();
        }
        return hsError;
    }

    /**
     * A streaming scan session created by {@link Scanner#openStream(Database)}.
     * Input is fed in chunks via {@link #scan(byte[], ByteMatchEventHandler)}
     * or {@link #scan(ByteBuffer, ByteMatchEventHandler)}; matches are reported
     * with byte offsets relative to the start of the stream, so patterns
     * spanning chunk boundaries are matched transparently.
     * Not thread-safe; the stream shares the owning scanner's scratch space.
     */
    public class Stream implements Closeable {
        private final Database database;
        private hs_stream_t nativeStream;

        private Stream(final Database database, final hs_stream_t nativeStream) {
            this.database = database;
            this.nativeStream = nativeStream;
        }

        /**
         * Feeds one chunk of input to the stream.
         *
         * @param input        Chunk to match against, may be empty (flush only).
         * @param eventHandler Handler to receive match events with byte indices.
         */
        public void scan(final byte[] input, ByteMatchEventHandler eventHandler) {
            ensureOpen();
            RawMatchEventHandler rawHandler = (expressionId, fromByteIdx, toByteIdx, expressionFlags) ->
                    eventHandler.onMatch(database.getExpression(expressionId), fromByteIdx, toByteIdx);
            int length = input == null ? 0 : input.length;
            ByteBuffer directBuffer = rawScanBuffer.get();
            if (directBuffer.capacity() < Math.max(length, 1)) {
                directBuffer = ByteBuffer.allocateDirect(Math.max(length, 1));
                rawScanBuffer.set(directBuffer);
            } else {
                ((Buffer) directBuffer).clear();
            }
            if (length > 0) {
                directBuffer.put(input);
            }
            ((Buffer) directBuffer).flip();
            scanStreamRaw(nativeStream, directBuffer, rawHandler);
        }

        /**
         * Feeds one chunk of input to the stream. Bytes from the buffer's
         * current position to its limit are scanned; position and limit are
         * not modified. Direct buffers are scanned zero-copy, heap buffers are
         * first copied into a reused per-thread direct buffer.
         *
         * @param input        Chunk to match against.
         * @param eventHandler Handler to receive match events with byte indices.
         */
        public void scan(final ByteBuffer input, ByteMatchEventHandler eventHandler) {
            ensureOpen();
            RawMatchEventHandler rawHandler = (expressionId, fromByteIdx, toByteIdx, expressionFlags) ->
                    eventHandler.onMatch(database.getExpression(expressionId), fromByteIdx, toByteIdx);
            if (input.isDirect()) {
                scanStreamRaw(nativeStream, input, rawHandler);
                return;
            }
            int remaining = input.remaining();
            ByteBuffer directBuffer = rawScanBuffer.get();
            if (directBuffer.capacity() < Math.max(remaining, 1)) {
                directBuffer = ByteBuffer.allocateDirect(Math.max(remaining, 1));
                rawScanBuffer.set(directBuffer);
            } else {
                ((Buffer) directBuffer).clear();
            }
            directBuffer.put(input.duplicate());
            ((Buffer) directBuffer).flip();
            scanStreamRaw(nativeStream, directBuffer, rawHandler);
        }

        /**
         * Closes the stream and discards any pending matches.
         */
        @Override
        public void close() {
            if (nativeStream != null) {
                hs_stream_t stream = nativeStream;
                nativeStream = null;
                int hsError = hs_close_stream(stream, scratch, null, null);
                if (hsError != 0 && hsError != HS_SCAN_TERMINATED) {
                    throw HyperscanException.hsErrorToException(hsError);
                }
            }
        }

        /**
         * Closes the stream, reporting any matches still pending at the end of
         * the stream to the given handler.
         *
         * @param eventHandler Handler to receive trailing match events.
         */
        public void close(final ByteMatchEventHandler eventHandler) {
            if (nativeStream == null) {
                return;
            }
            ensureCallbackFree();
            RawMatchEventHandler rawHandler = (expressionId, fromByteIdx, toByteIdx, expressionFlags) ->
                    eventHandler.onMatch(database.getExpression(expressionId), fromByteIdx, toByteIdx);
            hs_stream_t stream = nativeStream;
            nativeStream = null;
            activeCallback.set(rawHandler);
            try {
                int hsError = hs_close_stream(stream, scratch, matchHandler, null);
                if (hsError != 0 && hsError != HS_SCAN_TERMINATED) {
                    throw HyperscanException.hsErrorToException(hsError);
                }
            } finally {
                activeCallback.remove();
            }
        }

        private void ensureOpen() {
            if (nativeStream == null) {
                throw new IllegalStateException("Stream is already closed");
            }
        }

        private void ensureCallbackFree() {
            if (activeCallback.get() != null) {
                throw new IllegalStateException("Recursive scanning is not supported.");
            }
        }
    }

    @Override
    public void close() throws IOException {
        if(scratch != null) {
            hs_free_scratch(scratch);
            scratch = null;
        }
    }
}
