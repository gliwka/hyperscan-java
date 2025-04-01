package com.gliwka.hyperscan.wrapper.benchmarks;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * JUnit test to execute the ScannerBenchmark using the JMH Runner API.
 */
public class ScannerBenchmarkRunnerTest {

    @Test
    public void runScannerBenchmarks() throws RunnerException {
        Options opt = new OptionsBuilder()
                // Specify the benchmark class to run
                .include(ScannerBenchmark.class.getName() + ".*") // Use simple name or full regex
                // Set up benchmark modes and iterations
                .measurementIterations(5) // Number of measurement iterations
                .warmupIterations(3)      // Number of warmup iterations
                .forks(1)                 // Number of forks (1 for quicker runs during dev)
                .timeUnit(TimeUnit.MICROSECONDS) // Output time unit
                // Specify the output format and file (optional)
                // .resultFormat(ResultFormatType.JSON)
                // .result("target/jmh-results.json")
                .build();

        new Runner(opt).run();
    }
}
