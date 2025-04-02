package com.gliwka.hyperscan.wrapper;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class ScannerTest {

    @Test
    void oneScannerWithMultipleDatabases() throws Exception {
        try (
                Database dbA = Database.compile(new Expression("a"));
                Database dbB = Database.compile(new Expression("b"));
                Scanner scanner = new Scanner()
        ) {
            scanner.allocScratch(dbA);
            List<Match> matchesA = scanner.scan(dbA, "a");
            assertFalse(matchesA.isEmpty());

            scanner.allocScratch(dbB);
            List<Match>  matchesB = scanner.scan(dbB, "b");
            assertFalse(matchesB.isEmpty());
        }
    }
}
