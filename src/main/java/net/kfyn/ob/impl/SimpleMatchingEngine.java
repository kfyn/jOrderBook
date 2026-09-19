package net.kfyn.ob.impl;

import net.kfyn.ob.entity.Order;
import net.kfyn.ob.entity.OrderBook;
import net.kfyn.ob.entity.Side;
import net.kfyn.ob.entity.Trade;
import net.kfyn.ob.engine.MatchResult;
import net.kfyn.ob.engine.MatchingEngine;

import java.util.*;

/**
 * Price-time priority matching. Priority (comparator, FIFO) is book-owned;
 * this engine owns crossing policy only. Exactly one MatchingEngine may
 * operate on a given OrderBook. Order ids must be unique for the lifetime
 * of the engine. Single-threaded.
 */
public class SimpleMatchingEngine implements MatchingEngine {
    private final OrderBook book;
    private final Map<Long, Order> open = new HashMap<>();
    private final Set<Long> submitted = new HashSet<>();

    public SimpleMatchingEngine(OrderBook book) {
        this.book = Objects.requireNonNull(book, "book");
    }

    @Override public MatchResult submit(Order incoming) {
        Objects.requireNonNull(incoming, "order");
        if (!submitted.add(incoming.id()))
            throw new IllegalArgumentException("duplicate order id: " + incoming.id());

        List<Trade> trades = new ArrayList<>();
        long remaining = incoming.qtyTicks();

        while (remaining > 0) {
            var best = incoming.side() == Side.BUY ? book.bestAsk() : book.bestBid();
            if (best == null || !crosses(incoming, best.getKey())) break;

            Order resting = book.pollBest(incoming.side().opposite());
            long fill = Math.min(remaining, resting.qtyTicks());
            if (resting.qtyTicks() > fill) {
                Order reduced = withQty(resting, resting.qtyTicks() - fill);
                book.requeue(reduced);              // keeps FIFO position
                open.put(reduced.id(), reduced);
            } else {
                open.remove(resting.id());
            }
            trades.add(new SimpleTrade(
                    incoming.side() == Side.BUY ? incoming.id() : resting.id(),
                    incoming.side() == Side.BUY ? resting.id() : incoming.id(),
                    resting.pxTicks(), fill));      // fill at resting price
            remaining -= fill;
        }

        if (remaining > 0) {
            Order remainder = remaining == incoming.qtyTicks() ? incoming : withQty(incoming, remaining);
            book.add(remainder);
            open.put(remainder.id(), remainder);
        }
        return new MatchResult(trades, remaining);
    }

    @Override public boolean cancel(long orderId) {
        Order o = open.get(orderId);
        if (o == null) return false;
        if (!book.remove(o))
            throw new IllegalStateException("open order missing from book: " + orderId);
        open.remove(orderId);
        return true;
    }

    @Override public boolean isOpen(long orderId) {
        return open.containsKey(orderId);
    }

    @Override public int openCount() {
        return open.size();
    }

    private boolean crosses(Order incoming, long bestOppositePx) {
        return incoming.side() == Side.BUY
                ? incoming.pxTicks() >= bestOppositePx
                : incoming.pxTicks() <= bestOppositePx;
    }

    private static Order withQty(Order o, long qtyTicks) {
        return new SimpleOrder(o.id(), o.side(), o.pxTicks(), qtyTicks, o.orderType());
    }
}