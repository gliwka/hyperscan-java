package com.gliwka.hyperscan.wrapper;

import com.gliwka.hyperscan.jni.hs_compile_error_t;
import com.gliwka.hyperscan.jni.hs_database_t;
import org.bytedeco.javacpp.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.*;
import java.util.stream.Collectors;

import static com.gliwka.hyperscan.jni.hyperscan.*;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;

/**
 * Database containing compiled expressions ready for scanning using the Scanner
 */
public class Database implements Closeable {
    private final Map<Integer, Expression> expressions;
    private final Expression[] expressionsById;
    private final int expressionCount;

    private NativeDatabase database;

    private static class NativeDatabase extends hs_database_t {
        void registerDeallocator() {
            hs_database_t p = new hs_database_t(this);
            deallocator(() -> hs_free_database(p));
        }
    }

    private static final Map<Integer, ExpressionFlag> BITMASK_TO_FLAG =
            Collections.unmodifiableMap(Arrays.stream(ExpressionFlag.values())
                    .collect(Collectors.toMap(ExpressionFlag::getBits, identity())));

    private final Mode mode;

    private Database(NativeDatabase database, List<Expression> expressions, Mode mode) {
        this.mode = mode;
        this.database = database;
        this.expressionCount = expressions.size();
        database.registerDeallocator();

        boolean hasIds = expressions.get(0).getId() != null;

        this.expressions = new HashMap<>(expressionCount);
        if (hasIds) {
            for (Expression expression : expressions) {
                if (this.expressions.put(expression.getId(), expression) != null)
                    throw new IllegalStateException("Expression ID must be unique within a Database.");
            }
        } else {
            int i = 0;
            for (Expression expression : expressions) {
                this.expressions.put(i++, expression);
            }
        }

        this.expressionsById = buildExpressionsById(expressions, hasIds);
    }

    private static Expression[] buildExpressionsById(List<Expression> expressions, boolean hasIds) {
        int maxId = -1;
        if (hasIds) {
            for (Expression expression : expressions) {
                Integer id = expression.getId();
                if (id != null && id > maxId) {
                    maxId = id;
                }
            }
        } else {
            maxId = expressions.size() - 1;
        }
        if (maxId < 0 || maxId > Math.max(4 * expressions.size(), 1024)) {
            return null;
        }
        Expression[] byId = new Expression[maxId + 1];
        if (hasIds) {
            for (Expression expression : expressions) {
                Integer id = expression.getId();
                if (id != null) {
                    byId[id] = expression;
                }
            }
        } else {
            int i = 0;
            for (Expression expression : expressions) {
                byId[i++] = expression;
            }
        }
        return byId;
    }

    private static void handleErrors(int hsError, hs_compile_error_t compileError, List<Expression> expressions) throws CompileErrorException {
        if (hsError == 0) {
            return;
        }

        if (hsError == HS_COMPILER_ERROR) {
            Expression expression = compileError.expression() < 0 ? null : expressions.get(compileError.expression());
            throw new CompileErrorException(compileError.message().getString(), expression);
        } else {
            throw HyperscanException.hsErrorToException(hsError);
        }
    }

    /**
     * compile an expression into a database to use for scanning
     *
     * @param expression Expression to compile
     * @return Compiled database
     * @throws CompileErrorException If the expression cannot be compiled
     */
    public static Database compile(Expression expression) throws CompileErrorException {
        return compile(singletonList(expression));
    }

    /**
     * Compiles a list of expressions into a database to use for scanning
     *
     * @param expressions List of expressions to compile
     * @return Compiled database
     * @throws CompileErrorException If any of the expressions cannot be compiled
     */
    public static Database compile(Expression... expressions) throws CompileErrorException {
        return Database.compile(Arrays.asList(expressions));
    }

    /**
     * Compiles a list of expressions into a database to use for scanning
     *
     * @param expressions List of expressions to compile
     * @return Compiled database
     * @throws CompileErrorException If any of the expressions cannot be compiled
     */
    public static Database compile(List<Expression> expressions) throws CompileErrorException {
        return compile(expressions, Mode.BLOCK);
    }

    /**
     * Compiles a list of expressions into a database in the given mode.
     * Block-mode databases work with the block scanning methods, stream-mode
     * databases with {@link Scanner#openStream(Database)}, and vectored-mode
     * databases with the vectored scanning methods.
     *
     * @param expressions List of expressions to compile
     * @param mode        Compilation mode
     * @return Compiled database
     * @throws CompileErrorException If any of the expressions cannot be compiled
     */
    public static Database compile(List<Expression> expressions, Mode mode) throws CompileErrorException {
        try (
                NativeExpressionCollection nativeExpressions = new NativeExpressionCollection(expressions);
                hs_compile_error_t errorT = new hs_compile_error_t();
                PointerPointer<NativeDatabase> database = new PointerPointer<>(1);
                PointerPointer<hs_compile_error_t> error = new PointerPointer<>(errorT)
        ) {

            int hsError = hs_compile_multi(
                    nativeExpressions.getExpressionsBytes(),
                    nativeExpressions.getNativeFlags(),
                    nativeExpressions.getNativeIds(),
                    nativeExpressions.getSize(),
                    nativeMode(mode),
                    null,
                    database,
                    error);

            handleErrors(hsError, error.get(hs_compile_error_t.class), expressions);

            return new Database(database.get(NativeDatabase.class), expressions, mode);
        }
    }

    /**
     * compile an expression into a database in the given mode to use for scanning
     *
     * @param expression Expression to compile
     * @param mode       Compilation mode
     * @return Compiled database
     * @throws CompileErrorException If the expression cannot be compiled
     */
    public static Database compile(Expression expression, Mode mode) throws CompileErrorException {
        return compile(singletonList(expression), mode);
    }

    private static int nativeMode(Mode mode) {
        switch (mode) {
            case STREAM:
                return HS_MODE_STREAM;
            case VECTORED:
                return HS_MODE_VECTORED;
            default:
                return HS_MODE_BLOCK;
        }
    }

    Mode getMode() {
        return mode;
    }

    NativeDatabase getDatabase() {
        return database;
    }


    /**
     * Get the database size in bytes
     *
     * @return count of bytes
     */
    public long getSize() {
        if (database == null) {
            throw new IllegalStateException("Database has already been deallocated");
        }

        try (SizeTPointer size = new SizeTPointer(1)) {
            hs_database_size(database, size);
            return size.get();
        }
    }

    Expression getExpression(int id) {
        Expression[] byId = expressionsById;
        if (byId != null && id >= 0 && id < byId.length) {
            Expression expression = byId[id];
            if (expression != null) {
                return expression;
            }
        }
        return expressions.get(id);
    }

    @Override
    public void close() {
        if(database != null) {
            database.close();
            database = null;
        }
    }

    /**
     * Saves the expressions and the compiled database to an OutputStream.
     * Expression contexts are not saved.
     * The OutputStream is not closed.
     *
     * @param out stream to write to
     * @throws IOException If an I/O error occurs while writing to the stream
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
     * @param databaseOut    stream to write database to
     * @throws IOException If an I/O error occurs while writing to either stream
     */
    public void save(OutputStream expressionsOut, OutputStream databaseOut) throws IOException {
        DataOutputStream expressionsDataOut = new DataOutputStream(expressionsOut);
        // How many expressions will be present. We need this to know when to stop reading.
        expressionsDataOut.writeInt(expressionCount);
        for (Expression expression : expressions.values()) {
            if (expression == null) {
                continue;
            }

            // Expression id
            expressionsDataOut.writeInt(expression.getId() == null ? -1 : expression.getId());
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
        try (BytePointer bytePointer = new BytePointer(1); SizeTPointer size = new SizeTPointer(1)) {
            int hsError = hs_serialize_database(database, bytePointer, size);

            if (hsError != 0) {
                throw HyperscanException.hsErrorToException(hsError);
            }

            int length = (int) size.get();

            // Write the native memory to the output stream.
            // We could just load all the native memory onto the heap but that would double our memory usage.
            // Instead we copy small blocks at a time
            ByteBuffer buffer = bytePointer.capacity(length).asBuffer();

            DataOutputStream databaseDataOut = new DataOutputStream(databaseOut);
            databaseDataOut.writeInt(length);
            // Neither DataOutputStream nor WritableByteChannel buffer so we can intermix usage.
            Channels.newChannel(databaseDataOut).write(buffer);
            databaseDataOut.flush();
        }
    }

    /**
     * Loads the database saved via {@link #save(OutputStream)}.
     * The saved payload contains platform-specific formatting so it should be loaded on a compatible platform.
     * All Expression contexts will be null.
     *
     * @param in stream to read from
     * @return loaded Database
     * @throws IOException If an I/O error occurs while reading from the stream
     */
    public static Database load(InputStream in) throws IOException {
        return load(in, in);
    }


    /**
     * Loads the database saved via {@link #save(OutputStream, OutputStream)}.
     * The saved payload contains platform-specific formatting so it should be loaded on a compatible platform.
     *
     * @param expressionsIn stream to read expressions from
     * @param databaseIn    stream to read database from
     * @return loaded Database
     * @throws IOException If an I/O error occurs while reading from either stream
     */
    public static Database load(InputStream expressionsIn, InputStream databaseIn) throws IOException {
        // DataInputStream doesn't buffer so it will only read as much as we ask for.
        // This makes it safe to use even if expressionsIn and databaseIn are the same streams.
        DataInputStream expressionsDataIn = new DataInputStream(expressionsIn);
        int expressionCount = expressionsDataIn.readInt();
        List<Expression> expressions = new ArrayList<>(expressionCount);

        for (int i = 0; i < expressionCount; i++) {
            int id = expressionsDataIn.readInt();
            String pattern = expressionsDataIn.readUTF();
            int flagCount = expressionsDataIn.readInt();
            EnumSet<ExpressionFlag> flags = EnumSet.noneOf(ExpressionFlag.class);
            for (int j = 0; j < flagCount; j++) {
                int bitmask = expressionsDataIn.readInt();
                flags.add(BITMASK_TO_FLAG.get(bitmask));

            }
            expressions.add(new Expression(pattern, flags, id == -1 ? null : id));
        }

        DataInputStream databaseDataIn = new DataInputStream(databaseIn);
        int length = databaseDataIn.readInt();
        byte[] bytes = new byte[(int) length];

        BytePointer bytePointer = new BytePointer(length);
        databaseDataIn.readFully(bytes);
        bytePointer.put(bytes);

        NativeDatabase database = new NativeDatabase();

        int hsError = hs_deserialize_database(bytePointer, length, database);
        if (hsError != 0) {
            throw HyperscanException.hsErrorToException(hsError);
        }

        // The mode is not recoverable from the serialized form; leave it
        // unknown so API-level mode validation is skipped for loaded databases.
        return new Database(database, expressions, null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Database database = (Database) o;
        return expressionCount == database.expressionCount && expressions.equals(database.expressions);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(expressionCount);
        result = 31 * result + expressions.hashCode();
        return result;
    }
}
