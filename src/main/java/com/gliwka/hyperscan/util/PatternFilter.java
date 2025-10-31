package com.gliwka.hyperscan.util;

import com.gliwka.hyperscan.wrapper.*;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filters a list of {@link java.util.regex.Pattern} instances using Hyperscan
 * to quickly identify potential matches before using the standard Java Pattern API.
 * <p>
 * This class allows leveraging Hyperscan's performance for pre-filtering a large
 * set of patterns against an input string. Only the patterns flagged as potential
 * matches by Hyperscan (along with any patterns Hyperscan couldn't analyze, see
 * {@link #notFilterable}) are returned for final matching using {@link Matcher#find()}
 * or {@link Matcher#matches()}. This approach can significantly speed up scenarios
 * where many patterns need to be checked against input text.
 * <p>
 * This implementation is similar in concept to Hyperscan's "chimera" library,
 * but uses the Java {@code java.util.regex} API.
 * <p>
 * <b>Note:</b> This class is not thread-safe. Each thread should use its own
 * instance.
 */
public class PatternFilter implements Closeable {
    private final Matcher[] matchers;
    private final Scanner scanner;
    private final Database database;

    /**
     * Stores Matcher instances for patterns that could not be processed by Hyperscan's
     * PREFILTER mode (i.e., {@link Expression#validate()} returned false).
     * These patterns are always included in the results of {@link #filter(String)},
     * as their potential match status cannot be pre-determined by Hyperscan.
     */
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
            Expression expression = ExpressionUtil.mapToExpression(pattern, id);

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

    @Override
    public void close() throws IOException {
        scanner.close();
        database.close();
    }
}
