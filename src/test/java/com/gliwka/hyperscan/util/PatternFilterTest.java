package com.gliwka.hyperscan.util;

import com.gliwka.hyperscan.wrapper.CompileErrorException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;

class PatternFilterTest {

    @Test
    void readmeSample() throws CompileErrorException {
        List<Pattern> patterns = asList(
                Pattern.compile("The number is ([0-9]+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("The color is (blue|red|orange)")
                // and thousands more
        );

        //not thread-safe, create per thread
        PatternFilter filter = new PatternFilter(patterns);

        //this list now only contains the probably matching patterns, in this case the first one
        List<Matcher> matchers = filter.filter("The number is 7 the NUMber is 27");

        //now we use the regular java regex api to check for matches- this is not hyperscan specific
        for(Matcher matcher : matchers) {
            while (matcher.find()) {
                // will print 7 and 27
                System.out.println(matcher.group(1));
            }
        }
    }

    @Test
    void filterNotMatchingPatterns() throws CompileErrorException {
        List<Pattern> patterns = asList(
                Pattern.compile("The number is ([0-9]+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("The color is (blue|red|orange)")
        );

        PatternFilter filter = new PatternFilter(patterns);

        List<Matcher> matchers = filter.filter("The color is orange");

        assertHasPattern(patterns.get(1), matchers);
    }

    @Test
    void handleFlagsProperly() throws CompileErrorException {
        List<Pattern> patterns = asList(
            Pattern.compile("The number is ([0-9]+)"),
            Pattern.compile("The number is ([0-9]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^The color is (blue|red|orange)$"),
            Pattern.compile("^The color is (blue|red|orange)$", Pattern.MULTILINE),
            Pattern.compile("something.else"),
            Pattern.compile("something.else", Pattern.DOTALL),
            Pattern.compile("match.THIS", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
        );

        PatternFilter filter = new PatternFilter(patterns);

        List<Matcher> matchers = filter.filter("tHE nuMBeR is 17");
        assertHasPattern(patterns.get(1), matchers);
        assertEquals(1, matchers.size());

        matchers = filter.filter("The number is 17");
        assertHasPattern(patterns.get(0), matchers);
        assertHasPattern(patterns.get(1), matchers);
        assertEquals(2, matchers.size());

        matchers = filter.filter("Some text\nThe color is red");
        assertHasPattern(patterns.get(3), matchers);
        assertEquals(1, matchers.size());

        matchers = filter.filter("something\nelse");
        assertHasPattern(patterns.get(5), matchers);
        assertEquals(1, matchers.size());

        matchers = filter.filter("match\nthiS");
        assertHasPattern(patterns.get(6), matchers);
        assertEquals(1, matchers.size());
    }

    private void assertHasPattern(Pattern pattern, List<Matcher> matchers) {
        List<Pattern> filteredPatterns = matchers.stream().map(Matcher::pattern).collect(toList());
        assertTrue(filteredPatterns.contains(pattern));
    }
}