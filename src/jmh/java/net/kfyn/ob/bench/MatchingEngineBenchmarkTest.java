package net.kfyn.ob.bench;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the JMH benchmarks in-process (forks(0)) and surfaces each one as
 * a JUnit dynamic test whose name carries the measured throughput, so the
 * GitLab MR tests widget shows per-benchmark results. The floor is
 * deliberately low — CI is noisy, so this is a run + sanity gate, not a
 * regression gate.
 */
class MatchingEngineBenchmarkTest {

    private static final double FLOOR_OPS_PER_SEC = 100_000.0;

    @TestFactory
    Stream<DynamicTest> throughputAboveFloor() {
        return Arrays.stream(new String[]{"submitResting", "submitCrossing", "cancelResubmit"})
                .map(MatchingEngineBenchmarkTest::runBenchmark);
    }

    private static DynamicTest runBenchmark(String name) {
        RunResult result = measure(name);
        double score = result.getPrimaryResult().getScore();
        String unit = result.getPrimaryResult().getScoreUnit();
        String label = String.format(Locale.ROOT, "%s: %,.0f %s", name, score, unit);
        return DynamicTest.dynamicTest(label, () ->
                assertTrue(score >= FLOOR_OPS_PER_SEC,
                        name + " below floor " + FLOOR_OPS_PER_SEC + " " + unit + ": " + score));
    }

    private static RunResult measure(String name) {
        try {
            var options = new OptionsBuilder()
                    .include("net\\.kfyn\\.ob\\.bench\\.MatchingEngineBenchmark\\." + name)
                    .forks(0)
                    .warmupIterations(1)
                    .warmupTime(TimeValue.seconds(1))
                    .measurementIterations(1)
                    .measurementTime(TimeValue.seconds(1))
                    .shouldFailOnError(true)
                    .build();
            var results = new Runner(options).run();
            if (results.isEmpty()) {
                throw new IllegalStateException("no results for " + name);
            }
            return results.iterator().next();
        } catch (Exception e) {
            throw new IllegalStateException("benchmark failed: " + name, e);
        }
    }
}