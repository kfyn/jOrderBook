# jOrderBook

Java solution to the exchange-auction order-book exercise: build a limit order
book, let participants **submit, amend and cancel** orders during the auction
phase (no matching while the auction runs), then uncross the book at a single
clearing price found by the **Maximum Volume Matching** algorithm — the price
that maximizes the number of shares traded. The total matched volume at that
price is reported as well.

## Requirements

- JDK 25 (Gradle wrapper included; no other setup)

## Build & test

```sh
./gradlew test        # unit tests + JaCoCo coverage (gate: 90% line coverage)
./gradlew jmhSmoke    # quick benchmark smoke run (CI gate)
./gradlew jmh         # full benchmark suite
```

## Run the demo

```sh
./gradlew run
```

Prints the order book from the problem statement, the matching auction price,
the total matched volume, every trade executed at the clearing price, and the
unfilled (leftover) orders:

```
book: bids 100@100, 1000@99, 500@96 | asks 50000@102, 200@99, 700@98
matching auction price : 99
total matched volume   : 900 share(s)
imbalance at the price : 1100 buy vs 900 sell -> 200 extra buy share(s)
trades                 : 3, all at the clearing price
  trade    : bid #1 <-> ask #5, 100 share(s) @ 99
  ...
unfilled (leftover) orders : 3
  ...
```

## How the auction price is found

For each candidate price `p` (every price that appears on either side):

- `demand(p)` = total buy quantity priced >= `p`
- `supply(p)` = total sell quantity priced <= `p`
- `volume(p)` = `min(demand(p), supply(p))`

The clearing price is the candidate with the highest `volume(p)`; the total
matched volume is that volume. Buy orders priced >= the clearing price and
sell orders priced <= it are eligible; allocation is price-time (FIFO) at the
clearing price, and unexecuted remainder is reported as leftover orders.

Priority ownership: the engine only computes the clearing price and allocates
in the exact order it is given — it sorts nothing and has no source of time.
Price-then-time priority is established by `PriceTimeOrderBook`, which flattens
the book by descending bid price (ascending ask price) and FIFO within each
level. Callers of `AuctionEngine.uncross` directly must pass bids/asks already
ordered by price/time; otherwise eligible orders are filled in the supplied
order and a better-priced order can be filled last.

An uncrossed book (best bid below best ask) cannot trade at any price, so the
engine returns the no-cross result without aggregating or sweeping; the touch
case (best bid == best ask) still crosses.

Tie-break (several prices with the same maximum volume): prefer the price
with the smallest demand/supply imbalance; on equal imbalance the highest tied
price under pure buy pressure, otherwise the lowest. The problem statement
leaves this open — the rule is deterministic and covered by tests.

Orders are identified by their id, and the book/engine treat an order as equal
to any value-equal instance (the `Order` records have no hidden identity).
Passing a value-equal instance where the resting order is expected is
supported; callers that create their own instances must keep ids unique.

`PriceTimeOrderBook` runs the same uncross directly on the resting book:
`uncross()` is a pure query (no state change); `close()` additionally settles
the book to the auction outcome — fully filled orders removed, partially
filled orders reduced (new instances, same id), unfilled orders left resting —
and returns the result.

## Outstanding work

Deliberately out of scope for this exercise; listed so the current boundaries
are explicit.

**Order types**
- **Market orders**: only limit orders exist (`OrderType` has a single `LIMIT`
  constant). A market order has no price and must not rest on a level; supporting
  it needs a separate book side (or a sentinel price), an eligibility rule at the
  clearing price, and a rule for residual unfilled quantity after the auction.

**Instrument tick/lot constraints**
- **Price tick ladder**: prices are only validated as whole price ticks
  (`SimpleInstrument.pxTicks` rejects off-tick values), but there is no explicit
  ladder/schedule of permitted prices beyond a fixed tick size. Supporting a real
  ladder (per-price-band tick sizes, or an enumerated set of permitted prices)
  would move validation and rounding into the instrument.
- **Quantity lot sizes**: quantities use a fixed lot (`SimpleInstrument`
  quantity tick) and reject non-multiples; there is no round-lot/minimum-lot
  model, no lot-size change over time, and no handling of residual odd lots.

**Book & engine**
- Duplicate order ids are not enforced (`add()` accepts them); the book and
  engine treat orders as value-equal, so callers must keep ids unique.
- `remove()`/`amend()` are O(level size) (linear scan of the level's queue);
  fine at this scale, an intrusive linked list would make them O(1).
- The auction result materialises every trade and leftover in memory; very large
  crossed books would need streaming or a capped result.
- `bids()`/`asks()` return read-only maps whose level collections remain live.

## Layout

| Path | Contents |
|---|---|
| `src/main/java/net/kfyn/ob/entity` | Order, Trade, Instrument, OrderBook abstractions |
| `src/main/java/net/kfyn/ob/engine` | Price/time order book and maximum-volume auction engine (`MaxVolAuctionEngine`) |
| `src/main/java/net/kfyn/ob/Main.java` | Demo: uncrosses the book from the problem statement |
| `src/test/java` | JUnit 6 tests incl. a randomized auction-vs-reference property test |
| `src/jmh/java` | JMH benchmarks of the auction uncross (separate source set; JMH never leaks into main/test code) |

External dependencies: JUnit 6 (test scope only) and JMH (benchmark source
set only). The main and test code uses only the JDK.

## Interpreting the JMH results

The numbers in `build/jmh/results.json` are **relative** micro-benchmark results, not
absolute capacity or latency figures. They are only meaningful when comparing builds,
commits or code changes measured **on the same host, with the same JDK build, the same
JVM flags and under comparable load**. Do not compare scores across machines, CI
runners, cloud instances or JDK vendors/builds, and do not quote an "ops/s" figure as
a service guarantee: on this project the same engine measured roughly twice as fast on
a local machine as on a shared CI runner (e.g. `uncrossNoCross` 256 levels: ~545k
vs ~300k ops/s for the same commit).

Reading a result: `primaryMetric.score` is throughput (ops/s) and `scoreError` is the
half-width of the confidence interval — if two runs' `score ± scoreError` intervals
overlap, the difference is not statistically significant. Results from `-prof gc`
(`gc.count`, `gc.time`, and `gc.alloc.rate.norm` = bytes allocated per operation where
the running JDK build exposes per-thread allocation counters) are the memory evidence.

`./gradlew jmhSmoke` is the CI regression gate: short rounds (`-wi 2 -i 3 -r 1s -w 1s`)
and two forks, good for catching large regressions, too noisy for capacity planning.
`./gradlew jmh` is the fuller run (3 warmup + 5 measurement iterations, 2 forks); pass
`-PjmhArgs="..."` for extra JMH flags. Treat both as "is this commit faster or slower
than the last one on this host?", never as a hardware specification.

## CI

`.gitlab-ci.yml` runs `test` (JUnit + JaCoCo report, 90% line-coverage gate)
and a JMH smoke benchmark on every push; a full benchmark run is a manual job.