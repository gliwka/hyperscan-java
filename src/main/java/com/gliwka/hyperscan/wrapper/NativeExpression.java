package com.gliwka.hyperscan.wrapper;

import lombok.AccessLevel;
import lombok.Getter;
import org.bytedeco.javacpp.BytePointer;

import java.io.Closeable;

class NativeExpression implements Closeable {
    @Getter(AccessLevel.PACKAGE)
    private final BytePointer expressionBytes;

    @Getter(AccessLevel.PACKAGE)
    private final Expression expression;

    NativeExpression(Expression expression) {
        this.expressionBytes = new BytePointer(expression.getExpression());
        this.expression = expression;
    }

    @Override
    public void close()  {
        expressionBytes.close();
    }
}
