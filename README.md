# Assignment 4 – Facade over a three-stage chain: E-Commerce Fulfillment (Topic 5)

A customer order goes through three independent subsystems, coordinated only by a facade:

| Step | Subsystem | Package | What it does |
|------|-----------|---------|--------------|
| A | Inventory Manager + Order Validator | `fulfillment.inventory` | validates the order line and **reserves stock** |
| B | Warehouse Picker + Packing Station | `fulfillment.warehouse` | **picks** the items from the shelf and **packs** them into a box |
| C | Shipping Carrier API + Payment Gateway | `fulfillment.shipping` | **charges the card** and **hands the parcel to the carrier** |

Every subsystem stores its own data in CSV files in the `data/` folder, so everything survives a restart.

## 1. Setup and commands

Requirements: **JDK 17 or newer** and **Maven 3.8+**. No other installation is needed (persistence uses plain CSV files via `java.nio`).

| Purpose | Command |
|---------|---------|
| Build **and** run the demo (single command) | `mvn compile exec:java` |
| Alternative: build a jar, then run it | `mvn package` then `java -jar target/ecommerce-fulfillment.jar` |
| Run the unit tests | `mvn test` |
| Tests + coverage report + 80 % coverage check | `mvn verify` |
| Coverage report location | `target/site/jacoco/index.html` |
| **Reset the persisted store** (clean start) | stop the app, then `rm -rf data` (Windows: `rmdir /s /q data`) |

On the next start the application re-creates `data/` with the default products and test cards.
A different data folder can be given as an argument: `java -jar target/ecommerce-fulfillment.jar demo-data`.

## 2. Live demo script

Default data: products `LAPTOP, PHONE, HEADPHONES, KEYBOARD, MOUSE`; cities served by the carrier `ASTANA, ALMATY, SHYMKENT, KARAGANDA`; cards `CARD-OK` (2 000 000 KZT), `CARD-LOW` (20 000 KZT), `CARD-EMPTY` (0 KZT).

| Scenario | How to trigger it in the menu | Expected persisted end state |
|----------|-------------------------------|------------------------------|
| Happy path A → B → C | `1`, MOUSE, 2, ALMATY, CARD-OK | order `COMPLETED`, reservation `SOLD`, task `PACKED`, shipment `SHIPPED`, stock −2, card charged |
| **B fails after A committed** | `6` block shelf bin of PHONE, then `1`, PHONE, 1, ASTANA, CARD-OK | order `COMPENSATED`, reservation `RELEASED`, stock restored, no pick task |
| **C fails after A and B committed** (payment declined) | `1`, HEADPHONES, 2, ALMATY, **CARD-EMPTY** | output shows undo of **B, then A**; task `CANCELLED`, reservation `RELEASED`, stock restored |
| C fails (carrier does not deliver) | `1`, MOUSE, 1, **MOON**, CARD-OK | same as above |
| Extra failure: rejected before anything is committed | `1`, LAPTOP, **50**, … (max 10) or a quantity larger than the stock, or an unknown product | order `REJECTED`, nothing changed in A, B, C |
| Crash in the middle of the chain + restart | `8` (app exits after step A), start the app again → warning lists the interrupted order → `9` resume it **or** `10` compensate it | `COMPLETED` (resume) or `COMPENSATED` (manual compensation) |

Use `2`, `3`, `4` (inspect one order in A, B and C) and `5` at any time to show what is stored on disk. You can also open the CSV files in `data/` directly.

## 3. Architecture

UML class diagram: [`docs/uml-class-diagram.svg`](docs/uml-class-diagram.svg) (PNG: `docs/uml-class-diagram.png`, Graphviz source: `docs/uml-class-diagram.dot`).

| Package | Classes |
|---------|---------|
| `inventory` (A) | `InventoryManager` (service), `OrderValidator` (pure business logic), `InventoryStore` (persistence gateway), `StockReservation` (entity: `RESERVED → SOLD`, `RESERVED → RELEASED`), `InventoryException` |
| `warehouse` (B) | `WarehousePicker` (service), `PackingStation` (pure business logic: box size), `WarehouseStore` (persistence gateway), `PickTask` (entity: `CREATED → PICKED → PACKED`, any → `CANCELLED`), `WarehouseException` |
| `shipping` (C) | `ShippingCarrierApi` (service), `PaymentGateway` (pure business logic: approve/decline), `ShippingStore` (persistence gateway), `Shipment` (entity: `CREATED → PAID → SHIPPED`, any → `CANCELLED`), `ShippingException` |
| `facade` | `OrderFulfillmentFacade` (customer workflow), `FulfillmentAdminFacade` (inspection, shelf blocking, manual compensation), `Compensator` (shared rollback rules), `WorkflowStore` + `OrderRecord` (persisted chain progress), `OrderRequest`, `OrderRejectedException`, `OrderFailedException` |
| `app` | `Main` – console client, talks only to the two facades |

Independence: no class in `inventory`, `warehouse` or `shipping` imports anything from another subsystem (check with `grep -r "import fulfillment" src/main/java/fulfillment/{inventory,warehouse,shipping}` – it prints nothing). Each entity checks its own legal status transitions and throws `IllegalStateException` otherwise. Each subsystem can be used alone – see `InventoryManagerTest`, `WarehousePickerTest`, `ShippingCarrierApiTest`, which use the subsystems without any facade.

### Persisted workflow status (`data/workflow_orders.csv`)

| Status | Meaning | What happens on `resume` | What compensation undoes |
|--------|---------|--------------------------|--------------------------|
| `PENDING_B` | A committed | run B, then C | A |
| `PENDING_C` | A and B committed | run C | B, then A (and C first if a crash happened right after C) |
| `COMPLETED` | all done | nothing | not allowed |
| `COMPENSATED` | undone after a failure | nothing | not allowed |
| `REJECTED` | A refused the order | nothing | not allowed |

`placeOrder()` = `startOrder()` (step A, saves `PENDING_B`) + `resume()`. `resume()` reads the status **from disk** and continues from there. When B or C fails, `Compensator` again reads the order **from disk** and undoes in reverse order. All subsystem operations are safe to repeat (reserving, packing or shipping the same order twice does nothing the second time), so resuming after a crash never reserves, packs or charges twice.

## 4. Principle of Least Knowledge – before / after

**Before (no facade):** the client must know six classes of three subsystems, their order, their exceptions and the rollback rules.

```java
InventoryManager inventory = new InventoryManager(dataDir);
WarehousePicker picker = new WarehousePicker(dataDir);
ShippingCarrierApi carrier = new ShippingCarrierApi(dataDir);

StockReservation reservation = inventory.reserve(orderId, "MOUSE", 2);
try {
    picker.pickAndPack(orderId, "MOUSE", 2);
} catch (WarehouseException e) {
    inventory.release(orderId);                       // client must know how to undo A
    throw e;
}
try {
    carrier.ship(orderId, "CARD-OK", "ALMATY", reservation.getTotalPrice(), 2);
} catch (ShippingException e) {
    picker.cancelTask(orderId);                       // ... and B, in the right order
    inventory.release(orderId);
    throw e;
}
inventory.markSold(orderId);
// and the client still has no persisted progress, so a crash in the middle is lost
```

**After (with the facade):** the client has one immediate friend.

```java
OrderFulfillmentFacade orders = new OrderFulfillmentFacade(dataDir);
OrderRecord order = orders.placeOrder(new OrderRequest("MOUSE", 2, "ALMATY", "CARD-OK"));
```

`Main` imports only classes from `fulfillment.facade`.

## 5. Tests

* **Framework:** JUnit 5. **Coverage tool:** JaCoCo (`jacoco-maven-plugin`), the standard coverage tool for Maven/Java; `mvn verify` fails if line coverage of the subsystems and facades is below 80 %. The console class `fulfillment.app.Main` is excluded from measurement because it is only the user interface and is exercised in the live demo.
* **F.I.R.S.T.:** every test uses a new JUnit `@TempDir`, so each test starts with a clean store, tests do not share files and can run in any order; no sleeps or network, so they are fast and repeatable; every test uses assertions.
* **Domain-specific test language:** `src/test/java/fulfillment/facade/ShopTestKit.java`, e.g.
  ```java
  shop.withStock("MOUSE", 8000, 10).withBlockedShelfFor("MOUSE");
  shop.customerOrders(2, "MOUSE").shippedTo("ALMATY").paidWith("CARD-T");
  shop.assertStockOf("MOUSE", 10);
  ```
* **Compensation depth 1:** `OrderFulfillmentFacadeTest.whenPickingFailsOnlyTheStockReservationIsUndone`
* **Compensation depth 2:** `OrderFulfillmentFacadeTest.whenPaymentIsDeclinedPackingIsUndoneFirstAndThenTheReservation`
* **Persistence reload (restart mid-chain):** `afterARestartBehindStepATheOrderIsResumedFromDisk`, `afterARestartBehindStepBOnlyStepCIsRun`, `afterARestartBehindStepBAFailingStepCStillUndoesBThenA`

## 6. Reflection: why Facade and not Adapter

An Adapter exists to make **one** existing class usable through an interface that a client already expects – it converts an interface, and the client does exactly the same thing as before, just through different method names. Nothing in this project has that problem: `InventoryManager`, `WarehousePicker` and `ShippingCarrierApi` do not need to look like some other interface, and no client is waiting for a particular signature.

Our problem is different: one business operation – "fulfil this order" – needs **three unrelated subsystems** in a fixed order (reserve → pick and pack → charge and ship), and when B or C fails, the already committed steps must be undone in reverse order based on what is stored on disk. The intent of `OrderFulfillmentFacade` is to **simplify and hide this coordination**: the client calls `placeOrder()` and never learns that a pick task, a box size, a card balance or a reservation exist. Wrapping each subsystem in its own adapter would still leave the client responsible for ordering the calls, persisting the progress and compensating – the hard part would stay in the client. The facade also adds behaviour of its own (sequencing, status tracking, rollback decisions), which is not what an adapter is for. So the intent – a simpler, higher-level entry point to a group of subsystems, following the Principle of Least Knowledge – is exactly the intent of Facade.
