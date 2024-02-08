package com.gliwka.hyperscan.wrapper;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DatabaseTest {

    @Test
    void serializationTest() throws Exception {
        LinkedList<Expression> expressions = new LinkedList<>();
        expressions.add(new Expression("[0-9]{5}", EnumSet.of(ExpressionFlag.SOM_LEFTMOST)));
        expressions.add(new Expression("Test", EnumSet.of(ExpressionFlag.CASELESS)));
        try (
                Database originalDb = Database.compile(expressions);
                Scanner originalScanner = new Scanner();
                Database deserializedDb = Database.deserialize(originalDb.serialize());
                Scanner deserializedScanner = new Scanner();
        ) {
            originalScanner.allocScratch(originalDb);
            List<Match> matches = originalScanner.scan(originalDb, "Test 12345");
            assertEquals(2, matches.size());

            deserializedScanner.allocScratch(deserializedDb);
            List<Match> matchesFromSerialized = deserializedScanner.scan(deserializedDb, "Test 12345");
            assertEquals(2, matchesFromSerialized.size());
        }
    }
}
