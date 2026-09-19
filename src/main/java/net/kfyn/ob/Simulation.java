package net.kfyn.ob;

import net.kfyn.ob.engine.AuctionEngine;
import net.kfyn.ob.engine.AuctionResult;
import net.kfyn.ob.engine.MatchResult;
import net.kfyn.ob.engine.PriceTimeMatchingEngine;
import net.kfyn.ob.engine.PriceTimeOrderBook;
import net.kfyn.ob.entity.Instrument;
import net.kfyn.ob.entity.Order;
import net.kfyn.ob.entity.OrderType;
import net.kfyn.ob.entity.Side;
import net.kfyn.ob.entity.SimpleOrder;
import net.kfyn.ob.entity.Trade;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/**
 * Deterministic end-to-end scenario: a resting price-time book, an
 * opening call auction on top of it, and the continuous matcher draining
 * the auction leftovers (rest-on-book policy).
 *
 * Hard numbers of the scenario (both phases are exact):
 * - resting book: bids 99..95 and asks 101..105, qty 10 each
 * - auction batch: bids {102x15, 101x8}, asks {98x20, 99x5}
 *   → clearing price 99 (max volume 23; tie with 101 broken by sell
 *   pressure → lower price), 3 trades, leftover ask 2@99
 * - leftover rests on the book, then a buy 99x10 partially drains it
 *   (2 filled against the rested ask at 99), remainder rests
 */
public final class Simulation {

    private Simulation() {
    }

    public record Outcome(
            AuctionResult auction,
            List<Trade> engineTrades,
            int openOrders,
            long submittedQtyTicks,
            long openQtyTicks,
            long tradedQtyTicks,
            OptionalLong bestBidTicks,
            OptionalLong bestAskTicks,
            Instrument instrument) {

        public Outcome {
            engineTrades = List.copyOf(engineTrades);
        }
    }

    public static Outcome run() {
        Instrument btc = Instrument.of("BTCUSDT", "0.10", "0.001");
        PriceTimeOrderBook book = new PriceTimeOrderBook(btc);
        PriceTimeMatchingEngine engine = new PriceTimeMatchingEngine(book);

        long id = 0;
        long submitted = 0;
        // Phase A: resting book, 10 orders
        for (int p = 99; p >= 95; p--) {
            engine.submit(order(++id, Side.BUY, p, 10));
            submitted += 10;
        }
        for (int p = 101; p <= 105; p++) {
            engine.submit(order(++id, Side.SELL, p, 10));
            submitted += 10;
        }

        // Phase B: opening call auction (batch ticks, incl. the later-rested leftover)
        AuctionResult auction = AuctionEngine.priceTime().uncross(
                List.of(order(++id, Side.BUY, 102, 15), order(++id, Side.BUY, 101, 8)),
                List.of(order(++id, Side.SELL, 98, 20), order(++id, Side.SELL, 99, 5)));
        submitted += 15 + 8 + 20 + 5;

        // Phase C: rest auction leftovers on the book (opening-auction policy),
        // then continue with continuous matching
        List<Trade> engineTrades = new ArrayList<>();
        for (Order leftover : auction.leftovers()) {
            engineTrades.addAll(engine.submit(leftover).trades());
        }
        MatchResult phased = engine.submit(order(++id, Side.BUY, 99, 10));
        engineTrades.addAll(phased.trades());
        submitted += 10;

        List<Trade> allTrades = new ArrayList<>(auction.trades());
        allTrades.addAll(engineTrades);
        Outcome outcome = new Outcome(
                auction,
                engineTrades,
                engine.openCount(),
                submitted,
                openQty(book),
                allTrades.stream().mapToLong(Trade::qtyTicks).sum(),
                keyOrEmpty(book.bestBid()),
                keyOrEmpty(book.bestAsk()),
                btc);

        // fail-loud invariant: every submitted tick is open or traded once per side
        if (outcome.submittedQtyTicks() != outcome.openQtyTicks() + 2 * outcome.tradedQtyTicks()) {
            throw new IllegalStateException("conservation violated: submitted=" + outcome.submittedQtyTicks()
                    + " open=" + outcome.openQtyTicks() + " traded=" + outcome.tradedQtyTicks());
        }
        return outcome;
    }

    private static long openQty(PriceTimeOrderBook book) {
        long total = 0;
        for (var level : book.bids().values()) {
            for (Order o : level) {
                total = Math.addExact(total, o.qtyTicks());
            }
        }
        for (var level : book.asks().values()) {
            for (Order o : level) {
                total = Math.addExact(total, o.qtyTicks());
            }
        }
        return total;
    }

    private static OptionalLong keyOrEmpty(java.util.Map.Entry<Long, ?> best) {
        return best == null ? OptionalLong.empty() : OptionalLong.of(best.getKey());
    }

    private static Order order(long id, Side side, long pxTicks, long qtyTicks) {
        return new SimpleOrder(id, side, pxTicks, qtyTicks, OrderType.LIMIT);
    }
}