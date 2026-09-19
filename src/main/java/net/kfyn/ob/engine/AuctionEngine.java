package net.kfyn.ob.engine;

import net.kfyn.ob.entity.Order;

import java.util.List;

/**
 * Call auction (uncrossing): Stateless and single-threaded.
 *
 * <p>Tie-break, used when several candidate prices share the maximum
 * volume: prefer the price with the smallest demand/supply imbalance;
 * among equal-imbalance ties pick the highest price under pure buy
 * pressure, otherwise the lowest.
 */
public interface AuctionEngine {

    AuctionResult uncross(List<Order> bids, List<Order> asks);

    static AuctionEngine priceTime() {
        return new PriceTimeAuctionEngine();
    }
}