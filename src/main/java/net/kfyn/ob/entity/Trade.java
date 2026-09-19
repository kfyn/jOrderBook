package net.kfyn.ob.entity;

import net.kfyn.common.number.KNumber;

public record Trade(long bidOrderId, long askOrderId, KNumber px, KNumber qty) {
}
