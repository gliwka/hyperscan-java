package com.gliwka.hyperscan.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for ScopedPatternFilterImpl.
 * <p>
 * NOTE: These tests require at least one Hyperscan-compatible pattern to successfully
 * initialize the underlying Hyperscan database. By mixing compatible and incompatible
 * patterns, we can test the filtering logic without mocking.
 */
class ScopedPatternFilterImplTest {

    // A simple pattern that is compatible with Hyperscan.
    private final Pattern compatiblePattern = Pattern.compile("foobar");
    // A pattern with a lookbehind, which is not supported by Hyperscan.
    private final Pattern incompatiblePattern = Pattern.compile("a++");

    private ScopedPatternFilterImpl<Pattern> filter;

    @BeforeEach
    void setUp() throws Exception {
        // Initialize with a mix of patterns. This ensures the Hyperscan database
        // can be compiled with at least one valid expression.
        List<Pattern> allPatterns = Arrays.asList(compatiblePattern, incompatiblePattern);
        filter = new ScopedPatternFilterImpl<>(allPatterns, Function.identity());
    }

    @AfterEach
    void tearDown() throws IOException {
        if (filter != null) {
            filter.close();
        }
    }

    @Test
    void filter_whenMatchOccurs_shouldReturnMatchingPatternAndAllIncompatiblePatterns() {
        List<Pattern> result = filter.filter("some text with foobar inside");

        // Expecting the pattern that matched ("foobar") AND the pattern that couldn't be filtered.
        assertThat(result).containsExactlyInAnyOrder(compatiblePattern, incompatiblePattern);
    }

    @Test
    void filter_whenNoMatchOccurs_shouldReturnOnlyIncompatiblePatterns() {
        List<Pattern> result = filter.filter("some text with no matches");

        // No compatible patterns matched, so we only get the fallback "not filterable" pattern.
        // This is the corrected test for the previously failing logic.
        assertThat(result).containsExactly(incompatiblePattern);
    }

    @Test
    void filter_shouldThrowIllegalStateExceptionIfClosed() throws IOException {
        filter.close();

        assertThatThrownBy(() -> filter.filter("some input")).isInstanceOf(IllegalStateException.class).hasMessage("Pattern filter is closed.");
    }

    @Test
    void getCloseAction_shouldReturnRunnableThatClosesFilter() {
        Runnable closeAction = filter.getCloseAction();

        // Run the close action
        closeAction.run();

        // After running, the filter should be closed
        assertThatThrownBy(() -> filter.filter("test")).isInstanceOf(IllegalStateException.class).hasMessage("Pattern filter is closed.");
    }
}