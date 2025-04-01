package com.gliwka.hyperscan.wrapper.benchmarks;

import com.gliwka.hyperscan.wrapper.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark) // Benchmark-level state (shared database, texts, pool)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS) // Longer warmup
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS) // More measurements
@Fork(value = 1, jvmArgsAppend = "-Xmx2g") // Ensure enough heap
public class ScannerBenchmark {

    // --- Shared Benchmark State ---
    private Database database;
    private String shortTextFewMatches;
    private String longTextManyMatches;
    private PooledScanner pooledScanner; // Pool is shared across threads
    private String currentText;

    // --- Parameters ---
    @Param({"shortFew", "longMany"})
    private String textType;

    @Param({"1", "4", "8"}) // Number of concurrent threads JMH will run
    public int threadCount;

    // --- Thread-Local State for Manual Scanner ---
    @State(Scope.Thread)
    public static class ManualScannerState {
        Scanner manualScanner;

        // Benchmark state is passed implicitly if needed
        @Setup(Level.Trial)
        public void doSetup(ScannerBenchmark benchmarkState) {
            System.out.println("Thread setup: Creating manual Scanner for thread " + Thread.currentThread().getId());
            manualScanner = new Scanner();
            try {
                // Allocate scratch once per thread-local scanner
                manualScanner.allocScratch(benchmarkState.database);
            } catch (HyperscanException e) {
                throw new RuntimeException("Failed to allocate scratch in thread state setup", e);
            }
        }

        @TearDown(Level.Trial)
        public void doTeardown() {
             System.out.println("Thread teardown: Closing manual Scanner for thread " + Thread.currentThread().getId());
            if (manualScanner != null) {
                try {
                    manualScanner.close();
                } catch (Exception e) {
                    System.err.println("Error closing manual scanner in teardown: " + e.getMessage());
                }
            }
        }
    }

    // --- Thread-Local State for Static Callback Scanner ---
    @State(Scope.Thread)
    public static class StaticCallbackScannerState {
        ScannerStaticCallback staticCallbackScanner;

        @Setup(Level.Trial)
        public void doSetup(ScannerBenchmark benchmarkState) {
            System.out.println("Thread setup: Creating ScannerStaticCallback for thread " + Thread.currentThread().getId());
            staticCallbackScanner = new ScannerStaticCallback();
            try {
                // Allocate scratch once per thread-local scanner
                staticCallbackScanner.allocScratch(benchmarkState.database);
            } catch (HyperscanException e) {
                throw new RuntimeException("Failed to allocate scratch in static callback thread state setup", e);
            }
        }

        @TearDown(Level.Trial)
        public void doTeardown() {
            System.out.println("Thread teardown: Closing ScannerStaticCallback for thread " + Thread.currentThread().getId());
            if (staticCallbackScanner != null) {
                try {
                    staticCallbackScanner.close();
                } catch (Exception e) {
                    System.err.println("Error closing static callback scanner in teardown: " + e.getMessage());
                }
            }
        }
    }

    @Setup(Level.Trial)
    public void setupBenchmark() throws CompileErrorException {
        System.out.println("Global setup: Thread count = " + threadCount + ", Text type = " + textType);
        // Expressions to compile
        List<Expression> expressions = new ArrayList<>();
        expressions.add(new Expression("Hyperscan", EnumSet.of(ExpressionFlag.CASELESS, ExpressionFlag.SOM_LEFTMOST)));
        expressions.add(new Expression("([0-9]+)", EnumSet.of(ExpressionFlag.SOM_LEFTMOST)));
        expressions.add(new Expression("benchmark", EnumSet.noneOf(ExpressionFlag.class)));
        expressions.add(new Expression("\bword\b", EnumSet.of(ExpressionFlag.CASELESS)));

        // Compile the database
        database = Database.compile(expressions);

        // Prepare input texts
        shortTextFewMatches = "This is a simple test string for Hyperscan.";
        StringBuilder longBuilder = new StringBuilder(20000);
        for (int i = 0; i < 1000; i++) {
            longBuilder.append("Line ").append(i).append(": Some text with numbers 12345 and the word hyperscan benchmark repeated. ");
        }
        longTextManyMatches = longBuilder.toString();

        // Initialize PooledScanner - size matches thread count
        pooledScanner = new PooledScanner(threadCount, threadCount);

        // Determine which text to use based on param
        currentText = "shortFew".equals(textType) ? shortTextFewMatches : longTextManyMatches;
        System.out.println("Using text length: " + currentText.length());
        System.out.println("Global setup complete.");
    }

    @TearDown(Level.Trial)
    public void teardownBenchmark() {
        System.out.println("Global teardown starting...");
        if (pooledScanner != null) {
            try {
                pooledScanner.close();
            } catch (Exception e) {
                System.err.println("Error closing pooled scanner: " + e.getMessage());
            }
        }
         // Database is closed automatically by its finalizer in this setup,
         // but explicit close is good practice if needed elsewhere.
        // if (database != null) { ... database.close(); ... }
        System.out.println("Global teardown complete.");
    }

    @Benchmark
    @Threads(Threads.MAX) // Use the threadCount parameter
    public void benchmarkPooledScanner(Blackhole bh) {
        try {
            // PooledScanner.scan handles acquire/release internally
            List<Match> matches = pooledScanner.scan(database, currentText);
            bh.consume(matches);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Log or handle appropriately in real code
            System.err.println("Benchmark interrupted (PooledScanner)");
        } catch (Exception e) {
            // Log or handle exception appropriately in real code
            System.err.println("Exception in PooledScanner benchmark: " + e.getMessage());
        }
        // No explicit acquire/release needed here
    }

    @Benchmark
    @Threads(Threads.MAX) // Use the threadCount parameter
    public void benchmarkStaticCallbackScanner(StaticCallbackScannerState threadState, Blackhole bh) {
        try {
            // Scanner and scratch are pre-allocated in threadState
            List<Match> matches = threadState.staticCallbackScanner.scan(database, currentText);
            bh.consume(matches);
        } catch (Exception e) {
             // Log or handle exception appropriately in real code
             System.err.println("Exception in StaticCallbackScanner benchmark: " + e.getMessage());
             // Optionally rethrow or mark benchmark as failed if needed
        }
        // No close/release here, scanner is reused by the thread
    }

    @Benchmark
    @Threads(Threads.MAX) // Use the threadCount parameter
    public void benchmarkManualScanner(ManualScannerState threadState, Blackhole bh) {
        try {
            // Scanner and scratch are pre-allocated in threadState
            List<Match> matches = threadState.manualScanner.scan(database, currentText);
            bh.consume(matches);
        } catch (Exception e) {
            // Log or handle
        }
        // No close/release here, scanner is reused by the thread
    }

    // Optional: Add a main method to run directly from IDE if needed
    /*
    public static void main(String[] args) throws Exception {
        // Use this to run specific benchmarks or configurations if needed
        // Options options = new OptionsBuilder()
        //         .include(ScannerBenchmark.class.getSimpleName())
        //         .param("threadCount", "4")
        //         .param("textType", "longMany")
        //         .build();
        // new Runner(options).run();
         org.openjdk.jmh.Main.main(args);
    }
    */
}
