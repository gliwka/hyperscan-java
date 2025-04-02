package com.gliwka.hyperscan.wrapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ScannerTest {

    private Scanner scanner;
    private List<Expression> expressions;
    private Database database;

    @BeforeEach
    void setUp() {
        scanner = new Scanner();
        expressions = new ArrayList<>();
        expressions.add(new Expression("test", EnumSet.of(ExpressionFlag.SOM_LEFTMOST, ExpressionFlag.CASELESS), 0)); // ID 0
        expressions.add(new Expression("test1", ExpressionFlag.SOM_LEFTMOST, 1)); // ID 1
        expressions.add(new Expression("test3", ExpressionFlag.SOM_LEFTMOST, 2)); // ID 2
        expressions.add(new Expression("world", ExpressionFlag.SOM_LEFTMOST, 3)); // ID 3
        expressions.add(new Expression("你好", EnumSet.of(ExpressionFlag.SOM_LEFTMOST, ExpressionFlag.UTF8), 4)); // ID 4 (UTF-8)

        try {
            database = Database.compile(expressions);
            scanner.allocScratch(database); // Allocate scratch space
        } catch (CompileErrorException e) {
            fail("Database compilation failed", e);
        } catch (HyperscanException e) {
            fail("Scratch allocation failed", e);
        }
    }

    @AfterEach
    void tearDown() {
        try {
            if (database != null) {
                database.close();
            }
            if (scanner != null) {
                scanner.close(); // Ensure scratch space is freed
            }
        } catch (Exception e) {
            // Ignore cleanup exceptions in tests
        }
    }

    @Test
    void scanString_shouldFindMatches() {
        String text = "test test1 test test3";
        List<Match> matches = scanner.scan(database, text);

        // Assert count and specific matches with SOM applied to all
        assertThat(matches).hasSize(6);

        // Verify individual matches (using inclusive byte offsets)
        assertThat(matches).anySatisfy(m -> assertMatch(m, 0, 3, 0)); // test
        assertThat(matches).anySatisfy(m -> assertMatch(m, 5, 8, 0)); // test
        assertThat(matches).anySatisfy(m -> assertMatch(m, 5, 9, 1)); // test1
        assertThat(matches).anySatisfy(m -> assertMatch(m, 11, 14, 0)); // test
        assertThat(matches).anySatisfy(m -> assertMatch(m, 16, 19, 0)); // test
        assertThat(matches).anySatisfy(m -> assertMatch(m, 16, 20, 2)); // test3
    }

    @Test
    void scanString_utf8_shouldFindMatch() throws CompileErrorException {
        String text = "Say 你好 world";
        // Use only expressions relevant to the input
        List<Expression> utfExpressions = Arrays.asList(expressions.get(3), expressions.get(4)); // world (3, SOM), 你好 (4, SOM+UTF8)
        Database utfDb = Database.compile(utfExpressions);

        List<Match> matches = scanner.scan(utfDb, text);

        assertThat(matches).hasSize(2);

        // Filter for each match based on the original expression ID and assert
        assertThat(matches).filteredOn(m -> m.getMatchedExpression().getId() == 4) // ID 4 = "你好"
                .hasSize(1)
                .first()
                .satisfies(m -> assertMatch(m, 4, 5, 4));

        assertThat(matches).filteredOn(m -> m.getMatchedExpression().getId() == 3) // ID 3 = "world"
                .hasSize(1)
                .first()
                .satisfies(m -> assertMatch(m, 7, 11, 3));

        // Need to close the temporary DB
        utfDb.close();
    }

    @Test
    void scanString_noMatches_shouldReturnEmptyList() {
        String text = "This text contains none of the patterns.";
        List<Match> matches = scanner.scan(database, text);
        assertThat(matches).isEmpty();
    }

    @Test
    void scanString_emptyInput_shouldReturnEmptyList() {
        String text = "";
        List<Match> matches = scanner.scan(database, text);
        assertThat(matches).isEmpty();
    }

    @Test
    void scanStringWithHandler_shouldInvokeHandler() {
        String text = "test test1 test test3";
        AtomicInteger matchCount = new AtomicInteger(0);
        final List<String> matchedExprs = new ArrayList<>();

        StringMatchEventHandler handler = (expression, from, to) -> {
            matchCount.incrementAndGet();
            matchedExprs.add(expression.getExpression());
            return true;
        };

        scanner.scan(database, text, handler);

        assertThat(matchCount.get()).isEqualTo(6);
        // Order not guaranteed, check contents
        assertThat(matchedExprs).containsExactlyInAnyOrder("test", "test", "test1", "test", "test", "test3");
    }

    @Test
    void scanStringWithHandler_terminating_shouldStopEarly() {
        String text = "Test example789 world"; // Matches: Test, example789, world
        AtomicInteger matchCount = new AtomicInteger(0);

        // Handler terminates after the first match
        StringMatchEventHandler handler = (expression, from, to) -> {
            matchCount.incrementAndGet();
            return false;
        };

        scanner.scan(database, text, handler);

        // Should only find the first match ("Test") before stopping
        assertThat(matchCount.get()).isEqualTo(1);
    }

    @Test
    void scanBytesWithHandler_shouldInvokeHandler() {
        byte[] data = "test test1 test test3".getBytes(StandardCharsets.UTF_8);
        AtomicInteger matchCount = new AtomicInteger(0);
        final List<String> matchedExprs = new ArrayList<>();

        ByteMatchEventHandler handler = (expression, from, to) -> {
            matchCount.incrementAndGet();
            matchedExprs.add(expression.getExpression());
            return true;
        };

        scanner.scan(database, data, handler);

        assertThat(matchCount.get()).isEqualTo(6);
        // Order not guaranteed, check contents
        assertThat(matchedExprs).containsExactlyInAnyOrder("test", "test", "test1", "test", "test", "test3");
    }

    @Test
    void scanBytesWithHandler_utf8_shouldFindMatch() throws CompileErrorException {
        byte[] data = "Say 你好 world".getBytes(StandardCharsets.UTF_8);
        AtomicInteger matchCount = new AtomicInteger(0);
        final List<Long> matchPositions = new ArrayList<>();

        // Use specific DB for this test
        List<Expression> utfExpressions = Arrays.asList(expressions.get(3), expressions.get(4));
        Database utfDb = Database.compile(utfExpressions);

        ByteMatchEventHandler handler = (expression, from, to) -> {
            matchCount.incrementAndGet();
            matchPositions.add(from);
            matchPositions.add(to);
            return true;
        };

        scanner.scan(utfDb, data, handler); // Use utfDb

        assertThat(matchCount.get()).isEqualTo(2);
        // Note: Handler reports exclusive end offset (start_inclusive, end_exclusive)
        assertThat(matchPositions).containsExactlyInAnyOrder(4L, 10L, 11L, 16L);

        // Need to close the temporary DB
        utfDb.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Test", "A test string", "Another TEST"})
    void hasMatch_shouldReturnTrueIfMatchExists(String input) {
        assertTrue(scanner.hasMatch(database, input.getBytes(StandardCharsets.UTF_8)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Completely different", "No patterns here", ""})
    void hasMatch_shouldReturnFalseIfNoMatchExists(String input) {
        assertFalse(scanner.hasMatch(database, input.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void hasMatch_utf8_shouldReturnTrue() {
        assertTrue(scanner.hasMatch(database, "你好 world".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void scan_afterClose_shouldThrowException() throws IOException {
        scanner.close(); // Close the scanner (frees scratch)

        assertThrows(IllegalStateException.class, () -> {
            scanner.scan(database, "some text");
        });

        assertThrows(IllegalStateException.class, () -> {
            scanner.scan(database, "some text".getBytes(), (expr, from, to) -> true);
        });

        assertThrows(IllegalStateException.class, () -> {
            scanner.hasMatch(database, "some text".getBytes());
        });
    }

    @Test
    void allocScratch_afterClose_shouldThrowException() throws IOException {
        scanner.close();
        assertThrows(IllegalStateException.class, () -> {
            scanner.allocScratch(database);
        });
    }

    @Test
    void doubleClose_shouldBeSafe() throws IOException {
        scanner.close();
        scanner.close(); // Closing again should not throw RuntimeException. IOException is declared.
    }

    @Test
    void scan_recursiveCall_shouldThrowException() {
        String text = "test world";
        AtomicBoolean insideScan = new AtomicBoolean(false);

        StringMatchEventHandler handler = (expression, from, to) -> {
            if (insideScan.get()) {
                fail("Recursive call detected unexpectedly.");
            }
            insideScan.set(true);
            try {
                // Attempt recursive scan
                assertThrows(IllegalStateException.class, () -> {
                    scanner.scan(database, "another test");
                }, "Recursive scanning should be disallowed.");
            } finally {
                insideScan.set(false);
            }
            return true; // Continue scanning
        };

        // Initial scan triggers the handler
        scanner.scan(database, text, handler);
    }

    @Test
    void scanString_nonSOM_shouldReportStartZero() throws HyperscanException, CompileErrorException, IOException {
        // Test that a non-SOM expression anchored to the start reports start=0
        Expression nonSomExpr = new Expression("^start", 5); // Use a unique ID
        Database nonSomDb = null;
        Scanner nonSomScanner = new Scanner(); // Use a separate scanner/scratch
        try {
            nonSomDb = Database.compile(Collections.singletonList(nonSomExpr));
            nonSomScanner.allocScratch(nonSomDb);
            String text = "start middle end";

            List<Match> matches = nonSomScanner.scan(nonSomDb, text);

            // Expect exactly one match because of ^ anchor
            assertThat(matches).hasSize(1);
            // Crucially, assert the start position is 0 for the non-SOM match
            assertThat(matches.get(0).getStartPosition()).isEqualTo(0);
            // End position should be the end of "start" (inclusive byte offset)
            assertThat(matches.get(0).getEndPosition()).isEqualTo(4);
            assertThat(matches.get(0).getMatchedExpression().getId()).isEqualTo(5);
        } finally {
            if (nonSomDb != null) nonSomDb.close();
            nonSomScanner.close();
        }
    }

    // Test static methods (don't require setup/teardown)
    @Test
    void getVersion_shouldReturnString() {
        String version = Scanner.getVersion();
        assertThat(version).isNotNull().isNotEmpty();
        assertThat(version).matches("\\d+\\.\\d+\\.\\d+.*"); // Hyperscan version format
    }

    @Test
    void getIsValidPlatform_shouldReturnBoolean() {
        // We can't know the expected value, but we can check it returns a boolean
        assertDoesNotThrow(() -> {
            boolean isValid = Scanner.getIsValidPlatform();
            assertThat(isValid).isTrue();
        });
    }

    // Helper method for assertions
    private void assertMatch(Match m, long start, long end, int expressionIndex) {
        assertEquals(start, m.getStartPosition(), "Start position mismatch");
        assertEquals(end, m.getEndPosition(), "End position mismatch (inclusive)");
        assertEquals(expressions.get(expressionIndex), m.getMatchedExpression(), "Expression mismatch");
    }

    private void assertMatchById(Match m, long start, long end, int expressionId) {
        assertEquals(start, m.getStartPosition(), "Start position mismatch");
        assertEquals(end, m.getEndPosition(), "End position mismatch (inclusive)");
        assertEquals(expressionId, m.getMatchedExpression().getId(), "Expression ID mismatch");
    }

    @Test
    void scanString_singleExpression_shouldFindMatches() throws CompileErrorException {
        Expression expression = new Expression("Te?st", EnumSet.of(ExpressionFlag.CASELESS, ExpressionFlag.SOM_LEFTMOST), 100);
        try (Database db = Database.compile(expression)) {
            scanner.allocScratch(db); // Re-alloc scratch for the new DB

            String text = "Dies ist ein Test tst.";
            List<Match> matches = scanner.scan(db, text);

            assertThat(matches).hasSize(2);

            // Using ID based assertion
            assertThat(matches.get(0)).satisfies(m -> assertMatchById(m, 13, 16, 100));
            assertThat(matches.get(0).getMatchedString()).isEqualTo("Test");

            assertThat(matches.get(1)).satisfies(m -> assertMatchById(m, 18, 20, 100));
            assertThat(matches.get(1).getMatchedString()).isEqualTo("tst");

            scanner.allocScratch(database); // Re-alloc scratch for the default DB for other tests
        }
    }

    @Test
    void scanString_emptyPatternAllowsEmptyMatch() throws CompileErrorException {
        Expression expression = new Expression(".*", EnumSet.of(ExpressionFlag.ALLOWEMPTY), 101);
        try (Database db = Database.compile(expression)) {
            scanner.allocScratch(db);
            String text = "";
            List<Match> matches = scanner.scan(db, text);
            assertThat(matches).hasSize(1);
            assertThat(matches.get(0)).satisfies(m -> {
                assertMatchById(m, 0, 0, 101);
                assertThat(m.getMatchedString()).isEqualTo("");
            });
            scanner.allocScratch(database);
        }
    }

    @Test
    void scanString_nonEmptyPatternDisallowsEmptyMatch() throws CompileErrorException {
        Expression expression = new Expression(".+", EnumSet.of(ExpressionFlag.ALLOWEMPTY, ExpressionFlag.SOM_LEFTMOST), 102);
        try (Database db = Database.compile(expression)) {
            scanner.allocScratch(db);
            String text = "";
            List<Match> matches = scanner.scan(db, text);
            assertThat(matches).isEmpty();
            scanner.allocScratch(database);
        }
    }

    @Test
    void scanString_multiExpression_shouldFindMatches() throws CompileErrorException {
        List<Expression> multiExpressions = new ArrayList<>();
        EnumSet<ExpressionFlag> flags = EnumSet.of(ExpressionFlag.CASELESS, ExpressionFlag.SOM_LEFTMOST);
        multiExpressions.add(new Expression("Te?st", flags, 200));
        multiExpressions.add(new Expression("ist", flags, 201));

        try (Database db = Database.compile(multiExpressions)) {
            scanner.allocScratch(db);
            String text = "Dies ist ein Test tst.";
            List<Match> matches = scanner.scan(db, text);

            assertThat(matches).hasSize(3);

            // Note: Order depends on Hyperscan implementation details (e.g., leftmost-longest?)
            // Assert based on expression ID
            assertThat(matches).filteredOn(m -> m.getMatchedExpression().getId() == 201)
                    .hasSize(1)
                    .first()
                    .satisfies(m -> {
                        assertMatchById(m, 5, 7, 201);
                        assertThat(m.getMatchedString()).isEqualTo("ist");
                    });

            assertThat(matches).filteredOn(m -> m.getMatchedExpression().getId() == 200)
                    .hasSize(2);
            // Can't guarantee order of the two "Test"/"tst" matches

            scanner.allocScratch(database);
        }
    }

    @Test
    void scanString_expressionWithId_shouldMatchCorrectId() throws CompileErrorException {
        Expression expression = new Expression("test", ExpressionFlag.SOM_LEFTMOST, 17);
        try (Database db = Database.compile(expression)) {
            scanner.allocScratch(db);
            String text = "12345 test string";
            List<Match> matches = scanner.scan(db, text);
            assertThat(matches).hasSize(1);
            assertThat(matches.get(0)).satisfies(m -> {
                assertMatchById(m, 6, 9, 17);
                assertThat(m.getMatchedString()).isEqualTo("test");
            });
            scanner.allocScratch(database);
        }
    }

    @Test
    void scanString_utf8Digits_shouldMatchCorrectStringAndPosition() throws CompileErrorException {
        Expression expr = new Expression("\\d{5}", EnumSet.of(ExpressionFlag.SOM_LEFTMOST, ExpressionFlag.UTF8), 300);
        try (Database db = Database.compile(expr)) {
            scanner.allocScratch(db);
            // Input requires careful handling of UTF-8 chars vs surrogate pairs vs multi-byte chars
            String text = "58744 78524 \uD83D\uDE00The quick brown fox ◌\uD804\uDD00 jumps 06840 over the lazy dog༼؈";

            List<Match> matches = scanner.scan(db, text);
            assertThat(matches).hasSize(3);

            assertThat(matches.get(2)).satisfies(m -> {
                assertMatchById(m, 44, 48, 300); // Reverted back to original assertion
                assertThat(m.getMatchedString()).isEqualTo("06840");
            });
            scanner.allocScratch(database);
        }
    }

    @Test
    void scanWithOneScannerAndMultipleDatabases() throws Exception {
        Expression exprA = new Expression("a", ExpressionFlag.SOM_LEFTMOST, 500);
        Expression exprB = new Expression("b", ExpressionFlag.SOM_LEFTMOST, 501);

        try (
                Database dbA = Database.compile(exprA);
                Database dbB = Database.compile(exprB)
        ) {
            // Scan with dbA
            scanner.allocScratch(dbA);
            List<Match> matchesA = scanner.scan(dbA, "a");
            assertThat(matchesA).hasSize(1);
            assertThat(matchesA.get(0)).satisfies(m -> assertMatchById(m, 0, 0, 500));

            // Scan with dbB
            scanner.allocScratch(dbB);
            List<Match> matchesB = scanner.scan(dbB, "b");
            assertThat(matchesB).hasSize(1);
            assertThat(matchesB.get(0)).satisfies(m -> assertMatchById(m, 0, 0, 501));

            // Restore scratch for default DB
            scanner.allocScratch(database);
        }
    }


    @Test
    void scanString_potentiallyInfinitePattern_shouldComplete() throws CompileErrorException {
        Expression expression = new Expression("a|", EnumSet.of(ExpressionFlag.ALLOWEMPTY), 400);
        try (Database db = Database.compile(expression)) {
            scanner.allocScratch(db);
            String text = "bbbbbbb";
            assertThat(scanner.hasMatch(db, text)).isTrue(); // Check scan completed
        }
    }

    @Test
    void canCreateHighNumberOfScannersWithoutRunningOutOfNativeHandles() throws CompileErrorException, IOException {
        // Test resource cleanup: Ensure creating many scanners and databases
        // and allocating scratch space doesn't exhaust native resources.
        for (int i = 0; i < 100_000; i++) {
            try (Scanner scanner = new Scanner();
                 Database db = Database.compile(new Expression("a"))) {
                scanner.allocScratch(db);
                // Perform a minimal scan to ensure scratch is usable
                assertFalse(scanner.scan(db, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa").isEmpty());
                // Resources are released by try-with-resources
            }
        }
    }

    @Test
    void scanString_unknownUtf8Character() throws CompileErrorException {
        Expression expression = new Expression("Hallo", EnumSet.of(ExpressionFlag.UTF8, ExpressionFlag.SOM_LEFTMOST), 700);
        try (Database db = Database.compile(expression)) {
            scanner.allocScratch(db);
            String text = "\uD83D\uDE00Hallo";
            List<Match> matches = scanner.scan(db, text);
            assertThat(matches).hasSize(1);
            assertThat(matches.get(0)).satisfies(m -> {
                assertMatchById(m, 2, 6, 700);
                assertThat(m.getMatchedString()).isEqualTo("\uD83D\uDE00");
            });
            scanner.allocScratch(database);
        }
    }
}
