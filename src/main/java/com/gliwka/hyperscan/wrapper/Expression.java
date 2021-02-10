package com.gliwka.hyperscan.wrapper;

import com.gliwka.hyperscan.jni.hs_compile_error_t;
import com.gliwka.hyperscan.jni.hs_expr_info_t;

import java.util.EnumSet;
import java.util.Objects;

import static com.gliwka.hyperscan.jni.hyperscan.*;


/**
 * Expression to be compiled as a Database and then be used for scanning using the Scanner
 */
public class Expression {
    private EnumSet<ExpressionFlag> flags;
    private String expression;
    private Object context = null;

    /**
     * Get the context object associated with the Expression
     * @return
     */
    public Object getContext() {
        return context;
    }


    /**
     * Represents the validation results for a expression
     */
    public class ValidationResult {
        private String errorMessage;
        private boolean isValid;

        ValidationResult(boolean isValid) {
            this.isValid = isValid;
        }

        ValidationResult(String errorMessage, boolean isValid) {
            this.errorMessage = errorMessage;
            this.isValid = isValid;
        }


        /**
         * Get a boolean indicating if the expression is valid
         * @return true if valid, otherwise false
         */
        public boolean getIsValid() {
            return isValid;
        }

        /**
         * Get an string containing an error message in case of an invalid expression
         * @return error message string if invalid, otherwise empty string.
         */
        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Constructor for a new expression without flags
     * @param expression Expression to use for matching
     */
    public Expression(String expression)
    {
        checkArguments(expression);

        this.expression = expression;
        this.flags = EnumSet.noneOf(ExpressionFlag.class);
    }

    /**
     * Constructor for a new expression without flags
     * @param expression Expression to use for matching
     * @param context Context object associated with expression
     */
    public Expression(String expression, Object context)
    {
        checkArguments(expression);

        this.expression = expression;
        this.flags = EnumSet.noneOf(ExpressionFlag.class);
        this.context = context;
    }


    /**
     * Constructor for a new expression
     * @param expression Expression to use for matching
     * @param flags Flags influencing the behaviour of the scanner
     */
    public Expression(String expression, EnumSet<ExpressionFlag> flags)
    {
        checkArguments(expression);

        this.expression = expression;
        this.flags = flags;
    }

    /**
     * Constructor for a new expression
     * @param expression Expression to use for matching
     * @param flag Single ExpressionFlag influencing the behaviour of the scanner
     */
    public Expression(String expression, ExpressionFlag flag)
    {
        checkArguments(expression);

        this.expression = expression;
        this.flags = EnumSet.of(flag);
    }

    /**
     * Constructor for a new expression
     * @param expression Expression to use for matching
     * @param flags Flags influencing the behaviour of the scanner
     * @param context Context object associated with the expression
     */
    public Expression(String expression, EnumSet<ExpressionFlag> flags, Object context)
    {
        checkArguments(expression);

        this.expression = expression;
        this.flags = flags;
        this.context = context;
    }

    /**
     * Constructor for a new expression
     * @param expression Expression to use for matching
     * @param flag Single ExpressionFlag influencing the behaviour of the scanner
     * @param context Context object associated with the expression
     */
    public Expression(String expression, ExpressionFlag flag, Object context)
    {
        checkArguments(expression);

        this.expression = expression;
        this.flags = EnumSet.of(flag);
        this.context = context;
    }
    

    /**
     * Validates if the expression instance is valid
     * @return ValidationResult object
     */


    public ValidationResult validate() {
        try(hs_expr_info_t info = new hs_expr_info_t(); hs_compile_error_t error = new hs_compile_error_t()) {
            int hsResult = hs_expression_info(this.expression, getFlagBits(), info, error);

            if(hsResult != 0) {
                return new ValidationResult(error.message().getString(), false);

            }
            else {
                return new ValidationResult(true);
            }

        }
    }

    /**
     * Get the flags influencing the behaviour of the scanner
     * @return All defined flags for this expression
     */
    public EnumSet<ExpressionFlag> getFlags()
    {
        return flags;
    }

    int getFlagBits() {
        int bitValue = 0;

        for(BitFlag flag : flags) {
            bitValue = flag.getBits() | bitValue;
        }

        return bitValue;
    }

    /**
     * Get the expression String used for matching
     * @return expression as String
     */
    public String getExpression()
    {
        return expression;
    }

    private static void checkArguments(String expression) {
        if(expression == null) {
            throw new NullPointerException("Null value for expression is not allowed");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Expression that = (Expression) o;
        return Objects.equals(flags, that.flags) &&
                Objects.equals(expression, that.expression) &&
                Objects.equals(context, that.context);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flags, expression, context);
    }
}