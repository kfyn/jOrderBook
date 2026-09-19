package net.kfyn.ob.engine;

import net.kfyn.ob.entity.Order;
import net.kfyn.ob.entity.Trade;

import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Outcome of an auction uncrossing.
 *
 * @param priceTicks  clearing price when crossed; {@code empty} when no cross occurs
 * @param volumeTicks total executed quantity (sum of trade quantities)
 * @param trades      executed trades, all at {@code priceTicks}
 * @param leftovers   unexecuted orders: untouched orders as-is, partially
 *                    filled orders as new instances with reduced qty
 */
public record AuctionResult(OptionalLong priceTicks, long volumeTicks, List<Trade> trades, List<Order> leftovers) {
    public AuctionResult {
        Objects.requireNonNull(priceTicks, "priceTicks");
        if (volumeTicks < 0) throw new IllegalArgumentException("volumeTicks must be >= 0: " + volumeTicks);
        trades = List.copyOf(trades);
        leftovers = List.copyOf(leftovers);
    }
}