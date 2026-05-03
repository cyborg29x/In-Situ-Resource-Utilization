# Mission Reserved Commodities

## Problem

Starsector has transport/delivery missions that require the player to deliver a certain quantity of commodities from one market to another. The mobile refining mod converts raw commodities into processed goods, but it has no awareness of these missions and could convert commodities the player needs to deliver.

## How Starsector Handles Transport Missions

### Mission Types (Transport/Delivery)

1. **Delivery Mission** (`DeliveryMission` via rule command)
   - Accepted from bar events
   - Commodities are **added directly to player cargo** when accepted
   - Game checks completion: `cargo.getCommodityQuantity(commodityId) >= requiredQuantity`
   - On delivery: `cargo.removeItems(CargoItemType.RESOURCES, commodityId, quantity)`
   - Note: No public `DeliveryMissionIntel` class exists; uses `BaseCommandPlugin` internally

2. **Procurement Mission** (`ProcurementMissionIntel`)
   - Player must acquire commodities themselves
   - Same completion check: `cargo.getCommodityQuantity(commodityId) >= quantity`
   - `quantity` is protected - requires reflection to access

3. **Cheap Commodity Mission** (`CheapCommodityMission`)
   - Player buys at discount from source market, then delivers
   - Same pattern as above
   - `commodityId` and `quantity` are protected - requires reflection

### Non-Transport Mission Types (Excluded)

- **Commodity Production Mission** (`CommodityProductionMission`)
  - Requires player to produce commodities at their colony via industry
  - Does not affect player cargo - irrelevant for this mod

### Key Finding: No Built-in Reservation System

Starsector does **NOT** have a separate "reserved" flag or tracking system for mission commodities. Mission cargo is indistinguishable from regular cargo in the player's hold. The game simply checks total quantity when the player reaches the destination.

## API Reference

### DeliveryMission (Rule Command)

The Delivery Mission does not use a public Intel class. It uses `DeliveryMission` (a `BaseCommandPlugin`) invoked via rule commands. Detection can be done via:
- Checking mission memory flags
- Searching IntelManager for missions with specific memory keys

```java
// Check intel list for delivery missions (alternative approach)
for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel()) {
    // Check if intel has delivery-related tags or memory
    // Use reflection or memory API to detect delivery missions
}
```

### ProcurementMissionIntel

```java
// Get commodity (returns CommodityOnMarketAPI)
String commodityId = intel.getCommodity().getId();

// Get quantity - protected field, requires reflection
java.lang.reflect.Field field = ProcurementMissionIntel.class.getDeclaredField("quantity");
field.setAccessible(true);
float quantity = (Float) field.get(intel);
```

### CheapCommodityMission

```java
// Get commodity ID - protected field, requires reflection
java.lang.reflect.Field field = CheapCommodityMission.class.getDeclaredField("commodityId");
field.setAccessible(true);
String commodityId = (String) field.get(intel);

// Get quantity - protected field, requires reflection
field = CheapCommodityMission.class.getDeclaredField("quantity");
field.setAccessible(true);
int quantity = (Integer) field.get(intel);
```

### IntelManager

```java
// Get all active intel
List<IntelInfoPlugin> intelList = Global.getSector().getIntelManager().getIntel();

// Filter for mission types (note: no instanceof for Delivery - use alternative)
for (IntelInfoPlugin intel : intelList) {
    if (intel instanceof ProcurementMissionIntel) { ... }
    // For Delivery: check memory flags or mission name
    // For CheapCommodityMission: class name check via getClass().getSimpleName()
}
```

## Implementation Strategy

### MissionCargoTracker Utility

Create a utility class that:

1. Iterates through `Global.getSector().getIntelManager().getIntel()`
2. Filters for `ProcurementMissionIntel` and `CheapCommodityMission` instances
   - Note: No public class for Delivery mission; filter via memory flags or mission name
3. Checks `isAccepted()` and excludes completed/failed/abandoned/cancelled missions
4. Uses reflection to extract `quantity` from each mission
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
4. **ProcurementMission/CheapCommodityMission quantity field**: Protected, requires reflection
5. **Delivery mission detection**: No public Intel class - requires alternative detection (memory flags, mission names)
6. **CommodityProductionMission**: Excluded - uses production, not player cargo
