package com.gliwka.hyperscan.util;

import com.gliwka.hyperscan.wrapper.*;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filters a list of java.util.regex.Pattern using hyperscan
 * Returns only potentially matching patterns.
 *
 * This is not thread-safe, use once per thread.
 *
 * It allows to use hyperscan to filter only potentially
 * matching Patterns from a large list of patterns.
 *
 * You can still use the the full feature set
 * provided by regular Java Pattern API with some
 * performance benefits from hyperscan
 *
 * This is similar to chimera, only with java APIs.
 */
public class PatternFilter implements Closeable {
    private final Matcher[] matchers;
    private final Scanner scanner;
    private final Database database;

    //Some obscure patterns cannot be handled by hyperscan PREFILTER, hence will never be filtered
    private final List<Matcher> notFilterable = new ArrayList<>();

    /**
     * Create a pattern filter for the provided patterns
     * @param patterns Patterns to be filtered
     * @throws CompileErrorException in case the compilation of the hyperscan representation fails
     */
    public PatternFilter(List<Pattern> patterns) throws CompileErrorException {
        matchers = new Matcher[patterns.size()];
        List<Expression> expressions = new ArrayList<>();

        int id = 0;

        for(Pattern pattern : patterns) {
            Expression expression = mapToExpression(pattern, id);

            if(expression == null) {
                //can't be compiled to expression -> not filterable
                notFilterable.add(pattern.matcher(""));
            }
            else {
                expressions.add(expression);
                matchers[id] = pattern.matcher("");
                id++;
            }
        }

        database = Database.compile(expressions);
        scanner = new Scanner();
        scanner.allocScratch(database);
    }

    /**
     * Filter the large list of patterns down to a small list of probable matches
     * You need to confirm those using the regular Matcher API (.find()/matches())
     * @param input Text to use to match Patterns
     * @return Matcher for the probably matching Patterns
     */
    public List<Matcher> filter(String input) {
        List<Matcher> matchedMatchers = new ArrayList<>();

        List<Match> matches = scanner.scan(database, input);
        matches.forEach(match -> {
            matchedMatchers.add(this.matchers[match.getMatchedExpression().getId()]);
        });

        matchedMatchers.addAll(notFilterable);

        matchedMatchers.forEach(matcher -> matcher.reset(input));

        return matchedMatchers;
    }

    private Expression mapToExpression(Pattern pattern, int id) {
        EnumSet<ExpressionFlag> flags = EnumSet.of(
            ExpressionFlag.UTF8,
            ExpressionFlag.PREFILTER,
            ExpressionFlag.ALLOWEMPTY,
            ExpressionFlag.SINGLEMATCH
        );

        if(hasFlag(pattern, Pattern.CASE_INSENSITIVE)) {
            flags.add(ExpressionFlag.CASELESS);
        }

        if(hasFlag(pattern, Pattern.MULTILINE)) {
            flags.add(ExpressionFlag.MULTILINE);
        }

        if(hasFlag(pattern, Pattern.DOTALL)) {
            flags.add(ExpressionFlag.DOTALL);
        }

        Expression expression = new Expression(pattern.pattern(), flags, id);

        if(!expression.validate().isValid()) {
            return null;
        }

        return expression;
    }

    private boolean hasFlag(Pattern pattern, int flag) {
        return (pattern.flags() & flag) == flag;
    }

    @Override
    public void close() {
        scanner.close();
        database.close();
    }
}
