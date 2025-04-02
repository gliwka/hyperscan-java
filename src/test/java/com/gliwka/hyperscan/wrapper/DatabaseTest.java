package com.gliwka.hyperscan.wrapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseTest {

    private List<Expression> expressions;
    private Path tempDbFile;

    @BeforeEach
    void setUp() throws IOException {
        expressions = new ArrayList<>();
        expressions.add(new Expression("test", EnumSet.of(ExpressionFlag.CASELESS)));
        expressions.add(new Expression("[0-9]+"));
        tempDbFile = Files.createTempFile("hyperscan-db-test-", ".db");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(tempDbFile);
    }

    @Test
    void compileSingleExpression() throws CompileErrorException {
        Expression expression = new Expression("test", EnumSet.of(ExpressionFlag.CASELESS));
        try (Database db = Database.compile(expression)) {
            assertNotNull(db);
            assertThat(db.getSize()).isGreaterThan(0);
        }
    }

    @Test
    void compileMultipleExpressions() throws CompileErrorException {
        try (Database db = Database.compile(expressions)) {
            assertNotNull(db);
            assertThat(db.getSize()).isGreaterThan(0);
        }
    }

    @Test
    void compileInvalidExpressionShouldThrow() {
        Expression invalidExpression = new Expression("test("); // Unmatched parenthesis
        CompileErrorException exception = assertThrows(CompileErrorException.class, () -> {
            Database.compile(invalidExpression);
        });
        assertThat(exception.getFailedExpression()).isEqualTo(invalidExpression);
        assertThat(exception.getMessage()).contains("Missing close parenthesis for group started at index 4.");
    }

    @Test
    void compileWithNullExpressionShouldThrow() {
        assertThrows(NullPointerException.class, () -> Database.compile((Expression) null));
    }

    @Test
    void compileWithNullExpressionListShouldThrow() {
        assertThrows(NullPointerException.class, () -> Database.compile((List<Expression>) null));
    }

    @Test
    void compileWithEmptyExpressionListShouldThrow() {
         assertThrows(CompileErrorException.class, () -> Database.compile(Collections.emptyList()));
    }

    @Test
    void databaseClose() throws CompileErrorException {
        Database db = Database.compile(expressions);
        assertDoesNotThrow(db::close); // First close should succeed
        assertDoesNotThrow(db::close); // Subsequent closes should be no-ops
    }

    @Test
    void compileWithDuplicateExpressionIdsShouldThrow() {
        List<Expression> duplicateIdExpressions = Arrays.asList(
                new Expression("pattern1", EnumSet.noneOf(ExpressionFlag.class), 1),
                new Expression("pattern2", EnumSet.noneOf(ExpressionFlag.class), 1) // Same ID
        );
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            Database.compile(duplicateIdExpressions);
        });
        assertThat(exception.getMessage()).contains("Expression ID must be unique");
    }

    @Test
    void compileWithMixedIdPresenceShouldThrow() throws CompileErrorException {
        List<Expression> mixedIds = Arrays.asList(
                new Expression("pattern1"), // No ID
                new Expression("pattern2", EnumSet.noneOf(ExpressionFlag.class), 1) // Has ID
        );

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            Database.compile(mixedIds);
        });
        assertThat(exception.getMessage()).contains("You can't mix expressions with and without id's in a single database");
    }

    @Test
    void getExpressionById() throws CompileErrorException {
        List<Expression> expressionsWithIds = Arrays.asList(
                new Expression("abc", EnumSet.noneOf(ExpressionFlag.class), 10),
                new Expression("def", EnumSet.noneOf(ExpressionFlag.class), 20)
        );
        try (Database db = Database.compile(expressionsWithIds)) {
            Expression expr10 = db.getExpression(10);
            Expression expr20 = db.getExpression(20);
            Expression exprMissing = db.getExpression(99);

            assertNotNull(expr10);
            assertEquals("abc", expr10.getExpression());
            assertEquals(Integer.valueOf(10), expr10.getId());

            assertNotNull(expr20);
            assertEquals("def", expr20.getExpression());
            assertEquals(Integer.valueOf(20), expr20.getId());

            assertNull(exprMissing, "Should return null for non-existent ID");
        }
    }

    @Test
    void getExpressionByIndexWhenNoIdsProvided() throws CompileErrorException {
         // Using the expressions list from setUp which has no IDs
        try (Database db = Database.compile(expressions)) {
             Expression expr0 = db.getExpression(0);
             Expression expr1 = db.getExpression(1);
             Expression exprMissing = db.getExpression(2);

             assertNotNull(expr0);
             assertEquals("test", expr0.getExpression());
             assertNull(expr0.getId());

             assertNotNull(expr1);
             assertEquals("[0-9]+", expr1.getExpression());
             assertNull(expr1.getId());

             assertNull(exprMissing, "Should return null for out-of-bounds index");
        }
    }

    @Test
    void getSizeAfterCloseShouldThrow() throws CompileErrorException {
        Database db = Database.compile(expressions);
        db.close();
        assertThrows(IllegalStateException.class, db::getSize,
                "getSize should throw IllegalStateException after close");
    }

    @Test
    void testSerializationDeserializationRoundtrip() throws CompileErrorException, IOException, ClassNotFoundException {
        // 1. Setup expressions with IDs and flags
        List<Expression> expressionsWithIds = Arrays.asList(
                new Expression("Word\\d+", EnumSet.of(ExpressionFlag.CASELESS, ExpressionFlag.SOM_LEFTMOST), 100),
                new Expression("\\d{3}-\\d{2}-\\d{4}", EnumSet.noneOf(ExpressionFlag.class), 200)
        );
        String inputText = "Test Word1 123-45-6789 Word2 end";

        Database originalDb = null;
        Database deserializedDb = null;
        List<Match> expectedMatches;

        try {
            // 2. Compile original DB and get expected matches
            originalDb = Database.compile(expressionsWithIds);
            try (Scanner scanner = new Scanner()) {
                scanner.allocScratch(originalDb);
                expectedMatches = scanner.scan(originalDb, inputText);
                assertThat(expectedMatches).hasSize(3); // Word1, 123-45-6789, Word2
            }

            // 3. Serialize the database to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            originalDb.save(baos);
            byte[] serializedBytes = baos.toByteArray();
            assertThat(serializedBytes.length).isGreaterThan(0);

            // 4. Deserialize the database from byte array
            ByteArrayInputStream bais = new ByteArrayInputStream(serializedBytes);
            deserializedDb = Database.load(bais);
            assertThat(deserializedDb).isNotNull();

            // 5. Verify restored expressions
            assertThat(deserializedDb.getSize()).isEqualTo(originalDb.getSize());

            Expression expr100 = deserializedDb.getExpression(100);
            assertNotNull(expr100);
            assertEquals("Word\\d+", expr100.getExpression());
            assertEquals(Integer.valueOf(100), expr100.getId());
            assertThat(expr100.getFlags()).containsExactlyInAnyOrder(ExpressionFlag.CASELESS, ExpressionFlag.SOM_LEFTMOST);

            Expression expr200 = deserializedDb.getExpression(200);
            assertNotNull(expr200);
            assertEquals("\\d{3}-\\d{2}-\\d{4}", expr200.getExpression());
            assertEquals(Integer.valueOf(200), expr200.getId());
            assertThat(expr200.getFlags()).isEmpty();

            // 6. Verify matching behavior of deserialized DB
            try (Scanner scanner = new Scanner()) {
                scanner.allocScratch(deserializedDb);
                List<Match> actualMatches = scanner.scan(deserializedDb, inputText);
                // Use AssertJ's recursive comparison for complex objects like Match
                assertThat(actualMatches).usingRecursiveComparison().isEqualTo(expectedMatches);
            }

        } finally {
            if (originalDb != null) {
                originalDb.close();
            }
            if (deserializedDb != null) {
                deserializedDb.close();
            }
        }
    }
}
