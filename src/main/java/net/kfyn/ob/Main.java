package net.kfyn.ob;

import net.kfyn.ob.engine.AuctionEngine;
import net.kfyn.ob.engine.AuctionResult;
import net.kfyn.ob.entity.Instrument;
import net.kfyn.ob.entity.Order;
import net.kfyn.ob.entity.OrderType;
import net.kfyn.ob.entity.Side;
import net.kfyn.ob.entity.SimpleOrder;

import java.util.List;

/**
 * Prints the auction uncrossing of the order book from the problem
 * statement: clearing price, matched volume, trades and leftovers.
 */
public final class Main {

    private Main() {
    }

    /** Tick size $1, quantity unit 1 share. */
    static final Instrument BHP = Instrument.of("BHP", "1", "1");

    public static void main(String[] args) {
        printDemo();
    }

    /**
     * Spec book: bids 102@50000, 1000@99, 700@98;
     * asks 100@100, 200@99, 500@96.
     */
    static AuctionResult uncrossBook() {
        return AuctionEngine.priceTime().uncross(bids(), asks());
    }

    static List<Order> bids() {
        return List.of(
                share(1, Side.BUY, 50000, 102),
                share(2, Side.BUY, 99, 1000),
                share(3, Side.BUY, 98, 700));
    }

    static List<Order> asks() {
        return List.of(
                share(4, Side.SELL, 100, 100),
                share(5, Side.SELL, 99, 200),
                share(6, Side.SELL, 96, 500));
    }

    private static Order share(long id, Side side, long pxTicks, long qtyTicks) {
        return new SimpleOrder(id, side, pxTicks, qtyTicks, OrderType.LIMIT);
    }

    private static void printDemo() {
        System.out.printf("book: bids 102@50000, 1000@99, 700@98 | asks 100@100, 200@99, 500@96%n");
        AuctionResult r = uncrossBook();
        if (r.priceTicks().isEmpty()) {
            System.out.println("no cross: no matching auction price");
            return;
        }
        long px = r.priceTicks().getAsLong();
        long buyQty = bids().stream().filter(o -> o.pxTicks() >= px).mapToLong(Order::qtyTicks).sum();
        long sellQty = asks().stream().filter(o -> o.pxTicks() <= px).mapToLong(Order::qtyTicks).sum();
        System.out.printf("matching auction price : %s%n", BHP.pxValue(px));
        System.out.printf("total matched volume   : %s share(s)%n", BHP.qtyValue(r.volumeTicks()));
        System.out.printf("imbalance at the price : %d buy vs %d sell -> %d extra %s share(s)%n",
                buyQty, sellQty, Math.abs(buyQty - sellQty), buyQty > sellQty ? "buy" : "sell");
        System.out.printf("trades                 : %d, all at the clearing price%n", r.trades().size());
        for (var t : r.trades()) {
            System.out.printf("  trade    : bid #%d <-> ask #%d, %s share(s) @ %s%n",
                    t.bidOrderId(), t.askOrderId(), BHP.qtyValue(t.qtyTicks()), BHP.pxValue(t.pxTicks()));
        }
        System.out.printf("unfilled (leftover) orders : %d%n", r.leftovers().size());
        for (Order o : r.leftovers()) {
            System.out.printf("  leftover : #%d %s %s share(s) @ %s%n",
                    o.id(), o.side(), BHP.qtyValue(o.qtyTicks()), BHP.pxValue(o.pxTicks()));
        }
    }
}