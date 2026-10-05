package com.gliwka.hyperscan.util;

import com.gliwka.hyperscan.wrapper.Database;
import com.gliwka.hyperscan.wrapper.Expression;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ScopedPatternFilterImpl}.
 * <p>
 * The implementation operates over a pre-compiled, shared {@link Database} owned by the factory.
 * These tests build that database directly so the impl can be exercised in isolation, mixing one
 * Hyperscan-compatible pattern with one incompatible pattern to cover both candidate sources.
 */
class ScopedPatternFilterImplTest {

    // Compatible with Hyperscan's prefilter mode.
    private final Pattern compatiblePattern = Pattern.compile("foobar");
    // \R (any Unicode linebreak) is not supported by Hyperscan, so it is always a candidate.
    private final Pattern incompatiblePattern = Pattern.compile("\\R");

    private final List<Pattern> filterable = Collections.singletonList(compatiblePattern);
    private final List<Pattern> notFilterable = Collections.singletonList(incompatiblePattern);

    private final AtomicBoolean databaseClosed = new AtomicBoolean(false);
    private Database database;
    private ScopedPatternFilterImpl<Pattern> filter;

    @BeforeEach
    void setUp() throws Exception {
        Expression expression = ExpressionUtil.mapToExpression(compatiblePattern, 0);
        database = Database.compile(Collections.singletonList(expression));
        filter = new ScopedPatternFilterImpl<>(database, databaseClosed, filterable, notFilterable);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (filter != null) {
            filter.close();
        }
        if (database != null) {
            database.close();
        }
    }

    @Test
    void filter_whenMatchOccurs_shouldReturnMatchedAndIncompatiblePatterns() {
        List<Pattern> result = filter.filter("some text with foobar inside");

        // The matched compatible pattern plus the always-included incompatible pattern.
        assertThat(result).containsExactlyInAnyOrder(compatiblePattern, incompatiblePattern);
    }

    @Test
    void filter_whenNoMatchOccurs_shouldReturnOnlyIncompatiblePatterns() {
        List<Pattern> result = filter.filter("nothing of interest here");

        assertThat(result).containsExactly(incompatiblePattern);
    }

    @Test
    void filter_shouldRejectNullInput() {
        assertThatThrownBy(() -> filter.filter(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("The input string must not be null.");
    }

    @Test
    void filter_shouldThrowWhenFilterIsClosed() throws IOException {
        filter.close();

        assertThatThrownBy(() -> filter.filter("foobar"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("This pattern filter has already been closed.");
    }

    @Test
    void filter_shouldThrowWhenBackingDatabaseIsClosed() {
        databaseClosed.set(true);

        assertThatThrownBy(() -> filter.filter("foobar"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The backing ScopedPatternFilterFactory has already been closed.");
    }

    @Test
    void getCloseAction_shouldReturnRunnableThatClosesTheFilter() {
        Runnable closeAction = filter.getCloseAction();

        closeAction.run();

        assertThatThrownBy(() -> filter.filter("foobar"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("This pattern filter has already been closed.");
    }

    @Test
    void getCloseAction_shouldBeIdempotent() {
        Runnable closeAction = filter.getCloseAction();

        closeAction.run();
        // A second run (e.g. close() followed by the cleaner firing) must not throw.
        closeAction.run();

        assertThatThrownBy(() -> filter.filter("foobar"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void filter_withNonMatchingFilterablePattern_isReportedByHyperscan() throws Exception {
        // A second compatible pattern that does not match the input is correctly excluded,
        // proving the result reflects actual Hyperscan matches rather than all filterable patterns.
        Pattern other = Pattern.compile("widget");
        List<Pattern> twoFilterable = asList(compatiblePattern, other);
        Database db = Database.compile(asList(
                ExpressionUtil.mapToExpression(compatiblePattern, 0),
                ExpressionUtil.mapToExpression(other, 1)));
        try (ScopedPatternFilterImpl<Pattern> f =
                     new ScopedPatternFilterImpl<>(db, new AtomicBoolean(false), twoFilterable, Collections.emptyList())) {
            assertThat(f.filter("only foobar here")).containsExactly(compatiblePattern);
        } finally {
            db.close();
        }
    }
}
