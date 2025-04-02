package com.gliwka.hyperscan.wrapper;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class ExpressionTest {

    @Test
    void validExpressionShouldValidate() {
        EnumSet<ExpressionFlag> flags = EnumSet.of(ExpressionFlag.CASELESS, ExpressionFlag.SOM_LEFTMOST);
        Expression expression = new Expression("Te?st", flags);
        Expression.ValidationResult result = expression.validate();
        assertTrue(result.isValid(), "Valid expression should return isValid() = true");
        assertNull(result.getErrorMessage(), "Valid expression should have null error message");
    }

    @Test
    void invalidExpressionShouldNotValidate() {
        Expression invalidExpression = new Expression("test\\1", EnumSet.noneOf(ExpressionFlag.class));
        Expression.ValidationResult invalidResult = invalidExpression.validate();
        assertFalse(invalidResult.isValid(), "Invalid expression should return isValid() = false");
        assertNotNull(invalidResult.getErrorMessage(), "Invalid expression should have a non-null error message");
        assertTrue(invalidResult.getErrorMessage().length() > 0, "Invalid expression error message should not be empty");
    }

    @Test
    void constructorWithNullExpressionStringShouldThrow() {
        assertThrows(NullPointerException.class, () -> new Expression(null),
                "Constructor should throw NullPointerException for null expression string");
    }

     @Test
    void constructorWithNullFlagsShouldThrow() {
         assertThrows(NullPointerException.class, () -> new Expression("test", (EnumSet<ExpressionFlag>) null),
                 "Constructor should throw NullPointerException for null flags set");
    }

     @Test
    void constructorWithIdAndNullFlagsShouldThrow() {
         assertThrows(NullPointerException.class, () -> new Expression("test", (EnumSet<ExpressionFlag>) null, 1),
                 "Constructor should throw NullPointerException for null flags set (with ID)");
    }

    @Test
    void constructorWithValidIdShouldSucceed() {
        assertDoesNotThrow(() -> new Expression("test", EnumSet.noneOf(ExpressionFlag.class), 0),
                "Constructor should accept ID 0");
        assertDoesNotThrow(() -> new Expression("test", EnumSet.noneOf(ExpressionFlag.class), 123),
                "Constructor should accept positive ID");
        assertDoesNotThrow(() -> new Expression("test", EnumSet.noneOf(ExpressionFlag.class), null),
                "Constructor should accept null ID");
    }

    @Test
    void constructorWithNegativeIdShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> new Expression("test", EnumSet.noneOf(ExpressionFlag.class), -1),
                "Constructor should throw IllegalArgumentException for negative ID");
    }

     @Test
    void constructorWithSingleFlagShouldSucceed() {
        Expression exp = new Expression("test", ExpressionFlag.CASELESS, 1);
        assertEquals("test", exp.getExpression());
        assertEquals(EnumSet.of(ExpressionFlag.CASELESS), exp.getFlags());
        assertEquals(Integer.valueOf(1), exp.getId());
    }

    @Test
    void equalsAndHashCodeShouldWork() {
        EnumSet<ExpressionFlag> flags1 = EnumSet.of(ExpressionFlag.CASELESS, ExpressionFlag.DOTALL);
        EnumSet<ExpressionFlag> flags2 = EnumSet.of(ExpressionFlag.DOTALL, ExpressionFlag.CASELESS); // Same flags, different order
        EnumSet<ExpressionFlag> flags3 = EnumSet.of(ExpressionFlag.MULTILINE);

        Expression expr1 = new Expression("pattern1", flags1, 1);
        Expression expr1Same = new Expression("pattern1", flags2, 1); // Same content as expr1
        Expression expr2 = new Expression("pattern2", flags1, 1); // Different pattern
        Expression expr3 = new Expression("pattern1", flags3, 1); // Different flags
        Expression expr4 = new Expression("pattern1", flags1, 2); // Different ID
        Expression expr5 = new Expression("pattern1", flags1, null); // Null ID
        Expression expr5Same = new Expression("pattern1", flags2, null); // Null ID, same content
        Expression expr6 = new Expression("pattern1", flags1); // Null ID, different constructor

        // Reflexive
        assertEquals(expr1, expr1);

        // Symmetric & Equal objects
        assertEquals(expr1, expr1Same);
        assertEquals(expr1Same, expr1);
        assertEquals(expr1.hashCode(), expr1Same.hashCode());

        assertEquals(expr5, expr5Same);
        assertEquals(expr5Same, expr5);
        assertEquals(expr5.hashCode(), expr5Same.hashCode());

        assertEquals(expr5, expr6); // Check ID null comparison
        assertEquals(expr6, expr5);
        assertEquals(expr5.hashCode(), expr6.hashCode());

        // Unequal objects
        assertNotEquals(expr1, expr2); // Different pattern
        assertNotEquals(expr1, expr3); // Different flags
        assertNotEquals(expr1, expr4); // Different ID
        assertNotEquals(expr1, expr5); // Different ID (null vs non-null)
        assertNotEquals(expr1, null);  // Different type (null)
        assertNotEquals(expr1, "pattern1"); // Different type (String)

        // HashCode consistency (not strictly tested, but inequality implies hash diff usually)
        assertNotEquals(expr1.hashCode(), expr2.hashCode());
        assertNotEquals(expr1.hashCode(), expr3.hashCode());
        // Note: Hashcodes *might* collide for expr1/expr4/expr5, but it's unlikely and not required by contract
    }
}
