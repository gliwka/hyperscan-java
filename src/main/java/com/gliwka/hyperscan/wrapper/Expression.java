package com.gliwka.hyperscan.wrapper;

import com.gliwka.hyperscan.jna.CompileErrorStruct;
import com.gliwka.hyperscan.jna.HyperscanLibrary;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import java.util.EnumSet;


/**
 * Expression to be compiled as a Database and then be used for scanning using the Scanner
 */
public class Expression {
    private EnumSet<ExpressionFlag> flags;
    private String expression;


    /**
     * Represents the validation results for a expression
     */
    public class ValidationResult {
        private String errorMessage;
        private boolean isValid;

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
     * Constructor for a new expression
     * @param expression Expression to use for matching
     * @param flags Flags influencing the behaviour of the scanner
     */
    public Expression(String expression, EnumSet<ExpressionFlag> flags)
    {
        this.expression = expression;
        this.flags = flags;
    }

    /**
     * Constructor for a new expression without flags
     * @param expression Expression to use for matching
     */
    public Expression(String expression)
    {
        this.expression = expression;
        this.flags = EnumSet.noneOf(ExpressionFlag.class);
    }

    /**
     * Validates if the expression instance is valid
     * @return ValidationResult object
     */
    public ValidationResult validate() {
        PointerByReference info = new PointerByReference();
        PointerByReference error = new PointerByReference();

        int hsResult = HyperscanLibrary.INSTANCE.hs_expression_info(this.expression, Util.bitEnumSetToInt(this.flags), info, error);

        String errorMessage = "";
        boolean isValid = true;

        if(hsResult != 0) {
            isValid = false;
            CompileErrorStruct errorStruct = new CompileErrorStruct(error.getValue());
            errorMessage = errorStruct.message;
            HyperscanLibrary.INSTANCE.hs_free_compile_error(errorStruct);
        }
        else {
            Native.free(Pointer.nativeValue(info.getValue()));
        }

        return new ValidationResult(errorMessage, isValid);
    }

    /**
     * Get the flags influencing the behaviour of the scanner
     * @return All defined flags for this expression
     */
    public EnumSet<ExpressionFlag> getFlags()
    {
        return flags;
    }

    /**
     * Get the expression String used for matching
     * @return expression as String
     */
    public String getExpression()
    {
        return expression;
    }
}