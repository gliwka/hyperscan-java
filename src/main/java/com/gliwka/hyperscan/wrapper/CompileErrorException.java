package com.gliwka.hyperscan.wrapper;

/**
 * Represents a compiler error due to an invalid expression
 */
public class CompileErrorException extends Exception {

    private Expression failedExpression;

    CompileErrorException(String s, Expression failedExpression) {
        super(s);

        this.failedExpression = failedExpression;
    }

    /**
     * Get the failed expression object
     * @return Expression object
     */
    public Expression getFailedExpression() {
        return failedExpression;
    }
}
