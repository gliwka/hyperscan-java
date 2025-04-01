package com.gliwka.hyperscan.wrapper;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ScannerStaticCallback, focusing on basic functionality and concurrent usage
 * with separate instances per thread.
 */
public class ScannerStaticCallbackTest {

    private static Database database;
    private static final String TEST_TEXT = "This string contains hyperscan and 123 numbers.";

    @BeforeAll
    static void setupClass() throws CompileErrorException {
        // Compile a shared database for all tests
        List<Expression> expressions = new ArrayList<>();
        expressions.add(new Expression("hyperscan", EnumSet.of(ExpressionFlag.CASELESS, ExpressionFlag.SOM_LEFTMOST)));
        expressions.add(new Expression("\\d{3}", ExpressionFlag.SOM_LEFTMOST)); // Regex for digits
        database = Database.compile(expressions);
        System.out.println("Test Database compiled.");
    }

    @AfterAll
    static void teardownClass() {
        if (database != null) {
            // Database should be managed by GC/finalizers in this library's typical use,
            // but explicit close would be here if needed.
            System.out.println("Test Database teardown.");
        }
    }

    @Test
    @Timeout(5) // Add a timeout to prevent hangs
    void testSingleThreadedScan() {
        System.out.println("Starting testSingleThreadedScan...");
        try (ScannerStaticCallback scanner = new ScannerStaticCallback()) {
            System.out.println("Allocating scratch for single-threaded test...");
            scanner.allocScratch(database);
            System.out.println("Scratch allocated. Performing scan...");
            List<Match> matches = scanner.scan(database, TEST_TEXT);
            System.out.println("Scan complete. Matches found: " + (matches != null ? matches.size() : "null"));

            assertNotNull(matches, "Matches list should not be null");
            assertEquals(2, matches.size(), "Expected 2 matches ('hyperscan', '123')");

            boolean foundHyperscan = matches.stream()
                    .anyMatch(m -> m.getMatchedString().equalsIgnoreCase("hyperscan"));
            boolean foundNumbers = matches.stream()
                    .anyMatch(m -> m.getMatchedString().equals("123"));

            assertTrue(foundHyperscan, "Did not find 'hyperscan' match");
            assertTrue(foundNumbers, "Did not find '123' match");

            System.out.println("Assertions passed for single-threaded test.");

        } catch (HyperscanException e) {
            fail("HyperscanException occurred during single-threaded test: " + e.getMessage(), e);
        } catch (Exception e) {
            fail("Unexpected exception during single-threaded test: " + e.getMessage(), e);
        } finally {
             System.out.println("Finished testSingleThreadedScan.");
        }
         // Scanner is auto-closed by try-with-resources
    }

    @Test
    @Timeout(15) // Longer timeout for concurrent test
    void testConcurrentScansSeparateInstances() throws InterruptedException {
        System.out.println("Starting testConcurrentScansSeparateInstances...");
        final int numThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1); // To synchronize start
        CountDownLatch endLatch = new CountDownLatch(numThreads); // To wait for completion
        AtomicBoolean scratchInUseErrorOccurred = new AtomicBoolean(false);
        AtomicReference<Throwable> exceptionRef = new AtomicReference<>(null); // Capture first error

        ScannerStaticCallback scanner1 = null;
        ScannerStaticCallback scanner2 = null;

        try {
            // Create and prepare scanners *before* submitting tasks
            System.out.println("Creating and allocating scratch for scanner 1...");
            scanner1 = new ScannerStaticCallback();
            scanner1.allocScratch(database);
            System.out.println("Scanner 1 ready.");

            System.out.println("Creating and allocating scratch for scanner 2...");
            scanner2 = new ScannerStaticCallback();
            scanner2.allocScratch(database);
             System.out.println("Scanner 2 ready.");

            // Assign final references for lambda use
            final ScannerStaticCallback finalScanner1 = scanner1;
            final ScannerStaticCallback finalScanner2 = scanner2;

            // Thread 1 Task
            executor.submit(() -> {
                try {
                    System.out.println("Thread 1 waiting for start signal...");
                    startLatch.await();
                    System.out.println("Thread 1 starting scan with scanner: " + finalScanner1);
                    List<Match> matches = finalScanner1.scan(database, TEST_TEXT + " thread1");
                    System.out.println("Thread 1 finished scan. Matches: " + matches.size());
                    assertNotNull(matches); // Basic check
                } catch (HyperscanException e) {
                    System.err.println("Thread 1 caught HyperscanException: " + e.getMessage());
                    if (e.getMessage() != null && e.getMessage().contains("already in use")) {
                        scratchInUseErrorOccurred.set(true);
                    }
                    exceptionRef.compareAndSet(null, e); // Store first exception
                } catch (Throwable t) {
                    System.err.println("Thread 1 caught Throwable: " + t);
                    exceptionRef.compareAndSet(null, t);
                } finally {
                    endLatch.countDown();
                    System.out.println("Thread 1 finished.");
                }
            });

            // Thread 2 Task
            executor.submit(() -> {
                try {
                     System.out.println("Thread 2 waiting for start signal...");
                    startLatch.await();
                    System.out.println("Thread 2 starting scan with scanner: " + finalScanner2);
                    List<Match> matches = finalScanner2.scan(database, TEST_TEXT + " thread2");
                     System.out.println("Thread 2 finished scan. Matches: " + matches.size());
                    assertNotNull(matches); // Basic check
                } catch (HyperscanException e) {
                    System.err.println("Thread 2 caught HyperscanException: " + e.getMessage());
                    if (e.getMessage() != null && e.getMessage().contains("already in use")) {
                        scratchInUseErrorOccurred.set(true);
                    }
                    exceptionRef.compareAndSet(null, e);
                } catch (Throwable t) {
                     System.err.println("Thread 2 caught Throwable: " + t);
                    exceptionRef.compareAndSet(null, t);
                } finally {
                    endLatch.countDown();
                     System.out.println("Thread 2 finished.");
                }
            });

            // Start both threads concurrently
            System.out.println("Signalling threads to start scan...");
            startLatch.countDown();

            // Wait for both threads to finish
            System.out.println("Waiting for threads to complete...");
            boolean finished = endLatch.await(10, TimeUnit.SECONDS);
             System.out.println("Threads completion status: " + (finished ? "Finished" : "Timed Out"));
            assertTrue(finished, "Threads did not finish within the timeout period.");
            executor.shutdown();

            // Check if any exception was captured
            Throwable thrown = exceptionRef.get();
            if (thrown != null) {
                 fail("An exception occurred during the concurrent test: " + thrown.getMessage(), thrown);
            }

            // Specifically fail if the "scratch already in use" error was detected by any thread
            assertFalse(scratchInUseErrorOccurred.get(),
                    "HyperscanException 'scratch region was already in use' occurred during concurrent scan, even with separate Scanner instances.");

             System.out.println("Concurrent test passed - no exceptions or scratch conflicts detected.");

        } catch (HyperscanException e) {
            // Exception during setup (allocScratch)
            fail("Failed to allocate scratch for scanners before test execution: " + e.getMessage(), e);
        } finally {
            // Ensure scanners are closed even if setup fails partially or test throws
             System.out.println("Closing resources for concurrent test...");
            if (scanner1 != null) {
                try { scanner1.close(); } catch (Exception e) { System.err.println("Error closing scanner1: " + e.getMessage());}
            }
            if (scanner2 != null) {
                 try { scanner2.close(); } catch (Exception e) { System.err.println("Error closing scanner2: " + e.getMessage());}
            }
            // Ensure executor is shut down
            if (!executor.isShutdown()) {
                executor.shutdownNow();
                 System.out.println("Executor shutdown forced.");
            }
             System.out.println("Finished testConcurrentScansSeparateInstances.");
        }
    }
}
