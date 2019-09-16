package com.gliwka.hyperscan.wrapper;

import com.gliwka.hyperscan.jna.CompileErrorStruct;
import com.gliwka.hyperscan.jna.HyperscanLibrary;
import com.gliwka.hyperscan.jna.SizeT;
import com.gliwka.hyperscan.jna.SizeTByReference;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Database containing compiled expressions ready for scanning using the Scanner
 */
public class Database implements Closeable {
    private static final int HS_MODE_BLOCK = 1;
    private static final int HS_COMPILE_ERROR = -4;
    private Pointer database;
    private List<Expression> expressions;

    private Database(Pointer database, List<Expression> expressions) {
        this.database = database;
        this.expressions = expressions;
    }

    private static void handleErrors(int hsError, Pointer compileError, List<Expression> expressions) throws CompileErrorException {
        if(hsError == 0)
            return;

        if(hsError == HS_COMPILE_ERROR) {
            CompileErrorStruct errorStruct = new CompileErrorStruct(compileError);
            try {
                throw new CompileErrorException(errorStruct.message, expressions.get(errorStruct.expression));
            }
            finally {
                errorStruct.setAutoRead(false);
                HyperscanLibrary.INSTANCE.hs_free_compile_error(errorStruct);
            }
        }
        else {
            throw Util.hsErrorIntToException(hsError);
        }
    }

    /**
     * compile an expression into a database to use for scanning
     * @param expression Expression to compile
     * @return Compiled database
     */
    public static Database compile(Expression expression) throws CompileErrorException {
        PointerByReference database = new PointerByReference();
        PointerByReference error = new PointerByReference();


        int hsError = HyperscanLibrary.INSTANCE.hs_compile(expression.getExpression(),
                Util.bitEnumSetToInt(expression.getFlags()), HS_MODE_BLOCK, Pointer.NULL, database, error);

        ArrayList<Expression> expressions = new ArrayList<Expression>(1);
        expressions.add(expression);

        handleErrors(hsError, error.getValue(), expressions);

        return new Database(database.getValue(), expressions);
    }

    /**
     * Compiles an list of expressions into a database to use for scanning
     * @param expressions List of expressions to compile
     * @return Compiled database
     */
    public static Database compile(List<Expression> expressions) throws CompileErrorException {
        final int expressionsSize = expressions.size();

        String[] expressionsStr = new String[expressionsSize];
        int[] flags = new int[expressionsSize];
        int[] ids = new int[expressionsSize];

        for(int i = 0; i < expressionsSize; i++) {
            expressionsStr[i] = expressions.get(i).getExpression();
            flags[i] = Util.bitEnumSetToInt(expressions.get(i).getFlags());
            ids[i] = i;
        }

        PointerByReference database = new PointerByReference();
        PointerByReference error = new PointerByReference();

        int hsError = HyperscanLibrary.INSTANCE.hs_compile_multi(expressionsStr, flags, ids, expressionsSize,
                HS_MODE_BLOCK, Pointer.NULL, database, error);

        handleErrors(hsError, error.getValue(), expressions);

        return new Database(database.getValue(), expressions);
    }

    Pointer getPointer() {
        return database;
    }


    /**
     * Get the database size in bytes
     * @return count of bytes
     */
    public long getSize() {
        if(database == null) {
            throw new IllegalStateException("Database has alredy been deallocated");
        }

        SizeTByReference size = new SizeTByReference();
        HyperscanLibrary.INSTANCE.hs_database_size(database, size);
        return size.getValue().longValue();
    }

    @Override
    protected void finalize() {
        if(database != null) {
            HyperscanLibrary.INSTANCE.hs_free_database(database);
            database = null;
        }
    }

    Expression getExpression(int id) {
        return expressions.get(id);
    }

    int getExpressionCount() {
        return expressions.size();
    }

    @Override
    public void close() throws IOException {
        this.finalize();
    }

    /**
     * Saves the expressions and the compiled database to an OutputStream.
     * Expression contexts are not saved.
     * The OutputStream is not closed.
     *
     * @param out stream to write to
     */
    public void save(OutputStream out) throws IOException {
        save(out, out);
    }

    /**
     * Saves the expressions and the compiled database to (possibly) distinct OutputStreams.
     * All of the expressions are saved to expressionsOut before any of the database is saved to databaseOut so it's safe
     * to use the same backing OutputStream for both parameters.
     * Expression contexts are not saved.
     * Neither of the OutputStream is closed.
     *
     * @param expressionsOut stream to write expressions to
     * @param databaseOut stream to write database to
     */
    public void save(OutputStream expressionsOut, OutputStream databaseOut) throws IOException {
        DataOutputStream expressionsDataOut = new DataOutputStream(expressionsOut);
        // How many expressions will be present. We need this to know when to stop reading.
        expressionsDataOut.writeInt(expressions.size());
        for (Expression expression : expressions) {
            // Expression pattern
            expressionsDataOut.writeUTF(expression.getExpression());
            // Flag count
            EnumSet<ExpressionFlag> flags = expression.getFlags();
            expressionsDataOut.writeInt(flags.size());
            for (ExpressionFlag flag : flags) {
                // Bitmask for each flag
                expressionsDataOut.writeInt(flag.getBits());
            }
        }
        expressionsDataOut.flush();

        // Serialize the database into a contiguous native memory block
        PointerByReference bytesRef = new PointerByReference();
        SizeTByReference lengthRef = new SizeTByReference();
        int hsError = HyperscanLibrary.INSTANCE.hs_serialize_database(database, bytesRef, lengthRef);
        try {
            if (hsError != 0) {
                throw Util.hsErrorIntToException(hsError);
            }

            int length = lengthRef.getValue().intValue();

            // Write the native memory to the output stream.
            // We could just load all the native memory onto the heap but that would double our memory usage.
            // Instead we copy small blocks at a time
            ByteBuffer buffer = bytesRef.getValue().getByteBuffer(0, length);

            DataOutputStream databaseDataOut = new DataOutputStream(databaseOut);
            databaseDataOut.writeInt(length);
            // Neither DataOutputStream nor WritableByteChannel buffer so we can intermix usage.
            Channels.newChannel(databaseDataOut).write(buffer);
            databaseDataOut.flush();
        } finally {
            // hs_misc_free should ideally be used to clean up but that's difficult to get a hold of and we don't provide
            // a mechanism to change it from its default value of free anyway so we'll use free directly.
            HyperscanLibrary.INSTANCE.free(bytesRef.getValue());
        }
    }

    /**
     * Loads the database saved via {@link #save(OutputStream)}.
     * The saved payload contains platform-specific formatting so it should be loaded on a compatible platform.
     * All Expression contexts will be null.
     *
     * @param in stream to read from
     * @return loaded Database
     */
    public static Database load(InputStream in) throws IOException {
        return load(in, in);
    }

    /**
     * Loads the database saved via {@link #save(OutputStream, OutputStream)}.
     * The saved payload contains platform-specific formatting so it should be loaded on a compatible platform.
     * All Expression contexts will be null.
     *
     * @param expressionsIn stream to read expressions from
     * @param databaseIn stream to read database from
     * @return loaded Database
     */
    public static Database load(InputStream expressionsIn, InputStream databaseIn) throws IOException {
        return load(expressionsIn, databaseIn, (pattern, flags) -> null);
    }

    /**
     * Loads the database saved via {@link #save(OutputStream, OutputStream)}.
     * The saved payload contains platform-specific formatting so it should be loaded on a compatible platform.
     * Expression contexts will be recreated using the provided contextCreator.
     *
     * @param expressionsIn stream to read expressions from
     * @param databaseIn stream to read database from
     * @param contextCreator callback responsible for creating an Expression's context given its pattern and flags
     * @return loaded Database
     */
    public static Database load(InputStream expressionsIn, InputStream databaseIn,
                                BiFunction<String, EnumSet<ExpressionFlag>, Object> contextCreator) throws IOException {
        // DataInputStream doesn't buffer so it will only read as much as we ask for.
        // This makes it safe to use even if expressionsIn and databaseIn are the same streams.
        DataInputStream expressionsDataIn = new DataInputStream(expressionsIn);
        int expressionCount = expressionsDataIn.readInt();
        List<Expression> expressions = new ArrayList<>(expressionCount);

        // Setup a lookup map for expression flags
        Map<Integer, ExpressionFlag> bitmaskToFlag = Arrays.stream(ExpressionFlag.values())
                .collect(Collectors.toMap(ExpressionFlag::getBits, Function.identity()));

        for (int i = 0; i < expressionCount; i++) {
            String pattern = expressionsDataIn.readUTF();
            int flagCount = expressionsDataIn.readInt();
            EnumSet<ExpressionFlag> flags = EnumSet.noneOf(ExpressionFlag.class);
            for (int j = 0; j < flagCount; j++) {
                int bitmask = expressionsDataIn.readInt();
                flags.add(bitmaskToFlag.get(bitmask));

            }
            expressions.add(new Expression(pattern, flags, contextCreator.apply(pattern, flags)));
        }

        DataInputStream databaseDataIn = new DataInputStream(databaseIn);
        int length = databaseDataIn.readInt();
        byte[] bytes = new byte[length];
        databaseDataIn.readFully(bytes, 0, length);

        PointerByReference dbRef = new PointerByReference();
        int hsError = HyperscanLibrary.INSTANCE.hs_deserialize_database(bytes, new SizeT(length), dbRef);
        if (hsError != 0) {
            throw Util.hsErrorIntToException(hsError);
        }

        return new Database(dbRef.getValue(), expressions);
    }
}
