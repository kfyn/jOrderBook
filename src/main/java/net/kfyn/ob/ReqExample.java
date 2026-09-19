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
 * limit order book from the spec, uncrossed by the maximum-volume
 * auction algorithm.
 */
public final class ReqExample {

    private ReqExample() {
    }

    /** Tick size $1, quantity unit 1 share. */
    static final Instrument BHP = Instrument.of("BHP", "1", "1");

    /**
     * Spec book: bids 102@50000, 1000@99, 700@98;
     * asks 100@100, 200@99, 500@96.
     */
    public static AuctionResult uncrossBook() {
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
}