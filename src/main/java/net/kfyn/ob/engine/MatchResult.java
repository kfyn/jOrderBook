package net.kfyn.ob.engine;

import net.kfyn.ob.entity.Order;
import net.kfyn.ob.entity.Trade;

import java.util.List;

public record MatchResult(List<Trade> trades, long remainingQtyTicks) {
    public MatchResult {
        trades = List.copyOf(trades);
    }
}