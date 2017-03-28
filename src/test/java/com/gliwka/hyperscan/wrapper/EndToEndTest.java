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
}