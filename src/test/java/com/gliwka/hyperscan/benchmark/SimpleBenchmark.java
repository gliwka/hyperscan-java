package com.gliwka.hyperscan.benchmark;

import com.gliwka.hyperscan.wrapper.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(8)
@State(Scope.Benchmark)
public class SimpleBenchmark {

    private Database dbFewMatches;
    private Database dbManyMatches;
    private String shortString;
    private String longString;

    @State(Scope.Thread)
    public static class ThreadState {
        public Scanner scanner;

        @Setup(Level.Trial)
        public void setupScanner(SimpleBenchmark benchmarkState) throws CompileErrorException {
            scanner = new Scanner();
            scanner.allocScratch(benchmarkState.dbFewMatches);
            scanner.allocScratch(benchmarkState.dbManyMatches);
        }

        @TearDown(Level.Trial)
        public void tearDownScanner() {
            if (scanner != null) {
                try {
                    scanner.close();
                } catch (Exception e) {
                    System.err.println("Error closing thread-local scanner: " + e.getMessage());
                }
            }
        }
    }

    @Setup(Level.Trial)
    public void setup() throws CompileErrorException {
        Expression fewMatchesExpr = new Expression("benchmark", EnumSet.of(ExpressionFlag.CASELESS, ExpressionFlag.SOM_LEFTMOST));
        Expression manyMatchesExpr = new Expression("a", EnumSet.of(ExpressionFlag.CASELESS, ExpressionFlag.SOM_LEFTMOST));

        List<Expression> fewExpressions = new ArrayList<>();
        fewExpressions.add(fewMatchesExpr);
        dbFewMatches = Database.compile(fewExpressions);

        List<Expression> manyExpressions = new ArrayList<>();
        manyExpressions.add(manyMatchesExpr);
        dbManyMatches = Database.compile(manyExpressions);

        shortString = "This is a short benchmark string for testing the hyperscan scanner performance.";
        StringBuilder longStringBuilder = new StringBuilder(shortString.length() * 1000);
        for (int i = 0; i < 1000; i++) {
            longStringBuilder.append(shortString).append(" ");
        }
        longString = longStringBuilder.toString();
    }

    @TearDown(Level.Trial)
    public void tearDownDatabases() {
        if (dbFewMatches != null) {
            try {
                dbFewMatches.close();
            } catch (Exception e) {
                System.err.println("Error closing dbFewMatches: " + e.getMessage());
            }
        }
        if (dbManyMatches != null) {
            try {
                dbManyMatches.close();
            } catch (Exception e) {
                System.err.println("Error closing dbManyMatches: " + e.getMessage());
            }
        }
    }

    @Benchmark
    public void scanShortFewMatches(ThreadState threadState, Blackhole blackhole) {
        List<Match> result = threadState.scanner.scan(dbFewMatches, shortString);
        blackhole.consume(result);
    }

    @Benchmark
    public void scanShortManyMatches(ThreadState threadState, Blackhole blackhole) {
        List<Match> result = threadState.scanner.scan(dbManyMatches, shortString);
        blackhole.consume(result);
    }

    @Benchmark
    public void scanLongFewMatches(ThreadState threadState, Blackhole blackhole) {
        List<Match> result = threadState.scanner.scan(dbFewMatches, longString);
        blackhole.consume(result);
    }

    @Benchmark
    public void scanLongManyMatches(ThreadState threadState, Blackhole blackhole) {
        List<Match> result = threadState.scanner.scan(dbManyMatches, longString);
        blackhole.consume(result);
    }
}
