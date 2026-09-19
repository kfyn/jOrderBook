package net.kfyn.ob.bench;

import net.kfyn.ob.entity.Order;
import net.kfyn.ob.entity.OrderType;
import net.kfyn.ob.entity.Side;
import net.kfyn.ob.entity.SimpleOrder;
import net.kfyn.ob.engine.AuctionEngine;
import net.kfyn.ob.engine.AuctionResult;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * PriceTimeAuctionEngine uncross benchmarks. One @Param axis:
 *  - levels: distinct price levels per side. uncrossCrossed builds a
 *    symmetric overlapping ladder (best bid == best ask), so every run
 *    executes the full price-discovery sweep and the FIFO allocation;
 *    uncrossNoCross books the same size with a wide gap, exercising the
 *    sweep + no-trade exit path.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Benchmark)
public class AuctionEngineBenchmark {

    @Param({"16", "256"})
    int levels;

    List<Order> crossedBids;
    List<Order> crossedAsks;
    List<Order> gappedBids;
    List<Order> gappedAsks;

    @Setup(Level.Trial)
    public void setup() {
        crossedBids = new ArrayList<>(levels);
        crossedAsks = new ArrayList<>(levels);
        gappedBids = new ArrayList<>(levels);
        gappedAsks = new ArrayList<>(levels);
        long id = 1;
        for (int i = 0; i < levels; i++) {
            // tick size $1, quantity 100 shares per level
            crossedBids.add(new SimpleOrder(id++, Side.BUY, 100 + i, 100, OrderType.LIMIT));
            crossedAsks.add(new SimpleOrder(id++, Side.SELL, 100 - i, 100, OrderType.LIMIT));
            gappedBids.add(new SimpleOrder(id++, Side.BUY, 200 + i, 100, OrderType.LIMIT));
            gappedAsks.add(new SimpleOrder(id++, Side.SELL, 50 - i, 100, OrderType.LIMIT));
        }
    }

    @Benchmark
    public AuctionResult uncrossCrossed() {
        return AuctionEngine.priceTime().uncross(crossedBids, crossedAsks);
    }

    @Benchmark
    public AuctionResult uncrossNoCross() {
        return AuctionEngine.priceTime().uncross(gappedBids, gappedAsks);
    }
}