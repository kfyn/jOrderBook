package net.kfyn.ob.bench;

import net.kfyn.ob.entity.Instrument;
import net.kfyn.ob.entity.Order;
import net.kfyn.ob.entity.OrderType;
import net.kfyn.ob.entity.Side;
import net.kfyn.ob.entity.SimpleInstrument;
import net.kfyn.ob.entity.SimpleOrder;
import net.kfyn.ob.engine.AuctionResult;
import net.kfyn.ob.engine.PriceTimeOrderBook;
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
 * Book operations: add / amend / cancel / uncross / close on a crossed
 * (realistic-to-uncross) resting book. Mutating benchmarks return the book
 * to its steady state (same orders, same levels) so repeated invocations
 * measure the same structure.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Benchmark)
public class PriceTimeOrderBookBenchmark {

    @Param({"16", "256"})
    int levels;

    @Param({"1", "16"})
    int ordersPerLevel;

    Instrument instrument;
    PriceTimeOrderBook book;
    Order[] resting;          // current instance of every resting order
    long[] originalQty;       // qty each order started with

    @Setup(Level.Trial)
    public void setup() {
        instrument = SimpleInstrument.of("BENCH", "1", "1");
        List<Order> bids = new ArrayList<>(levels * ordersPerLevel);
        List<Order> asks = new ArrayList<>(levels * ordersPerLevel);
        long id = 1;
        for (int i = 0; i < levels; i++) {
            long bidPx = 100 + i;
            long askPx = 100 - i;   // crossed book
            for (int k = 0; k < ordersPerLevel; k++) {
                long qty = 100 + 25 * (k % 4);
                bids.add(new SimpleOrder(id++, Side.BUY, bidPx, qty, OrderType.LIMIT));
                asks.add(new SimpleOrder(id++, Side.SELL, askPx, qty, OrderType.LIMIT));
            }
        }
        book = new PriceTimeOrderBook(instrument);
        resting = new Order[bids.size() + asks.size()];
        originalQty = new long[resting.length];
        int i = 0;
        for (Order o : bids) {
            book.add(o);
            resting[i] = o;
            originalQty[i] = o.qtyTicks();
            i++;
        }
        for (Order o : asks) {
            book.add(o);
            resting[i] = o;
            originalQty[i] = o.qtyTicks();
            i++;
        }
    }

    /** Cost of populating a fresh book from scratch. */
    @Benchmark
    public PriceTimeOrderBook addAll() {
        PriceTimeOrderBook b = new PriceTimeOrderBook(instrument);
        for (Order o : resting) {
            b.add(o);
        }
        return b;
    }

    /**
     * One amend cycle over the whole book: every order reduced in place
     * (same level, keeps queue position) then restored (qty increase, loses
     * time priority). Book ends with the same ids/px/qty as it started.
     */
    @Benchmark
    public PriceTimeOrderBook amendCycle() {
        for (int i = 0; i < resting.length; i++) {
            Order o = resting[i];
            resting[i] = book.amend(o, o.pxTicks(), o.qtyTicks() / 2);
        }
        for (int i = 0; i < resting.length; i++) {
            Order o = resting[i];
            resting[i] = book.amend(o, o.pxTicks(), originalQty[i]);
        }
        return book;
    }

    /** Cancel every order, then re-add them: measures remove + reinsert. */
    @Benchmark
    public PriceTimeOrderBook cancelCycle() {
        for (Order o : resting) {
            book.remove(o);
        }
        for (Order o : resting) {
            book.add(o);
        }
        return book;
    }

    /** Pure uncross query on the resting book (no state change). */
    @Benchmark
    public AuctionResult uncrossBook() {
        return book.uncross();
    }

    /** Full auction lifecycle: build a fresh book and close the auction. */
    @Benchmark
    public AuctionResult addAndClose() {
        PriceTimeOrderBook b = new PriceTimeOrderBook(instrument);
        for (Order o : resting) {
            b.add(o);
        }
        return b.close();
    }
}