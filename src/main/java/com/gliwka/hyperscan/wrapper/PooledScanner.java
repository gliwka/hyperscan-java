package com.gliwka.hyperscan.wrapper;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A thread-safe scanner that manages a pool of underlying Hyperscan scanners.
 * It handles the allocation of scratch space automatically for each scanner/database combination.
 *
 * Use this class when you need to perform scans from multiple threads concurrently and are fine with
 * sacrificing some performance for the convenience of not having to manage scanners and scratch space per thread manually.
 */
public class PooledScanner implements Closeable {

    private static final int MAX_SCANNERS = 256; // Maximum allowed by underlying Scanner implementation

    private final BlockingQueue<Scanner> pool;
    private final AtomicInteger currentPoolSize = new AtomicInteger(0); // Tracks scanners created (in pool or in use)
    private final int maxSize;
    private volatile boolean closed = false;


    /**
     * Creates a pooled scanner with a specified initial and maximum size.
     *
     * @param initialSize The number of Scanner instances to create and pool initially.
     * @param maxSize The maximum number of Scanner instances allowed in the pool (cannot exceed 256).
     * @throws IllegalArgumentException if maxSize is greater than 256 or initialSize is greater than maxSize.
     */
    public PooledScanner(int initialSize, int maxSize) {
        if (maxSize > MAX_SCANNERS) {
            throw new IllegalArgumentException("Maximum pool size cannot exceed " + MAX_SCANNERS);
        }
        if (initialSize > maxSize) {
            throw new IllegalArgumentException("Initial size cannot be greater than maximum size");
        }
        // Also check if initialSize is non-positive
        if (initialSize <= 0 || maxSize <= 0) {
            throw new IllegalArgumentException("Initial and maximum sizes must be positive");
        }

        this.maxSize = maxSize;
        this.pool = new ArrayBlockingQueue<>(maxSize);

        // Pre-populate the pool
        for (int i = 0; i < initialSize; i++) {
            try {
                Scanner scanner = new Scanner(); // This might throw if > 256 total scanners exist JVM-wide
                pool.put(scanner); // Should not block as queue capacity >= initialSize
                currentPoolSize.incrementAndGet();
            } catch (Exception e) {
                // If creating a scanner fails (e.g., overall limit reached), stop pre-populating
                // and clean up any scanners already created for this pool instance.
                close();
                throw new RuntimeException("Failed to create initial scanner instance", e);
            }
        }
    }

    /**
     * Creates a pooled scanner with a default initial size equal to the number of available processors
     * and a maximum size of 256.
     */
    public PooledScanner() {
        this(Runtime.getRuntime().availableProcessors(), MAX_SCANNERS);
    }

    /**
     * Acquires a Scanner instance from the pool, creating a new one if necessary and allowed.
     * Blocks if the pool is empty and the maximum size has been reached.
     *
     * @return A Scanner instance.
     * @throws InterruptedException if interrupted while waiting for a scanner.
     * @throws IllegalStateException if the pool is closed.
     */
    private Scanner acquireScanner() throws InterruptedException {
         if (closed) {
            throw new IllegalStateException("PooledScanner is closed.");
        }

        Scanner scanner = pool.poll(); // Try non-blocking first

        if (scanner == null) {
            // Try to create a new one if max size not reached
            if (currentPoolSize.get() < maxSize) {
                 // Double check condition inside lock-like mechanism
                 if (currentPoolSize.incrementAndGet() <= maxSize) {
                    try {
                        scanner = new Scanner();
                        // Successfully created a new scanner, return it directly
                        return scanner;
                    } catch (Exception e) {
                        // Failed to create, decrement count and potentially block below
                        currentPoolSize.decrementAndGet();
                        // Log or handle exception? For now, proceed to blocking wait.
                         System.err.println("Failed to dynamically create Scanner instance: " + e.getMessage());
                    }
                 } else {
                     // Lost race, another thread incremented past max size
                     currentPoolSize.decrementAndGet();
                 }
            }
            // If pool was empty and couldn't create a new one, block until one is available
             if (closed) { // Check again after potential blocking wait setup
                throw new IllegalStateException("PooledScanner is closed.");
             }
            scanner = pool.take(); // Blocks until a scanner is available
        }
        return scanner;
    }

    /**
     * Returns a Scanner instance to the pool.
     *
     * @param scanner The Scanner instance to return.
     * @throws IllegalStateException if the pool is closed.
     * @throws InterruptedException if the thread is interrupted while waiting to put the scanner back.
     */
    private void releaseScanner(Scanner scanner) throws InterruptedException {
         if (closed) {
             // If pool is closed, close the scanner directly instead of returning
             try {
                 scanner.close();
             } catch (Exception e) {
                 // Log error, but continue
                 System.err.println("Error closing scanner after pool was closed: " + e.getMessage());
             }
             return;
         }

        try {
            pool.put(scanner); // May throw IllegalStateException if queue is full (shouldn't happen) or InterruptedException
        } catch (IllegalStateException e) {
             // This indicates a logic error - perhaps scanner was not acquired properly?
             System.err.println("Error returning scanner to pool: " + e.getMessage() + ". Closing scanner directly.");
             try {
                 scanner.close();
             } catch (Exception closeEx) {
                  System.err.println("Error closing scanner directly after failing to return to pool: " + closeEx.getMessage());
             }
        }
    }


    /**
     * Scans input data using a scanner from the pool.
     * Automatically handles scratch space allocation for the chosen scanner and database.
     *
     * @param db The compiled database to scan with.
     * @param input The data to scan.
     * @return A list of matches found.
     * @throws InterruptedException if interrupted while waiting for a scanner.
     * @throws HyperscanException if a Hyperscan error occurs during scanning or scratch allocation.
     * @throws IllegalStateException if the pool is closed.
     */
    public List<Match> scan(final Database db, final String input) throws InterruptedException {
        if (closed) {
            throw new IllegalStateException("PooledScanner is closed.");
        }

        Scanner scanner = acquireScanner();
        try {
            // Always call allocScratch. It's a no-op if already allocated for this scanner/db combo.
            scanner.allocScratch(db); // Can throw HyperscanException

            // Perform the scan
            return scanner.scan(db, input); // Can throw HyperscanException

        } finally {
            releaseScanner(scanner);
        }
    }

    /**
     * Closes the scanner pool and releases all underlying resources.
     * Waits briefly for scanners currently in use to be returned. Any scanners not returned
     * within a short timeout will be abandoned (but their resources might be cleaned up eventually
     * by the Scanner's own finalizer/deallocator, if implemented robustly).
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        // Drain the pool and close scanners
        List<Scanner> remainingScanners = new ArrayList<>();
        pool.drainTo(remainingScanners);

        int closedCount = 0;
        for (Scanner scanner : remainingScanners) {
            try {
                scanner.close();
                closedCount++;
            } catch (Exception e) {
                // Log error and continue closing others
                System.err.println("Error closing a pooled scanner: " + e.getMessage());
            }
        }
         System.out.println("Closed " + closedCount + " idle scanners from the pool.");

        currentPoolSize.set(0); // Reset counter

         // Note: Scanners currently in use by threads will be closed when releaseScanner is called on them.
    }
}
