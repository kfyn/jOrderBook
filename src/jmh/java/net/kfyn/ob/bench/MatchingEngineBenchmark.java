package net.kfyn.ob.bench;

import net.kfyn.ob.engine.MatchingEngine;
import net.kfyn.ob.entity.Instrument;
import net.kfyn.ob.entity.OrderType;
import net.kfyn.ob.entity.Side;
import net.kfyn.ob.impl.PriceTimeOrderBook;
import net.kfyn.ob.impl.SimpleMatchingEngine;
import net.kfyn.ob.impl.SimpleOrder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * MatchingEngine hot-path benchmarks. Matching is single-threaded by
 * design, so each benchmark runs on one thread with per-iteration state
 * (fresh book + engine) so accumulated positions cannot skew numbers.
 *
 * Benchmarks:
 *  - submitResting:   submit a non-crossing order (rest path, book grows
 *                     in one price level — per-op cost stays O(1))
 *  - submitCrossing:  full fill against one resting ask (cross path);
 *                     the ask is re-added to keep the book invariant
 *  - cancelResubmit:  cancel an open order and submit a fresh one in its
 *                     place (lifetime-unique ids, book size constant)
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

    /** Empty book: every submitted order rests. */
    @State(Scope.Thread)
    public static class RestingState {
        PriceTimeOrderBook book;
        SimpleMatchingEngine engine;
        long nextId;

        @Setup(Level.Iteration)
        public void up() {
            book = new PriceTimeOrderBook(BTC);
            engine = new SimpleMatchingEngine(book);
            nextId = 0;
        }
    }

    /** One resting ask at 100: every submitted buy crosses and fully fills. */
    @State(Scope.Thread)
    public static class CrossingState {
        PriceTimeOrderBook book;
        SimpleMatchingEngine engine;
        long nextId;

        @Setup(Level.Iteration)
        public void up() {
            book = new PriceTimeOrderBook(BTC);
            engine = new SimpleMatchingEngine(book);
            nextId = 1;
            book.add(order(nextId, Side.SELL, 100, 1));
        }
    }

    /** 1024 open bids at 100: cancel cycle + fresh submit keeps size constant. */
    @State(Scope.Thread)
    public static class CancelState {
        static final long OPEN = 1024;

        PriceTimeOrderBook book;
        SimpleMatchingEngine engine;
        long nextId;
        long cursor;

        @Setup(Level.Iteration)
        public void up() {
            book = new PriceTimeOrderBook(BTC);
            engine = new SimpleMatchingEngine(book);
            nextId = OPEN;
            cursor = 0;
            for (long id = 1; id <= OPEN; id++) {
                engine.submit(order(id, Side.BUY, 100, 1));
            }
        }
    }

    @Benchmark
    public long submitResting(RestingState s) {
        long id = ++s.nextId;
        return s.engine.submit(order(id, Side.BUY, 90, 1)).remainingQtyTicks();
    }

    @Benchmark
    public long submitCrossing(CrossingState s) {
        long buyId = ++s.nextId;
        long trades = s.engine.submit(order(buyId, Side.BUY, 100, 1)).trades().size();
        s.book.add(order(++s.nextId, Side.SELL, 100, 1));   // restore invariant
        return trades;
    }

    @Benchmark
    public long cancelResubmit(CancelState s) {
        long canceled = s.cursor++ % CancelState.OPEN + 1;
        boolean ok = s.engine.cancel(canceled);
        long freshId = ++s.nextId;
        s.engine.submit(order(freshId, Side.BUY, 100, 1));
        return ok ? freshId : -1;
    }
}