package com.gliwka.hyperscan.wrapper;

import com.gliwka.hyperscan.jni.hs_compile_error_t;
import com.gliwka.hyperscan.jni.hs_expr_info_t;
import lombok.*;

import java.util.EnumSet;

import static com.gliwka.hyperscan.jni.hyperscan.*;

/**
 * Expression to be compiled as a Database and then be used for scanning using the Scanner
 */
@EqualsAndHashCode
@ToString
public class Expression {
    @Getter @NonNull private final String expression;
    @Getter private final EnumSet<ExpressionFlag> flags;
    @Getter private final Integer id;

    /**
     * Represents the validation results for a expression
     */
    public static class ValidationResult {
        @Getter private String errorMessage;
        @Getter private final boolean isValid;

        ValidationResult(boolean isValid) {
            this.isValid = isValid;
        }

        ValidationResult(String errorMessage, boolean isValid) {
            this.errorMessage = errorMessage;
            this.isValid = isValid;
        }
    }

    /**
     * Creates an expression with the default NO_FLAG flag and no identifier.
     *
     * @param expression Regular expression pattern
     */
    public Expression(@NonNull String expression) {
        this(expression,  EnumSet.of(ExpressionFlag.NO_FLAG), null);
    }

    /**
     * Creates an expression with the default NO_FLAG flag and specified identifier.
     *
     * @param expression Regular expression pattern
     * @param id Expression identifier used to identify matches
     */
    public Expression(@NonNull String expression, Integer id) {
        this(expression,  EnumSet.of(ExpressionFlag.NO_FLAG), id);
    }

    /**
     * Creates an expression with the specified flags and no identifier.
     *
     * @param expression Regular expression pattern
     * @param flags Set of expression flags that modify the behavior of the pattern
     */
    public Expression(@NonNull String expression, @NonNull EnumSet<ExpressionFlag> flags) {
        this(expression, flags, null);
    }

    /**
     * Creates an expression with a single flag and no identifier.
     *
     * @param expression Regular expression pattern
     * @param flag Expression flag that modifies the behavior of the pattern
     */
    public Expression(@NonNull String expression, @NonNull ExpressionFlag flag) {
        this(expression, EnumSet.of(flag), null);
    }

    /**
     * Creates an expression with the specified flags and identifier.
     *
     * @param expression Regular expression pattern
     * @param flags Set of expression flags that modify the behavior of the pattern
     * @param id Expression identifier used to identify matches
     * @throws IllegalArgumentException if id is negative
     */
    public Expression(@NonNull String expression, @NonNull EnumSet<ExpressionFlag> flags, Integer id) {
        if (id != null && id < 0)
            throw new IllegalArgumentException("id must be >=0: " + id);

        this.expression = expression;
        this.flags = flags;
        this.id = id;
    }

    /**
     * Creates an expression with a single flag and specified identifier.
     *
     * @param expression Regular expression pattern
     * @param flag Expression flag that modifies the behavior of the pattern
     * @param id Expression identifier used to identify matches
     * @throws IllegalArgumentException if id is negative
     */
    public Expression(@NonNull String expression, @NonNull ExpressionFlag flag, Integer id) {
        this(expression, EnumSet.of(flag), id);
    }

    /**
     * Validates if the expression is a valid regular expression according to Hyperscan's requirements.
     * 
     * @return ValidationResult object containing validation status and error message if invalid
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
     * Returns the integer value representing the bitmask for this flag.
     * This value is used when interacting with the native Hyperscan library.
     *
     * @return The integer bitmask value.
     */
    int getFlagBits() {
        int bitValue = 0;

        if(flags != null) {
            for(BitFlag flag : flags) {
                bitValue = flag.getBits() | bitValue;
            }
        }

        return bitValue;
    }
}