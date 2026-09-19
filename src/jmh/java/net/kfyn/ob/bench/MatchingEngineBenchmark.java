package net.kfyn.ob.bench;

import net.kfyn.ob.entity.Instrument;
import net.kfyn.ob.entity.OrderType;
import net.kfyn.ob.entity.Side;
import net.kfyn.ob.engine.PriceTimeOrderBook;
import net.kfyn.ob.engine.PriceTimeMatchingEngine;
import net.kfyn.ob.entity.SimpleOrder;
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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * MatchingEngine hot-path benchmarks. Each benchmark has one @Param axis:
 *  - submitResting:   non-crossing submit (rest path); the book spreads over
 *                     `priceLevels` prices so level-tree depth grows with it
 *  - submitCrossing:  buy that sweeps and fully fills `levels` resting asks
 *                     (one qty-1 order per level, 100..100+levels-1); the
 *                     ask levels are re-added to keep the book invariant
 *  - cancelResubmit:  cancel an open order and submit a fresh one in its
 *                     place (lifetime-unique ids, constant book size =
 *                     `bookSize`; level removal is O(level size))
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@Threads(1)
public class MatchingEngineBenchmark {

    private static final Instrument BTC = Instrument.of("BTCUSDT", "0.10", "0.001");

    private static SimpleOrder order(long id, Side side, long pxTicks, long qtyTicks) {
        return new SimpleOrder(id, side, pxTicks, qtyTicks, OrderType.LIMIT);
    }

    /** Empty book: every submitted order rests. @Param: number of price levels. */
    @State(Scope.Thread)
    public static class RestingState {
        @Param({"1", "16", "256"})
        public int priceLevels;

        PriceTimeOrderBook book;
        PriceTimeMatchingEngine engine;
        long nextId;

        @Setup(Level.Iteration)
        public void up() {
            book = new PriceTimeOrderBook(BTC);
            engine = new PriceTimeMatchingEngine(book);
            nextId = 0;
        }
    }

    /** `levels` resting asks at 100..100+levels-1, qty 1: every buy sweeps them all. */
    @State(Scope.Thread)
    public static class CrossingState {
        @Param({"1", "8", "32"})
        public int levels;

        PriceTimeOrderBook book;
        PriceTimeMatchingEngine engine;
        long nextId;

        @Setup(Level.Iteration)
        public void up() {
            book = new PriceTimeOrderBook(BTC);
            engine = new PriceTimeMatchingEngine(book);
            nextId = 1;
            for (int i = 0; i < levels; i++) {
                book.add(order(nextId++, Side.SELL, 100 + i, 1));
            }
        }
    }

    /** `bookSize` open bids at 100: cancel cycle + fresh submit keeps size constant. */
    @State(Scope.Thread)
    public static class CancelState {
        @Param({"64", "1024", "8192"})
        public long bookSize;

        PriceTimeOrderBook book;
        PriceTimeMatchingEngine engine;
        long nextId;
        long cursor;

        @Setup(Level.Iteration)
        public void up() {
            book = new PriceTimeOrderBook(BTC);
            engine = new PriceTimeMatchingEngine(book);
            nextId = bookSize;
            cursor = 0;
            for (long id = 1; id <= bookSize; id++) {
                engine.submit(order(id, Side.BUY, 100, 1));
            }
        }
    }

    @Benchmark
    public long submitResting(RestingState s) {
        long id = ++s.nextId;
        return s.engine.submit(order(id, Side.BUY, 90 + (int) ((id - 1) % s.priceLevels), 1))
                .remainingQtyTicks();
    }

    @Benchmark
    public long submitCrossing(CrossingState s) {
        long buyId = ++s.nextId;
        long trades = s.engine.submit(order(buyId, Side.BUY, 100 + s.levels, s.levels)).trades().size();
        for (int i = 0; i < s.levels; i++) {
            s.book.add(order(s.nextId++, Side.SELL, 100 + i, 1));   // restore invariant
        }
        return trades;
    }

    @Benchmark
    public long cancelResubmit(CancelState s) {
        long canceled = s.cursor++ % s.bookSize + 1;
        boolean ok = s.engine.cancel(canceled);
        long freshId = ++s.nextId;
        s.engine.submit(order(freshId, Side.BUY, 100, 1));
        return ok ? freshId : -1;
    }
}