package com.gliwka.hyperscan.util;

import com.gliwka.hyperscan.wrapper.Expression;
import com.gliwka.hyperscan.wrapper.ExpressionFlag;

import java.util.EnumSet;
import java.util.regex.Pattern;

final class ExpressionUtil {

    private ExpressionUtil() {

        throw new IllegalStateException("Utility class");
    }

    static Expression mapToExpression(Pattern pattern, int id) {
        EnumSet<ExpressionFlag> flags = EnumSet.of(ExpressionFlag.UTF8, ExpressionFlag.PREFILTER, ExpressionFlag.ALLOWEMPTY, ExpressionFlag.SINGLEMATCH);

        if (hasFlag(pattern, Pattern.CASE_INSENSITIVE)) {
            flags.add(ExpressionFlag.CASELESS);
        }

        if (hasFlag(pattern, Pattern.MULTILINE)) {
            flags.add(ExpressionFlag.MULTILINE);
        }

        if (hasFlag(pattern, Pattern.DOTALL)) {
            flags.add(ExpressionFlag.DOTALL);
        }

        Expression expression = new Expression(pattern.pattern(), flags, id);

        if (!expression.validate().isValid()) {
            return null;
        }

        return expression;
    }

    static boolean hasFlag(Pattern pattern, int flag) {
        return (pattern.flags() & flag) == flag;
    }

}
