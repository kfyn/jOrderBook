package net.kfyn.ob.bench;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the JMH benchmarks in-process (forks(0)) and surfaces every
 * benchmark × parameter instance as a JUnit dynamic test whose name
 * carries the measured throughput, so the GitLab MR tests widget shows
 * per-instance results. The floor is deliberately low — CI is noisy, so
 * this is a run + sanity gate, not a regression gate.
 */
class MatchingEngineBenchmarkTest {

    private static final double FLOOR_OPS_PER_SEC = 100_000.0;

    @TestFactory
    Stream<DynamicTest> throughputAboveFloor() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String name : Arrays.asList("submitResting", "submitCrossing", "cancelResubmit")) {
            for (RunResult result : measure(name)) {
                tests.add(toTest(name, result));
            }
        }
        return tests.stream();
    }

    private static DynamicTest toTest(String name, RunResult result) {
        double score = result.getPrimaryResult().getScore();
        String unit = result.getPrimaryResult().getScoreUnit();
        String params = result.getParams().getParamsKeys().stream()
                .sorted()
                .map(key -> key + "=" + result.getParams().getParam(key))
                .collect(Collectors.joining(", "));
        String label = params.isEmpty()
                ? String.format(Locale.ROOT, "%s: %,.0f %s", name, score, unit)
                : String.format(Locale.ROOT, "%s[%s]: %,.0f %s", name, params, score, unit);
        return DynamicTest.dynamicTest(label, () ->
                assertTrue(score >= FLOOR_OPS_PER_SEC,
                        name + " below floor " + FLOOR_OPS_PER_SEC + " " + unit + ": " + score));
    }

    private static Collection<RunResult> measure(String name) {
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
            return new Runner(options).run();
        } catch (Exception e) {
            throw new IllegalStateException("benchmark failed: " + name, e);
        }
    }
}