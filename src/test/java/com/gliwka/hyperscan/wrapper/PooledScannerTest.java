package com.gliwka.hyperscan.wrapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the PooledScanner class.
 */
class PooledScannerTest {

    private Database db1;
    private Database db2;
    private PooledScanner pooledScanner;

    @BeforeEach
    void setUp() throws HyperscanException, CompileErrorException {
        // Basic database for testing
        Expression expr1 = new Expression("test", EnumSet.of(ExpressionFlag.CASELESS, ExpressionFlag.SOM_LEFTMOST));
        db1 = Database.compile(expr1);

        // A second database for multi-db tests
        Expression expr2 = new Expression("example", EnumSet.of(ExpressionFlag.SOM_LEFTMOST));
        db2 = Database.compile(expr2);

        // Initialize PooledScanner before each test, adjust sizes as needed per test
    }

    @AfterEach
    void tearDown() {
        if (pooledScanner != null) {
            pooledScanner.close();
        }
        if (db1 != null) {
            db1.close();
        }
        if (db2 != null) {
            db2.close();
        }
    }

    // --- Test Cases Implementation Follows ---

    @Test
    void testBasicScan() throws Exception {
        pooledScanner = new PooledScanner(1, 1); // Small pool for basic test
        List<Match> matches = pooledScanner.scan(db1, "This is a TEST string.");
        assertEquals(1, matches.size());
        assertEquals("TEST", matches.get(0).getMatchedString());
        assertEquals(10, matches.get(0).getStartPosition());
        assertEquals(13, matches.get(0).getEndPosition());
    }

    @Test
    void testMultiDatabaseScan() throws Exception {
        pooledScanner = new PooledScanner(1, 1);

        // Scan with db1
        List<Match> matches1 = pooledScanner.scan(db1, "Testing with db1 first.");
        assertEquals(1, matches1.size());
        assertEquals("Test", matches1.get(0).getMatchedString()); // Caseless

        // Scan with db2 using the same pooled scanner (should allocate scratch for db2)
        List<Match> matches2 = pooledScanner.scan(db2, "Now an example with db2.");
        assertEquals(1, matches2.size());
        assertEquals("example", matches2.get(0).getMatchedString());

        // Scan with db1 again to ensure it still works
        List<Match> matches3 = pooledScanner.scan(db1, "Another TEST with db1.");
        assertEquals(1, matches3.size());
        assertEquals("TEST", matches3.get(0).getMatchedString());
    }

    @Test
    @Timeout(10) // Timeout to prevent deadlock if pool logic fails
    void testConcurrentScan() throws Exception {
        int numThreads = 10;
        int scansPerThread = 50;
        int initialPoolSize = 4; // Smaller than numThreads to force creation/reuse
        int maxPoolSize = 8;     // Smaller than numThreads to force blocking/waiting

        pooledScanner = new PooledScanner(initialPoolSize, maxPoolSize);
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Callable<List<Match>>> tasks = new ArrayList<>();

        CountDownLatch latch = new CountDownLatch(numThreads);

        for (int i = 0; i < numThreads; i++) {
            final int threadIndex = i;
            tasks.add(() -> {
                latch.countDown();
                latch.await(); // Ensure all threads start roughly together
                List<Match> threadMatches = new ArrayList<>();
                String text1 = "Thread " + threadIndex + " is testing the system.";
                String text2 = "Thread " + threadIndex + " provides an example.";
                for (int j = 0; j < scansPerThread; j++) {
                    // Alternate between databases
                    if (j % 2 == 0) {
                        threadMatches.addAll(pooledScanner.scan(db1, text1));
                    } else {
                        threadMatches.addAll(pooledScanner.scan(db2, text2));
                    }
                }
                return threadMatches;
            });
        }

        List<Future<List<Match>>> futures = executor.invokeAll(tasks);
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Executor did not terminate in time");

        long totalMatches = 0;
        for (Future<List<Match>> future : futures) {
            List<Match> result = future.get(); // Get result, propagates exceptions
            assertNotNull(result);
            // Each thread should find one match per scan
            assertEquals(scansPerThread, result.size(), "Incorrect number of matches for a thread");
            totalMatches += result.size();

            // Basic check on one match per thread result list for sanity
            if (!result.isEmpty()) {
                Match firstMatch = result.get(0);
                assertTrue(firstMatch.getMatchedString().equalsIgnoreCase("test") ||
                           firstMatch.getMatchedString().equals("example"));
            }
        }

        assertEquals((long)numThreads * scansPerThread, totalMatches, "Incorrect total number of matches");
    }

    @Test
    void testInvalidConstructorArgs() {
        assertThrows(IllegalArgumentException.class, () -> new PooledScanner(0, 5));
        assertThrows(IllegalArgumentException.class, () -> new PooledScanner(5, 0));
        assertThrows(IllegalArgumentException.class, () -> new PooledScanner(-1, 5));
        assertThrows(IllegalArgumentException.class, () -> new PooledScanner(5, -1));
        assertThrows(IllegalArgumentException.class, () -> new PooledScanner(6, 5));
        assertThrows(IllegalArgumentException.class, () -> new PooledScanner(5, 257)); // Exceeds hard limit
        assertThrows(IllegalArgumentException.class, () -> new PooledScanner(257, 257));
    }

    @Test
    void testScanAfterClose() throws Exception {
        pooledScanner = new PooledScanner(1, 1);
        pooledScanner.close();

        assertThrows(IllegalStateException.class, () -> {
            pooledScanner.scan(db1, "Scanning after close should fail");
        });
    }

    // Note: Directly testing pool size limits and blocking behaviour is difficult without
    // exposing internal state or potentially flaky timing-based tests.
    // The concurrent test with maxPoolSize < numThreads provides some confidence.
    // Testing that close() *actually* closes the underlying Scanners is also hard
    // without modifying Scanner or using reflection/mocking.

}
