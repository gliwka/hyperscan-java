package com.gliwka.hyperscan.wrapper.performance;

import com.gliwka.hyperscan.wrapper.CompileErrorException;
import com.gliwka.hyperscan.wrapper.Database;
import com.gliwka.hyperscan.wrapper.Expression;
import com.gliwka.hyperscan.wrapper.Scanner;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.Throughput)
public class PerformanceRegressionBenchmark {
    public static Database db;

    @Benchmark
    public void benchmarkASCII(ThreadState state) {
        state.scanner.scan(db, "The quick brown fox jumps over the lazy dog is an English-language " +
                "pangram—a sentence that contains all of the letters of the English alphabet.");
    }

    @Benchmark
    public void benchmarkUTF8(ThreadState state) {
        state.scanner.scan(db, "\uD83D\uDE00The quick brown fox jumps over the lazy dog is an English-language " +
                "pangram—a sentence that contains all of the letters of the English alphabet.");
    }

    static {
        try {
            db = Database.compile(new Expression("The quick brown fox jumps over the lazy dog"));
        } catch (CompileErrorException e) {
            throw new RuntimeException(e);
        }
    }

    @State(Scope.Thread)
    public static class ThreadState {
        public Scanner scanner;
        {
            scanner = new Scanner();
            scanner.allocScratch(db);
        }
    }
}
