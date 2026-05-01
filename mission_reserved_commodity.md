# Mission Reserved Commodities

## Problem

Starsector has transport/delivery missions that require the player to deliver a certain quantity of commodities from one market to another. The mobile refining mod converts raw commodities into processed goods, but it has no awareness of these missions and could convert commodities the player needs to deliver.

## How Starsector Handles Transport Missions

### Mission Types

1. **Delivery Mission** (`DeliveryMissionIntel`)
   - Accepted from bar events
   - Commodities are **added directly to player cargo** when accepted
   - Game checks completion: `cargo.getCommodityQuantity(commodityId) >= requiredQuantity`
   - On delivery: `cargo.removeItems(CargoItemType.RESOURCES, commodityId, quantity)`

2. **Procurement Mission** (`ProcurementMissionIntel`)
   - Player must acquire commodities themselves
   - Same completion check: `cargo.getCommodityQuantity(commodityId) >= quantity`

3. **Cheap Commodity Mission** (`CheapCommodityMission`)
   - Player buys at discount from source market, then delivers
   - Same pattern as above

### Key Finding: No Built-in Reservation System

Starsector does **NOT** have a separate "reserved" flag or tracking system for mission commodities. Mission cargo is indistinguishable from regular cargo in the player's hold. The game simply checks total quantity when the player reaches the destination.

## API Reference

### DeliveryMissionIntel

```java
// Check if mission is active
intel.isAccepted() && !intel.isCompleted() && !intel.isFailed() && !intel.isAbandoned() && !intel.isCancelled()

// Get mission details
String commodityId = intel.getEvent().getCommodityId();
float quantity = intel.getEvent().getQuantity();
MarketAPI destination = intel.getDestination();
```

### ProcurementMissionIntel

```java
// Get commodity (returns CommodityOnMarketAPI)
String commodityId = intel.getCommodity().getId();

// Get quantity (private field, requires reflection)
java.lang.reflect.Field field = ProcurementMissionIntel.class.getDeclaredField("quantity");
field.setAccessible(true);
int quantity = (Integer) field.get(intel);
```

### IntelManager

```java
// Get all active intel
List<IntelInfoPlugin> intelList = Global.getSector().getIntelManager().getIntel();

// Filter for mission types
if (intel instanceof DeliveryMissionIntel) { ... }
if (intel instanceof ProcurementMissionIntel) { ... }
```

## Implementation Strategy

### MissionCargoTracker Utility

Create a utility class that:

1. Iterates through `Global.getSector().getIntelManager().getIntel()`
2. Filters for `DeliveryMissionIntel` and `ProcurementMissionIntel` instances
3. Checks `isAccepted()` and excludes completed/failed/abandoned/cancelled missions
4. Extracts commodity ID and quantity from each active mission
5. Returns a `Map<String, Float>` of `commodityId -> totalReservedQuantity`

### Integration Points in MobileRefiningAbility

Replace all `cargo.getCommodityQuantity()` calls with `MissionCargoTracker.getAvailableQuantity()`:

- **Volatiles** (lines 58, 146)
- **Metals** (lines 82, 159)
- **Rare metals / transplutonics** (line 83)
- **Ore** (in `processResource()`)
- **Rare ore** (in `processResource()`)
- **Organics** (in `processResource()`)

### Tooltip Updates

Show reserved amounts in the ability tooltip so players understand why less is being processed:

```
Available volatiles: 500
(Reserved for missions: 200)
```

## Commodities Affected

The mod processes these commodities, all of which can appear in transport missions:

| Commodity ID | Display Name |
|---|---|
| `volatiles` | Volatiles |
| `metals` | Metals |
| `rare_metals` | Transplutonics |
| `ore` | Ore |
| `rare_ore` | Transplutonic Ore |
| `organics` | Organics |

## Edge Cases

1. **Multiple missions for same commodity**: Quantities are summed
2. **Mission completed/failed/abandoned**: Automatically excluded from reservation
3. **Player has less than reserved**: `getAvailableQuantity()` returns 0 (never negative)
4. **ProcurementMission quantity field**: Private, requires reflection to access
