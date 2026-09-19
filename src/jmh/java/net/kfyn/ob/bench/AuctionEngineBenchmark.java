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

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Benchmark)
public class AuctionEngineBenchmark {

    @Param({"16", "256"})
    int levels;

    /** Orders resting at each price level. */
    @Param({"1", "4", "16"})
    int ordersPerLevel;

    List<Order> crossedBids;
    List<Order> crossedAsks;
    List<Order> gappedBids;
    List<Order> gappedAsks;

    @Setup(Level.Trial)
    public void setup() {
        int n = levels * ordersPerLevel;
        crossedBids = new ArrayList<>(n);
        crossedAsks = new ArrayList<>(n);
        gappedBids = new ArrayList<>(n);
        gappedAsks = new ArrayList<>(n);
        long id = 1;
        for (int i = 0; i < levels; i++) {
            // tick size $1; slight qty mix so levels are not all identical
            long crossedBidPx = 100 + i;
            long crossedAskPx = 100 - i;
            long gappedBidPx = 100 + i;
            // far above every bid for any `levels` value (max bid = 100 + levels - 1)
            long gappedAskPx = 1000 + i;
            for (int k = 0; k < ordersPerLevel; k++) {
                long qty = 100 + 25 * (k % 4);
                crossedBids.add(new SimpleOrder(id++, Side.BUY, crossedBidPx, qty, OrderType.LIMIT));
                crossedAsks.add(new SimpleOrder(id++, Side.SELL, crossedAskPx, qty, OrderType.LIMIT));
                gappedBids.add(new SimpleOrder(id++, Side.BUY, gappedBidPx, qty, OrderType.LIMIT));
                gappedAsks.add(new SimpleOrder(id++, Side.SELL, gappedAskPx, qty, OrderType.LIMIT));
            }
        }
    }

    @Benchmark
    public AuctionResult uncrossCrossed() {
        return AuctionEngine.priceTime().uncross(crossedBids, crossedAsks);
    }

    @Benchmark
    public AuctionResult uncrossNoCross() {
        // Truly uncrossed book: exercises the engine's early-exit path
        // (no aggregation, no sweep, single leftovers copy).
        return AuctionEngine.priceTime().uncross(gappedBids, gappedAsks);
    }
}