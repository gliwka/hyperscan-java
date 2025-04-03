package com.gliwka.hyperscan.wrapper;

/**
 * Represents a compiler error due to an invalid expression
 */
public class CompileErrorException extends Exception {

    /**
     * The expression that caused the compilation to fail
     */
    private Expression failedExpression;

    /**
     * Creates a new CompileErrorException with an error message and the failed expression.
     *
     * @param s Error message describing the compilation error
     * @param failedExpression The expression that failed to compile
     */
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
