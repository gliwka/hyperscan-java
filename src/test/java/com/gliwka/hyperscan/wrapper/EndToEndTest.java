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
            Database db = Database.compile(expression);
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

    @Test
    void simpleMultiExpression() {
        LinkedList<Expression> expressions = new LinkedList<Expression>();

        EnumSet<ExpressionFlag> flags = EnumSet.of(ExpressionFlag.CASELESS, ExpressionFlag.SOM_LEFTMOST);

        expressions.add(new Expression("Te?st", flags));
        expressions.add(new Expression("ist", flags));

        try {
            Database db = Database.compile(expressions);
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

    @Test
    void infiniteRegex() {
        try {
            Database db = Database.compile(new Expression("a|", EnumSet.of(ExpressionFlag.ALLOWEMPTY)));
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
        try {
            Database db = Database.compile(expressions);

            //initialize scanner
            Scanner scanner = new Scanner();

            //provide the database and the input string
            //returns a list with matches
            //synchronized method, only one execution at a time (use more scanner instances for multithreading)
            List<Match> matches = scanner.scan(db, "12345 test string");

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
            Database db = Database.compile(expr);
            Scanner scanner = new Scanner();
            scanner.allocScratch(db);
            List<Match> matches = scanner.scan(db, "这是一个测试");

            assertEquals(matches.size(), 1);
        }
        catch(Throwable e) {
            fail(e);
        }
    }

    @Test void utf8MatchedString() {
        Expression expr = new Expression("\\d{5}", EnumSet.of(ExpressionFlag.SOM_LEFTMOST, ExpressionFlag.UTF8));
        try {
            Database db = Database.compile(expr);
            Scanner scanner = new Scanner();
            scanner.allocScratch(db);
            List<Match> matches = scanner.scan(db, " Menu About Us Strategy Professionals Investments Contact Contact Home / Contact 1 2 Map DataMap data ©2017 GoogleMap DataMap data ©2017 GoogleMap data ©2017 GoogleTerms of UseReport a map errorMapCustom Map RFE Investment Partners 36 Grove St New Canaan, CT, 06840 (203) 966-2800 For general inquiries: info@rfeip.com For intermediaries: deals@rfeip.com For executives: executives@rfeip.com For investors: investors@rfeip.com Copyright 2016 RFE Investment Partners Premium Barbershop is the prime spot for your hair grooming needs in find us at 75250 FAIRWAY Drive Indian Wells, CA 92210 open 24/7 New York City. Our approach is simple and efficient. We are here to provide the best hair cut, shave, or any other grooming service you may desire! Menu HOME ABOUT US SERVICES GALLERY BLOG SOCIAL CONTACT US HOME ABOUT US SERVICES GALLERY BLOG SOCIAL CONTACT US We are open 7 days (855) 692 2887 latest news Enjoy a limited time $5 discount by printing the voucher below Premium Barbershop is growing. Read More We Open A New Location on 622 3rd avenue (Lobby) (bet. 40st and 41st) Read More What we offer Manhattan barber shop Best New York barbershop Premium Barbershop always offers professional quality for all of our customers and we are ready to deal with your highest expectations. Are you looking for quality? You found it! Our services are dedicated for your personal success. Here at Premium Barbershop we have award winning staff that have demonstrated talent of master barbers at several notable styling competitions. Let our barber to be your personal stylist and you will never be disappointed. In addition we place your personal style vision above all the other things. Our master barbers always ready to give you professional advices but will also ask you about all the cut to achieve a most desirable result for you. Most of our visitors are our regular clients now. They include celebrities, business executives and many other people who want to look good and make a proper impression. Our professional service and our care about their notion makes them to leave with a smile on their faces and totally satisfied. Many of our clients claims it was a best New York barbershop, they visit. Most accessible Manhattan barber shop Our modernly equipped Barbershop is located in one step away from the business center of Manhattan – on 299 East 52nd street, between 1st and 2nd Ave. We are open 7 days a week from early morning until evening, making it possible to get a haircut during the hours most convenient for you. We won`t waste even one moment of your time. We do our work, you enjoy your time and your style. While we take care of providing you with the best style you can; watch hot political and economic news of the world on large flat screen TVs, or for sports fans we show the latest UFC and Mixed Marshall Arts Championship programs. Here at Premium Barbershop we respect your time and try our best for our services to be most accessible, most enjoyable and convenient Manhattan barber shop ever. Working Hours Mon-Fri: 8:30 AM – 7:30 PM Saturday: 9:00 AM – 6:00 PM Sunday: 10:00 AM – 5:00 PM Save $5 OFF Services Haircut services Shampoo + Cut $24.95 Long Layered Cut $24.95 Regular Haircut $21.95 Fade + Hot Towel $21.95 Children’s Haircut $18.95 Crew Cut + Shape-Up $21.95 Senior Citizen Cut $17.95 Crew Cut + Hot Towel $17.95 RAZOR SERVICES Shave $24.95 Beard Trim $9.95 Beard Trim with Razor $12.95 Clean-Up $9.95 Goatee Beard $5.95 OUR LOCATIONS 299 East 52nd street (bet. 1st and 2nd Ave) New York, NY 10022 (212) 935 - 3066 (Read More) 134 1/2 East 62nd Street (bet. Lexington & 3rd Ave) New York, NY 10021 (212) 308 - 6660 (Read More) 622 3rd avenue (Lobby) (bet. 40st and 41st) New York, NY 10017 (646) 649 - 2235 (Read More) Home About us Blog Contact us Gallery Services Social @2017 Premium Barber Shop. All Rights Reserved. ");
            assertEquals(matches.get(0).getMatchedString(), "06840");
        }
        catch(Throwable e) {
            fail(e);
        }
    }
}
