package com.gliwka.hyperscan.util;

import com.gliwka.hyperscan.wrapper.Expression;
import com.gliwka.hyperscan.wrapper.ExpressionFlag;

import java.util.EnumSet;
import java.util.regex.Pattern;

/**
 * Internal helper for translating a {@link Pattern} into a Hyperscan {@link Expression}.
 *
 * <p>It maps the relevant {@link Pattern} flags (case-insensitive, multiline, dotall) onto their
 * Hyperscan equivalents and compiles every expression in prefilter mode. Shared by
 * {@link PatternFilter} and {@link ScopedPatternFilterFactory} so the classification of a pattern
 * as filterable or not stays identical across both.
 */
final class ExpressionUtil {

    private ExpressionUtil() {

        throw new IllegalStateException("Utility class");
    }

    /**
     * Translates a {@link Pattern} into a prefilter-mode Hyperscan {@link Expression}.
     *
     * @param pattern the pattern to translate
     * @param id      the expression id used to map a Hyperscan match back to the source pattern
     * @return the compiled expression, or {@code null} if Hyperscan cannot represent the pattern
     */
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
