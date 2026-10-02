package com.gliwka.hyperscan.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Tests for {@link PatternOnlyFilter}, the fallback used when no pattern is filterable by
 * Hyperscan: it returns every pattern as a candidate regardless of input and holds no native
 * resources to close.
 */
class PatternOnlyFilterTest {

    private final List<String> patterns = Arrays.asList("alpha", "beta");

    @Test
    void filter_shouldReturnAllPatternsRegardlessOfInput() {
        PatternOnlyFilter<String> filter = new PatternOnlyFilter<>(patterns);

        assertThat(filter.filter("anything")).containsExactly("alpha", "beta");
        assertThat(filter.filter("")).containsExactly("alpha", "beta");
    }

    @Test
    void filter_shouldReturnAnUnmodifiableList() {
        PatternOnlyFilter<String> filter = new PatternOnlyFilter<>(patterns);

        List<String> result = filter.filter("anything");

        assertThatThrownBy(() -> result.add("gamma"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void close_shouldBeNoOp() {
        PatternOnlyFilter<String> filter = new PatternOnlyFilter<>(patterns);

        assertDoesNotThrow(filter::close);
        // Still usable after close, since it owns no resources.
        assertThat(filter.filter("anything")).containsExactly("alpha", "beta");
    }
}
