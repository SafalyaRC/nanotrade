# ⚡ nanotrade

[![Java](https://img.shields.io/badge/Java-8-orange?logo=java&logoColor=white)](https://java.com/)
[![Maven](https://img.shields.io/badge/Maven-3.9-red?logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue)](LICENSE)
[![Build](https://img.shields.io/badge/Build-Passing-brightgreen)]()

> A production-grade stock market order matching engine — pure Java 8, zero frameworks, zero infrastructure. The same core logic that powers Nasdaq, NYSE, and Binance, built from scratch.

Real exchanges match billions of orders daily using exactly this architecture. Nanotrade implements it completely — greedy limit-order matching, FIFO fairness, OHLCV market data, circuit breakers, session persistence, and a full audit trail — all in a terminal you can run with one command.

---

## 🚀 Features

⚡ **Greedy Order Matching** — Price-time priority matching using `TreeMap<Double, Queue<Order>>`. O(log n) insertion, O(1) best-price lookup. Handles partial fills, full fills, and FIFO fairness at the same price level automatically.

📊 **Live Order Book** — Bids sorted descending, asks sorted ascending. Spread detection, real-time depth display. Multi-symbol support — trade AAPL, GOOGL, MSFT, TSLA simultaneously.

💼 **Portfolio Tracking** — Per-trader holdings, average cost basis, realized P&L, cash spent on buys, cash received from sales. Updates on every match execution.

📈 **Market Data & OHLCV** — Open, High, Low, Close, Volume, VWAP, trade count, and price change percentage per symbol. Includes a mini price history with direction arrows for the last 10 trades.

🔴 **Circuit Breakers** — Auto-halts trading on a symbol if price moves more than 5% within a 60-second window. 30-second cooldown with auto-resume. Manual override via `RESUME_MARKET`. Logs every halt and resume to the audit trail.

💾 **Session Persistence** — Full state serialized to disk after every mutating operation. Restart the engine and your order books, portfolios, market data, and circuit breaker state are all exactly where you left them.

📋 **Event Audit Log** — Every order, match, cancellation, halt, resume, and session start/end written to `data/nanotrade.log` with millisecond timestamps. Append-only across sessions.

🔥 **Stress Testing** — Built-in benchmark generates N random orders across 5 symbols and 5 traders. 10,000 orders processed in ~66ms. 100,000 orders at 380,000+ orders/sec.

---

## 💡 Why This Project?

Most portfolio projects are CRUD apps or LeetCode solutions. This is neither.

Order matching engines are among the most algorithmically demanding systems in production software. Every design decision here has a reason rooted in performance and correctness:

- **Why `TreeMap` over `HashMap`?** — Orders must be iterated in price order during matching. TreeMap gives O(log n) sorted insertion and O(1) first/last key access. A HashMap can't do this.
- **Why `Queue` at each price level?** — Multiple orders can rest at the same price. A Queue enforces FIFO — the first order placed at $150.00 gets filled before the second. This is a regulatory requirement on real exchanges.
- **Why `HashMap<Long, Order>` separately?** — Cancellations need O(1) lookup by order ID. Scanning the TreeMap would be O(n). The HashMap gives instant access at the cost of a small amount of memory.
- **Why greedy matching?** — Always taking the best available match immediately, without waiting for potentially better future matches, is provably optimal for limit-order books. Proof: any deferred match is strictly worse for at least one party.

---

## 🧠 Core Algorithm
```
matchBuyOrder(buyOrder):
  for askPrice in asks.keySet() (ascending):
    if askPrice > buyOrder.limitPrice: break
    sellers = asks.get(askPrice)
    while buyOrder.remaining > 0 and sellers not empty:
      qty = min(buyOrder.remaining, seller.remaining)
      execute match at buyOrder.limitPrice
      update both sides, remove filled orders
  return matches

matchSellOrder(sellOrder):
  for bidPrice in bids.descendingKeySet():
    if bidPrice < sellOrder.limitPrice: break
    buyers = bids.get(bidPrice)
    while sellOrder.remaining > 0 and buyers not empty:
      qty = min(sellOrder.remaining, buyer.remaining)
      execute match at sellOrder.limitPrice
      update both sides, remove filled orders
  return matches
```

**Complexity:**

| Operation | nanotrade | Naïve ArrayList | Why It Matters |
|-----------|-----------|-----------------|----------------|
| Add Order | O(log n) | O(n) | 100× faster at 1M orders |
| Match Order | O(log n × m) | O(n²) | Critical at exchange scale |
| Cancel Order | O(log n) | O(n) | 1000× faster at scale |
| Best Price Lookup | O(1) | O(n) | Instant always |

---

## 📦 Project Structure
```
nanotrade/
├── src/main/java/com/nanotrade/
│   ├── model/
│   │   ├── Order.java          # Core order object, fill/cancel logic
│   │   ├── OrderType.java      # BUY | SELL
│   │   ├── OrderStatus.java    # PENDING | PARTIALLY_FILLED | FILLED | CANCELLED
│   │   └── Match.java          # Executed trade between two orders
│   ├── engine/
│   │   ├── OrderBook.java      # TreeMap bid/ask books + greedy matching
│   │   ├── MatchingEngine.java # Orchestrator — wires all subsystems
│   │   ├── PerformanceMonitor  # Live session throughput and latency
│   │   ├── StressTest.java     # Benchmark generator
│   │   └── StressTestResult    # Result carrier
│   ├── portfolio/
│   │   └── Portfolio.java      # Per-trader holdings, cost basis, P&L
│   ├── market/
│   │   ├── MarketData.java     # OHLCV, VWAP, price history per symbol
│   │   ├── MarketDataStore     # Symbol → MarketData map
│   │   ├── CircuitBreaker.java # Per-symbol halt logic
│   │   └── CircuitBreakerManager.java
│   ├── persistence/
│   │   └── PersistenceManager  # Serialization to disk
│   ├── logging/
│   │   └── EventLogger.java    # Append-only audit log
│   ├── cli/
│   │   ├── CommandParser.java  # Interactive terminal interface
│   │   └── TablePrinter.java   # Fixed-width box drawing utility
│   └── Main.java
├── data/                       # Auto-created — persisted state + logs
│   ├── orderbooks.dat
│   ├── portfolios.dat
│   ├── marketdata.dat
│   ├── circuitbreakers.dat
│   └── nanotrade.log
└── pom.xml
```

---

## ⚡ Getting Started

**Prerequisites:** Java 8+, Maven 3.x
```bash
# Clone
git clone https://github.com/YOUR_USERNAME/nanotrade.git
cd nanotrade

# Build and run
mvn compile
mvn exec:java
```

**Windows Terminal recommended** for correct box-drawing character rendering.

---

## 🖥️ Commands

| Command | Description |
|---------|-------------|
| `ADD_ORDER <BUY\|SELL> <qty> <symbol> <price> <traderId>` | Place a limit order |
| `CANCEL_ORDER <orderId> <symbol>` | Cancel a resting order |
| `SHOW_BOOK <symbol>` | Display live order book |
| `SHOW_ORDER <orderId> <symbol>` | Order status and details |
| `SHOW_PORTFOLIO <traderId>` | Holdings, avg cost, P&L |
| `SHOW_TRADE_HISTORY <symbol>` | All executed trades |
| `SHOW_MARKET_DATA <symbol\|ALL>` | OHLCV, VWAP, price history |
| `SHOW_CIRCUIT <symbol\|ALL>` | Circuit breaker status |
| `SHOW_PERFORMANCE` | Live session throughput and latency |
| `SHOW_LOG [n]` | Last n audit log entries (default 20) |
| `RESUME_MARKET <symbol>` | Manually resume a halted symbol |
| `STRESS_TEST <n>` | Benchmark with n random orders |
| `CLEAR_MARKET <symbol>` | Reset a symbol's order book |
| `CLEAR_DATA` | Wipe all persisted state |
| `EXIT` | Save state and shut down |

---

## 🔥 Stress Test Results

Tested on a standard laptop (Intel i5, 16GB RAM):
```
Orders Generated  : 100,000
Matches Found     : 70,740
Match Rate        : 70.7%
Time Elapsed      : 262 ms
Throughput        : 381,679 orders/sec
Avg per Order     : 0.0026 ms
Avg Match Latency : 0.0055 ms
Min Match Latency : 0.0007 ms
Max Match Latency : 1.0598 ms
```

---

## 🛡️ Circuit Breaker

Automatically halts trading on a symbol when price moves more than **5%** within a **60-second window**. Modelled after real exchange circuit breakers (NYSE Rule 48, Nasdaq's LULD mechanism).
```
Trigger : |current_price - reference_price| / reference_price > 5%
Window  : 60 seconds (resets on expiry)
Cooldown: 30 seconds auto-resume
Override: RESUME_MARKET <symbol>
```

Every halt and resume is logged to the audit trail with timestamp and reason.

---

## 📋 Audit Log Sample
```
[2026-03-15 17:21:30.001] SYSTEM     SESSION_START        nanotrade engine started
[2026-03-15 17:21:34.210] ORDER      ADD                  #1 BUY 100 AAPL @ $150.00 trader=alice
[2026-03-15 17:21:38.442] ORDER      ADD                  #2 SELL 100 AAPL @ $149.00 trader=bob
[2026-03-15 17:21:38.451] MATCH      EXECUTE              buy=#1 sell=#2 qty=100 price=$149.00 symbol=AAPL
[2026-03-15 17:21:44.823] ORDER      ADD                  #3 BUY 100 AAPL @ $158.00 trader=alice
[2026-03-15 17:21:49.104] MATCH      EXECUTE              buy=#3 sell=#4 qty=100 price=$157.00 symbol=AAPL
[2026-03-15 17:21:49.106] CIRCUIT    HALT                 symbol=AAPL reason=price moved 5.37%
[2026-03-15 17:22:19.107] CIRCUIT    RESUME               symbol=AAPL
[2026-03-15 17:22:45.300] SYSTEM     SESSION_END          nanotrade engine stopped
```

---

## 🛠️ Tech Stack

| Layer | Tech |
|-------|------|
| Language | Java 8 |
| Build | Apache Maven 3.9 |
| Data Structures | TreeMap, HashMap, LinkedList, ArrayList |
| Persistence | Java Object Serialization |
| Logging | Custom append-only BufferedWriter |
| Interface | Interactive terminal CLI |
| Testing | JUnit 4 (structure in place) |

Zero external runtime dependencies. No Spring, no Hibernate, no databases, no message queues. Everything is hand-rolled.

---

## 🗺️ What Could Come Next

- **Market Orders** — Execute immediately at best available price
- **IOC / FOK / GTD** — Industry-standard Time-In-Force order types
- **Concurrency** — Per-symbol `ReentrantLock` for thread-safe parallel order submission
- **WebSocket API** — Real-time order submission and match notifications over the wire
- **JUnit Test Suite** — Edge case coverage for partial fills, FIFO ordering, empty book scenarios
- **Self-Match Prevention** — Reject orders that would match the same trader on both sides
- **FIX Protocol** — Parse Financial Information eXchange messages, the actual industry standard

---

*Built in pure Java 8. No shortcuts.*
