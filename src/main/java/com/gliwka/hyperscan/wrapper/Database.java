package com.gliwka.hyperscan.wrapper;

import com.gliwka.hyperscan.jna.CompileErrorStruct;
import com.gliwka.hyperscan.jna.HyperscanLibrary;
import com.gliwka.hyperscan.jna.SizeTByReference;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    private static void handleErrors(int hsError, Pointer compileError, List<Expression> expressions) throws Throwable {
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
     * @throws Throwable CompileErrorException on errors concerning the pattern, otherwise different Throwable's
     */
    public static Database compile(Expression expression) throws Throwable {
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
     * @throws Throwable CompileErrorException on errors concerning the pattern, otherwise different Throwable's
     */
    public static Database compile(List<Expression> expressions) throws Throwable {
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
        SizeTByReference size = new SizeTByReference();
        HyperscanLibrary.INSTANCE.hs_database_size(database, size);
        return size.getValue().longValue();
    }

    @Override
    protected void finalize() {
        HyperscanLibrary.INSTANCE.hs_free_database(database);
    }

    Expression getExpression(int id) {
        return expressions.get(id);
    }

    @Override
    public void close() throws IOException {
        this.finalize();
    }
}
