package com.gliwka.hyperscan.wrapper;

import lombok.AccessLevel;
import lombok.Getter;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.PointerPointer;

import java.io.Closeable;
import java.util.List;

import static java.util.stream.Collectors.toList;

class NativeExpressionCollection implements Closeable {
    @Getter(AccessLevel.PACKAGE)
    private final List<NativeExpression> nativeExpressions;

    @Getter(AccessLevel.PACKAGE)
    private final PointerPointer<BytePointer> expressionsBytes;

    @Getter(AccessLevel.PACKAGE)
    private final IntPointer nativeFlags;

    @Getter(AccessLevel.PACKAGE)
    private final IntPointer nativeIds;

    @Getter(AccessLevel.PACKAGE)
    private final int size;

    NativeExpressionCollection(List<Expression> expressions) {
        this.size = expressions.size();

        boolean expressionWithoutId = expressions.stream().anyMatch(expression -> expression.getId() == null);
        boolean expressionWithId = expressions.stream().anyMatch(expression -> expression.getId() != null);

        if (expressionWithId && expressionWithoutId) {
            throw new IllegalStateException("You can't mix expressions with and without id's in a single database");
        }

        this.nativeExpressions = expressions.stream().map(NativeExpression::new).collect(toList());
        this.expressionsBytes = new PointerPointer<>(size);

        BytePointer[] bytePointers = nativeExpressions
                .stream()
                .map(NativeExpression::getExpressionBytes)
                .toArray(BytePointer[]::new);

        this.expressionsBytes.put(bytePointers);

        int[] flags = new int[size];
        int[] ids = new int[size];

        int i = 0;
        for (Expression expression : expressions) {
            flags[i] = expression.getFlagBits();
            if (expressionWithId) {
                ids[i] = expression.getId();
            } else {
                ids[i] = i;
            }
            i++;
        }

        this.nativeFlags = new IntPointer(flags);
        this.nativeIds = new IntPointer(ids);
    }


    @Override
    public void close() {
        this.nativeExpressions.forEach(NativeExpression::close);
        expressionsBytes.close();
        this.nativeFlags.close();
        this.nativeIds.close();
    }
}
