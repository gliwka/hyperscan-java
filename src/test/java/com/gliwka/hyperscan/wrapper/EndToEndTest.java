package com.gliwka.hyperscan.wrapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.*;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class EndToEndTest {

    @TestWithDatabaseRoundtrip
    void simpleSingleExpression(SerializeDatabase serialize) {
        EnumSet<ExpressionFlag> flags = EnumSet.of(ExpressionFlag.CASELESS, ExpressionFlag.SOM_LEFTMOST);
        Expression expression = new Expression("Te?st", flags);
        Expression.ValidationResult result = expression.validate();
        assertEquals(result.getIsValid(), true);
        assertEquals(result.getErrorMessage(), "");
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
        catch (Throwable e) {
            fail(e.getMessage());
        }

        Expression invalidExpression = new Expression("test\\1", EnumSet.noneOf(ExpressionFlag.class));
        Expression.ValidationResult invalidResult = invalidExpression.validate();
        assertFalse(invalidResult.getIsValid());
        assertTrue(invalidResult.getErrorMessage().length() > 0);

        try {
            Database.compile(invalidExpression);
            fail("No exception was thrown");
        } catch (Throwable e) {
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
            assertEquals(matches.size(), 3);
            assertEquals(matches.get(0).getEndPosition(), 7);
            assertEquals(matches.get(0).getStartPosition(), 5);
            assertEquals(matches.get(0).getMatchedString(), "ist");
            assertEquals(matches.get(0).getMatchedExpression(), expressions.get(1));
            assertTrue(scanner.getSize() > 0);
        }
        catch (Throwable e) {
            e.printStackTrace();
            fail(e.getMessage());
        }

        expressions.add(new Expression("invalid\\1", flags));
        try {
            Database db = Database.compile(expressions);
            fail("Should never reach here");
        }
        catch(Throwable e) {
            //expected
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
        catch(Throwable t) {
            fail(t);
        }
    }

    @Test
    void doesIt() {
        try {
            Database db = Database.compile(new Expression(null));
            fail("Should never reach here");
        }
        catch(NullPointerException n) {
            //expected
        }
        catch(Throwable t) {
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
        catch(Throwable e) {
            //edge cases like OOM, illegal platform etc.
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

            assertEquals(matches.size(), 1);
        }
        catch(Throwable e) {
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
            List<Match> matches = scanner.scan(db, " Menu About Us Strategy Professionals Investments Contact Contact Home / Contact 1 2 Map DataMap data ©2017 GoogleMap DataMap data ©2017 GoogleMap data ©2017 GoogleTerms of UseReport a map errorMapCustom Map RFE Investment Partners 36 Grove St New Canaan, CT, 06840 (203) 966-2800 For general inquiries: info@rfeip.com For intermediaries: deals@rfeip.com For executives: executives@rfeip.com For investors: investors@rfeip.com Copyright 2016 RFE Investment Partners Premium Barbershop is the prime spot for your hair grooming needs in find us at 75250 FAIRWAY Drive Indian Wells, CA 92210 open 24/7 New York City. Our approach is simple and efficient. We are here to provide the best hair cut, shave, or any other grooming service you may desire! Menu HOME ABOUT US SERVICES GALLERY BLOG SOCIAL CONTACT US HOME ABOUT US SERVICES GALLERY BLOG SOCIAL CONTACT US We are open 7 days (855) 692 2887 latest news Enjoy a limited time $5 discount by printing the voucher below Premium Barbershop is growing. Read More We Open A New Location on 622 3rd avenue (Lobby) (bet. 40st and 41st) Read More What we offer Manhattan barber shop Best New York barbershop Premium Barbershop always offers professional quality for all of our customers and we are ready to deal with your highest expectations. Are you looking for quality? You found it! Our services are dedicated for your personal success. Here at Premium Barbershop we have award winning staff that have demonstrated talent of master barbers at several notable styling competitions. Let our barber to be your personal stylist and you will never be disappointed. In addition we place your personal style vision above all the other things. Our master barbers always ready to give you professional advices but will also ask you about all the cut to achieve a most desirable result for you. Most of our visitors are our regular clients now. They include celebrities, business executives and many other people who want to look good and make a proper impression. Our professional service and our care about their notion makes them to leave with a smile on their faces and totally satisfied. Many of our clients claims it was a best New York barbershop, they visit. Most accessible Manhattan barber shop Our modernly equipped Barbershop is located in one step away from the business center of Manhattan – on 299 East 52nd street, between 1st and 2nd Ave. We are open 7 days a week from early morning until evening, making it possible to get a haircut during the hours most convenient for you. We won`t waste even one moment of your time. We do our work, you enjoy your time and your style. While we take care of providing you with the best style you can; watch hot political and economic news of the world on large flat screen TVs, or for sports fans we show the latest UFC and Mixed Marshall Arts Championship programs. Here at Premium Barbershop we respect your time and try our best for our services to be most accessible, most enjoyable and convenient Manhattan barber shop ever. Working Hours Mon-Fri: 8:30 AM – 7:30 PM Saturday: 9:00 AM – 6:00 PM Sunday: 10:00 AM – 5:00 PM Save $5 OFF Services Haircut services Shampoo + Cut $24.95 Long Layered Cut $24.95 Regular Haircut $21.95 Fade + Hot Towel $21.95 Children’s Haircut $18.95 Crew Cut + Shape-Up $21.95 Senior Citizen Cut $17.95 Crew Cut + Hot Towel $17.95 RAZOR SERVICES Shave $24.95 Beard Trim $9.95 Beard Trim with Razor $12.95 Clean-Up $9.95 Goatee Beard $5.95 OUR LOCATIONS 299 East 52nd street (bet. 1st and 2nd Ave) New York, NY 10022 (212) 935 - 3066 (Read More) 134 1/2 East 62nd Street (bet. Lexington & 3rd Ave) New York, NY 10021 (212) 308 - 6660 (Read More) 622 3rd avenue (Lobby) (bet. 40st and 41st) New York, NY 10017 (646) 649 - 2235 (Read More) Home About us Blog Contact us Gallery Services Social @2017 Premium Barber Shop. All Rights Reserved. ");
            assertEquals(matches.get(0).getMatchedString(), "06840");
        }
        catch(Throwable e) {
            fail(e);
        }
    }

    @TestWithDatabaseRoundtrip
    void logicalCombination(SerializeDatabase serialize) {
        List<String> expressionStrings = Arrays.asList(
                "abc", //201 0
                "def", //202 1
                "foobar.*gh", //203 2
                "teakettle{4,10}",  //204 3
                "ijkl[mMn]", //205  4
                "(0 & 1 & 2) | (3 & !4)", //1001  5
                "(0 | 1 & 2) & (!3 | 4)", // 1002 6
                "((0 | 1) & 2) & (3 | 4)");// 1003 7
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
        catch(Throwable e) {
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

    private static Database roundTrip(Database db, SerializeDatabase serialize) throws Throwable {
        if (serialize == SerializeDatabase.DONT_SERIALIZE)
        {
            return db;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        db.save(baos);
        db.close();

        Database deserialized = Database.load(new ByteArrayInputStream(baos.toByteArray()));

        assertEquals(db.getExpressionCount(), deserialized.getExpressionCount(), "Deserialized database must have equal expression count");
        for (int i = 0; i < db.getExpressionCount(); i++) {
            assertEquals(db.getExpression(i).getExpression(), deserialized.getExpression(i).getExpression(), "Expression at index " + i + " must have the same pattern");
            assertEquals(db.getExpression(i).getFlags(), deserialized.getExpression(i).getFlags(), "Expression at index " + i + " must have the same flags");
        }

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
