package com.gliwka.hyperscan.wrapper.performance;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.text.DecimalFormat;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

public class PerformanceRegressionTest {
    private static DecimalFormat df = new DecimalFormat("0.000");
    private static final double REFERENCE_SCORE = 255043.149;  //measured on github actions
    private static final double MAX_DEVIATION = 0.20; //variance on github actions

    @Test
    public void runJmhBenchmark() throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(PerformanceRegressionBenchmark.class.getSimpleName())
                .threads(2)
                .forks(3)
                .build();
        Collection<RunResult> runResults = new Runner(opt).run();
        assertFalse(runResults.isEmpty());
        for(RunResult runResult : runResults) {
            assertDeviationWithin(runResult, REFERENCE_SCORE, MAX_DEVIATION);
        }
    }

    private static void assertDeviationWithin(RunResult result, double referenceScore, double maxDeviation) {
        double score = result.getPrimaryResult().getScore();
        double deviation = Math.abs(score/referenceScore - 1);
        String deviationString = df.format(deviation * 100) + "%";
        String maxDeviationString = df.format(maxDeviation * 100) + "%";
        String errorMessage = "Deviation " + deviationString + " exceeds maximum allowed deviation " + maxDeviationString;
        assertTrue(deviation < maxDeviation, errorMessage);
    }
}
