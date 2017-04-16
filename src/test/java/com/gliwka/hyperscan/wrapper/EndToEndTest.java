package com.gliwka.hyperscan.wrapper;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


class EndToEndTest {
    @Test
    void simpleSingleExpression() {
        EnumSet<ExpressionFlag> flags = EnumSet.of(ExpressionFlag.CASELESS, ExpressionFlag.SOM_LEFTMOST);
        Expression expression = new Expression("Te?st", flags);
        Expression.ValidationResult result = expression.validate();
        assertEquals(result.getIsValid(), true);
        assertEquals(result.getErrorMessage(), "");
        try {
            Database db = Database.Compile(expression);
            assertTrue(db.getSize() > 0);
            Scanner scanner = new Scanner();
            List<Match> matches = scanner.Scan(db, "Dies ist ein Test tst.");
            assertEquals(matches.size(), 2);
            assertEquals(matches.get(0).getEndPosition(), 17);
            assertEquals(matches.get(0).getStartPosition(), 13);
            assertEquals(matches.get(0).getMatchedString(), "Test");
            assertEquals(matches.get(0).getMatchedExpression(), expression);
            assertTrue(scanner.getSize() > 0);
        }
        catch (Throwable e) {
            fail(e.getMessage());
        }

        Expression invalidExpression = new Expression("test\\1", EnumSet.noneOf(ExpressionFlag.class));
        Expression.ValidationResult invalidResult = invalidExpression.validate();
        assertFalse(invalidResult.getIsValid());
        assertTrue(invalidResult.getErrorMessage().length() > 0);

        try {
            Database.Compile(invalidExpression);
            fail("No exception was thrown");
        } catch (Throwable e) {
            //expected
        }
    }

    @Test
    void simpleMultiExpression() {
        LinkedList<Expression> expressions = new LinkedList<Expression>();

        EnumSet<ExpressionFlag> flags = EnumSet.of(ExpressionFlag.CASELESS, ExpressionFlag.SOM_LEFTMOST);

        expressions.add(new Expression("Te?st", flags));
        expressions.add(new Expression("ist", flags));

        try {
            Database db = Database.Compile(expressions);
            assertTrue(db.getSize() > 0);
            Scanner scanner = new Scanner();
            List<Match> matches = scanner.Scan(db, "Dies ist ein Test tst.");
            assertEquals(matches.size(), 3);
            assertEquals(matches.get(0).getEndPosition(), 8);
            assertEquals(matches.get(0).getStartPosition(), 5);
            assertEquals(matches.get(0).getMatchedString(), "ist");
            assertEquals(matches.get(0).getMatchedExpression(), expressions.get(1));
            assertTrue(scanner.getSize() > 0);
        }
        catch (Throwable e) {
            fail(e.getMessage());
        }

        expressions.add(new Expression("invalid\\1", flags));
        try {
            Database db = Database.Compile(expressions);
            fail("Should never reach here");
        }
        catch(Throwable e) {
            //expected
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
        try {
            Database db = Database.Compile(expressions);

            //initialize scanner
            Scanner scanner = new Scanner();

            //provide the database and the input string
            //returns a list with matches
            //synchronized method, only one execution at a time (use more scanner instances for multithreading)
            List<Match> matches = scanner.Scan(db, "12345 test string");

            //matches always contain the expression causing the match and the end position of the match
            //the start position and the matches string it self is only part of a matach if the
            //SOM_LEFTMOST is set (for more details refer to the original hyperscan documentation)
        } catch (CompileErrorException ce) {
            //gets thrown during  compile in case something with the expression is wrong
            //you can retrieve the expression causing the exception like this:
            Expression failedExpression = ce.getFailedExpression();
        } catch (Throwable e) {
            //edge cases like OOM, illegal platform etc.
        }
    }


    @Test void chineseUTF8() {
        Expression expr = new Expression("测试", EnumSet.of(ExpressionFlag.UTF8));
        try {
            Database db = Database.Compile(expr);
            Scanner scanner = new Scanner();
            List<Match> matches = scanner.Scan(db, "这是一个测试");

            assertEquals(matches.size(), 1);
        }
        catch(Throwable e) {
            fail(e);
        }
    }
}
