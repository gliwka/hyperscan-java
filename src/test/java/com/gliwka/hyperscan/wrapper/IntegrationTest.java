package com.gliwka.hyperscan.wrapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.*;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationTest {

    @TestWithDatabaseRoundtrip
    void simpleSingleExpression(SerializeDatabase serialize) {
        EnumSet<ExpressionFlag> flags = EnumSet.of(ExpressionFlag.CASELESS, ExpressionFlag.SOM_LEFTMOST);
        Expression expression = new Expression("Te?st", flags);
        Expression.ValidationResult result = expression.validate();
        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());
        try {
            Database db = roundTrip(Database.compile(expression), serialize);
            assertTrue(db.getSize() > 0);
            Scanner scanner = new Scanner();
            scanner.allocScratch(db);
            List<Match> matches = scanner.scan(db, "Dies ist ein Test tst.");
            assertEquals(matches.size(), 2);
            assertEquals(matches.get(0).getEndPosition(), 16);
            assertEquals(matches.get(0).getStartPosition(), 13);
            assertEquals(matches.get(0).getMatchedString(), "Test");
            assertEquals(matches.get(0).getMatchedExpression(), expression);
            assertTrue(scanner.getSize() > 0);
        }
        catch (Exception e) {
            fail(e);
        }

        Expression invalidExpression = new Expression("test\\1", EnumSet.noneOf(ExpressionFlag.class));
        Expression.ValidationResult invalidResult = invalidExpression.validate();
        assertFalse(invalidResult.isValid());
        assertTrue(invalidResult.getErrorMessage().length() > 0);

        try {
            Database.compile(invalidExpression);
            fail("No exception was thrown");
        } catch (CompileErrorException e) {
            //expected
        }
    }

    @TestWithDatabaseRoundtrip
    void simpleMultiExpression(SerializeDatabase serialize) {
        LinkedList<Expression> expressions = new LinkedList<Expression>();

        EnumSet<ExpressionFlag> flags = EnumSet.of(ExpressionFlag.CASELESS, ExpressionFlag.SOM_LEFTMOST);

        expressions.add(new Expression("Te?st", flags));
        expressions.add(new Expression("ist", flags));

        try {
            Database db = roundTrip(Database.compile(expressions), serialize);
            assertTrue(db.getSize() > 0);
            Scanner scanner = new Scanner();
            scanner.allocScratch(db);
            List<Match> matches = scanner.scan(db, "Dies ist ein Test tst.");
            assertEquals(3, matches.size());
            assertEquals(7, matches.get(0).getEndPosition());
            assertEquals(5, matches.get(0).getStartPosition());
            assertEquals("ist", matches.get(0).getMatchedString());
            assertEquals(expressions.get(1), matches.get(0).getMatchedExpression());
            assertTrue(scanner.getSize() > 0);
        }
        catch (Exception e) {
            fail(e);
        }

        expressions.add(new Expression("invalid\\1", flags));
        try {
            Database db = Database.compile(expressions);
            fail("Should never reach here");
        }
        catch(Exception e) {
            //expected
        }
    }

    @TestWithDatabaseRoundtrip
    void expressionWithId(SerializeDatabase serialize) {
        try {
            Database db = roundTrip(Database.compile(Expression.builder().expression("test").id(17).build()), serialize);
            Scanner scanner = new Scanner();
            scanner.allocScratch(db);
            List<Match> matches = scanner.scan(db, "12345 test string");
            assertEquals(17, matches.get(0).getMatchedExpression().getId());
        }
        catch(Exception e) {
            fail(e);
        }
    }

    @TestWithDatabaseRoundtrip
    void infiniteRegex(SerializeDatabase serialize) {
        try {
            Database db = roundTrip(Database.compile(new Expression("a|", EnumSet.of(ExpressionFlag.ALLOWEMPTY))), serialize);
            Scanner scanner = new Scanner();
            scanner.allocScratch(db);
            List<Match> matches = scanner.scan(db, "12345 test string");
        }
        catch(Exception e) {
            fail(e);
        }
    }

    @Test
    void nullExpression() {
        try {
            Database db = Database.compile(new Expression(null));
            fail("Should never reach here");
        }
        catch(NullPointerException n) {
            //expected
        }
        catch(Exception t) {
            fail(t);
        }
    }

    @TestWithDatabaseRoundtrip
    void emptyStringMatch(SerializeDatabase serialize) {
        try {

            Database db = roundTrip(Database.compile(new Expression(".*", ExpressionFlag.ALLOWEMPTY)), serialize);
            Scanner scanner = new Scanner();
            scanner.allocScratch(db);
            final List<Match> matcher = scanner.scan(db,"");
            assertTrue(matcher.size() > 0);
            assertEquals("", matcher.get(0).getMatchedString());

        }
        catch(Exception t) {
            fail(t);
        }
    }

    @TestWithDatabaseRoundtrip
    void emptyStringNoMatch(SerializeDatabase serialize) {
        try {

            Database db = roundTrip(Database.compile(new Expression(".+", ExpressionFlag.ALLOWEMPTY)), serialize);
            Scanner scanner = new Scanner();
            scanner.allocScratch(db);
            final List<Match> matcher = scanner.scan(db,"");
            assertTrue(matcher.isEmpty());

        }
        catch(Exception t) {
            fail(t);
        }
    }

    @Test
    void readmeExample() {
        //we define a list containing all of our expressions
        LinkedList<Expression> expressions = new LinkedList<Expression>();

        //the first argument in the constructor is the regular pattern, the latter one is a expression flag
        //make sure you read the original hyperscan documentation to learn more about flags
        //or browse the ExpressionFlag.java in this repo.
        expressions.add(new Expression("[0-9]{5}", EnumSet.of(ExpressionFlag.SOM_LEFTMOST)));
        expressions.add(new Expression("Test", EnumSet.of(ExpressionFlag.CASELESS)));


        //we precompile the expression into a database.
        //you can compile single expression instances or lists of expressions

        //since we're interacting with native handles always use try-with-resources or call the close method after use
        try(Database db = Database.compile(expressions)) {
            //initialize scanner - one scanner per thread!
            //same here, always use try-with-resources or call the close method after use
            try(Scanner scanner = new Scanner())
            {
                //allocate scratch space matching the passed database
                scanner.allocScratch(db);


                //provide the database and the input string
                //returns a list with matches
                //synchronized method, only one execution at a time (use more scanner instances for multithreading)
                List<Match> matches = scanner.scan(db, "12345 test string");

                //matches always contain the expression causing the match and the end position of the match
                //the start position and the matches string it self is only part of a matach if the
                //SOM_LEFTMOST is set (for more details refer to the original hyperscan documentation)
            }

            // Save the database to the file system for later use
            try(OutputStream out = new FileOutputStream("db")) {
                db.save(out);
            }

            // Later, load the database back in. This is useful for large databases that take a long time to compile.
            // You can compile them offline, save them to a file, and then quickly load them in at runtime.
            // The load has to happen on the same type of platform as the save.
            try (InputStream in = new FileInputStream("db");
                 Database loadedDb = Database.load(in)) {
                // Use the loadedDb as before.
            }
        }
        catch (CompileErrorException ce) {
            //gets thrown during  compile in case something with the expression is wrong
            //you can retrieve the expression causing the exception like this:
            Expression failedExpression = ce.getFailedExpression();
        }
        catch(IOException ie) {
          //IO during serializing / deserializing failed
        }
    }

    @TestWithDatabaseRoundtrip
    void chineseUTF8(SerializeDatabase serialize) {
        Expression expr = new Expression("测试", EnumSet.of(ExpressionFlag.UTF8));
        try {
            Database db = roundTrip(Database.compile(expr), serialize);
            Scanner scanner = new Scanner();
            scanner.allocScratch(db);
            List<Match> matches = scanner.scan(db, "这是一个测试");

            assertEquals(1, matches.size());
        }
        catch(Exception e) {
            fail(e);
        }
    }

    @TestWithDatabaseRoundtrip
    void utf8MatchedString(SerializeDatabase serialize) {
        Expression expr = new Expression("\\d{5}", EnumSet.of(ExpressionFlag.SOM_LEFTMOST, ExpressionFlag.UTF8));
        try {
            Database db = roundTrip(Database.compile(expr), serialize);
            Scanner scanner = new Scanner();
            scanner.allocScratch(db);
            List<Match> matches = scanner.scan(db, "58744 78524 \uD83D\uDE00The quick brown fox ◌\uD804\uDD00 jumps 06840 over the lazy dog༼؈");
            assertEquals("06840", matches.get(2).getMatchedString());
            assertEquals(44, matches.get(2).getStartPosition());
            assertEquals(48, matches.get(2).getEndPosition());
        }
        catch(Exception e) {
            fail(e);
        }
    }

    @TestWithDatabaseRoundtrip
    void logicalCombination(SerializeDatabase serialize) {
        List<String> expressionStrings = Arrays.asList(
                "abc",
                "def",
                "foobar.*gh",
                "teakettle{4,10}",
                "ijkl[mMn]",
                "(0 & 1 & 2) | (3 & !4)",
                "(0 | 1 & 2) & (!3 | 4)",
                "((0 | 1) & 2) & (3 | 4)");
        List<EnumSet<ExpressionFlag>> flags = Arrays.asList(
                EnumSet.of(ExpressionFlag.QUIET),
                EnumSet.of(ExpressionFlag.QUIET),
                EnumSet.of(ExpressionFlag.QUIET),
                EnumSet.of(ExpressionFlag.NO_FLAG),
                EnumSet.of(ExpressionFlag.QUIET),
                EnumSet.of(ExpressionFlag.COMBINATION),
                EnumSet.of(ExpressionFlag.COMBINATION),
                EnumSet.of(ExpressionFlag.COMBINATION)
        );
        List<Expression> expressions = buildExpressions(expressionStrings, flags);

        try {
            Database db = roundTrip(Database.compile(expressions), serialize);
            Scanner scanner = new Scanner();
            scanner.allocScratch(db);
            List<Match> matches = scanner.scan(db, "abbdefxxfoobarrrghabcxdefxteakettleeeeexxxxijklmxxdef");
                                                        //01234567890123456789012345678901234567890123456789012
            assertEquals(17, matches.size());
            assertMatch(17, expressionStrings.get(6), matches.get(0));
            assertMatch(20, expressionStrings.get(5), matches.get(1));
            assertMatch(20, expressionStrings.get(6), matches.get(2));
            assertMatch(24, expressionStrings.get(5), matches.get(3));
            assertMatch(24, expressionStrings.get(6), matches.get(4));
            assertMatch(37, expressionStrings.get(3), matches.get(5));
            assertMatch(37, expressionStrings.get(5), matches.get(6));
            assertMatch(37, expressionStrings.get(7), matches.get(7));
            assertMatch(38, expressionStrings.get(3), matches.get(8));
            assertMatch(38, expressionStrings.get(5), matches.get(9));
            assertMatch(38, expressionStrings.get(7), matches.get(10));
            assertMatch(47, expressionStrings.get(5), matches.get(11));
            assertMatch(47, expressionStrings.get(6), matches.get(12));
            assertMatch(47, expressionStrings.get(7), matches.get(13));
            assertMatch(52, expressionStrings.get(5), matches.get(14));
            assertMatch(52, expressionStrings.get(6), matches.get(15));
            assertMatch(52, expressionStrings.get(7), matches.get(16));
        }
        catch(Exception e) {
            fail(e);
        }
    }

    private void assertMatch(int expectedEndPosition, String expectedExpression, Match actualMatch) {
        assertEquals(expectedEndPosition, actualMatch.getEndPosition());
        assertEquals(expectedExpression, actualMatch.getMatchedExpression().getExpression());
    }


    @Retention(RetentionPolicy.RUNTIME)
    @ParameterizedTest
    @EnumSource(SerializeDatabase.class)
    @interface TestWithDatabaseRoundtrip {}

    enum SerializeDatabase {
        DONT_SERIALIZE, SERIALIZE
    }

    private static Database roundTrip(Database db, SerializeDatabase serialize) throws IOException {
        if (serialize == SerializeDatabase.DONT_SERIALIZE)
        {
            return db;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        db.save(baos);
        db.close();

        Database deserialized = Database.load(new ByteArrayInputStream(baos.toByteArray()));

        assertEquals(db, deserialized, "Deserialized database must be equal");

        return deserialized;
    }

    private List<Expression> buildExpressions(List<String> expressionStrings, List<EnumSet<ExpressionFlag>> flags){
        List<Expression> expressions = new ArrayList<>();
        for (int i = 0; i < expressionStrings.size(); i++) {
            expressions.add(new Expression(expressionStrings.get(i), flags.get(i)));
        }
        return expressions;
    }
}
