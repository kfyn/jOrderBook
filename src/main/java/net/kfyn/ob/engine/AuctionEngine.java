package net.kfyn.ob.engine;

import net.kfyn.ob.entity.Order;

import java.util.List;

public interface AuctionEngine {

    AuctionResult uncross(List<Order> bids, List<Order> asks);

    static AuctionEngine priceTime() {
        return new PriceTimeAuctionEngine();
    }
}