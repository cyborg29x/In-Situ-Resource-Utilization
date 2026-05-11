# ProcurementMission Tracking Debugging Notes

## Summary

This document contains findings from debugging the mission cargo reservation tracking for `ProcurementMission` type missions in Starsector.

## The Problem

The Mobile Refining Mod was not reserving ore (or other commodities) from procurement missions. The mission tracking worked for other mission types (`DeliveryMissionIntel`, `CheapCommodityMission`) but not for `ProcurementMission`.

## Findings

### 1. Class Name Discovery

The actual class name found in the Intel list is:
```
com.fs.starfarer.api.impl.campaign.missions.ProcurementMission
```

**Not** `ProcurementMissionIntel` - the original prefix was incorrect.

### 2. Reflection Blocked

Reflection is blocked when running from Janino scripts:
```
java.lang.SecurityException: File access and reflection are not allowed to scripts.
```

This means direct field access like `intel.getClass().getDeclaredField("commodityId")` fails.

### 3. Memory Key Locations Investigated

The `ProcurementMission` class stores data via `set()` which writes to memory. Investigation showed:

#### Person Memory Keys Found:
```
[$voice, $requiredForMissions, $requiredForMissions_proCom, $proCom_ref, $missionId]
```

**Missing keys:** `$proCom_commodityId` and `$proCom_quantity` were NOT found on the person's memory.

#### Intel Memory
`BaseIntelPlugin` does NOT have a `getMemory()` method. The memory system is only on people/entities.

### 4. Why It Works for Other Mission Types

- **CheapCommodityMission**: Uses reflection on its own class (works because it's in a compiled jar, not Janino)
- **DeliveryMissionIntel**: Uses public API methods like `getName()` and `getEvent().getQuantity()`
- **ProcurementMissionIntel** (Intel type): Stores data on contact's memory via `$mpm_commodityName` and `$mpm_quantity`

### 5. ProcurementMission Source Code Analysis

From `ProcurementMission.java`:
```java
protected String commodityId;
protected int quantity;
protected MarketAPI deliveryMarket;
protected PersonAPI deliveryContact;
```

The class stores:
- `commodityId` - the commodity ID (e.g., "ore")
- `quantity` - the required quantity
- `deliveryContact` - the person to deliver to (for remote missions)

Data is written via `updateInteractionDataImpl()`:
```java
set("$proCom_commodityId", commodityId);
set("$proCom_quantity", Misc.getWithDGS(quantity));
```

The `set()` method writes to the **person's memory**, not the Intel's memory.

### 6. The Data Isn't Where Expected

The memory keys `$proCom_commodityId` and `$proCom_quantity` are NOT present on:
- The mission giver's memory
- The delivery contact's memory (for remote missions)

This suggests the data is only populated when the player interacts with the mission (accepting, viewing details), not stored persistently.

## Potential Solutions

### Option A: Compiled Plugin
Move the mission tracking logic to a compiled Java plugin instead of Janino script, allowing reflection access.

### Option B: Use Rule Script Memory
The `ProcurementMission` uses rules scripts for dialogs. The `$proCom_ref` memory key references the mission object. If accessible, could query the mission directly.

### Option C: Accept Limitation
Document that `ProcurementMission` tracking is not achievable via the public API without compiled code.

### Option D: Check Delivery Contact Memory
For remote procurement missions, try accessing the `deliveryContact` field via reflection to check their memory. This was attempted but likely also blocked.

## Conclusion

The `ProcurementMission` class (bar mission variant) cannot be tracked using the available public API. The data required (`commodityId`, `quantity`) is stored in protected fields that require reflection to access, which is blocked in script context.

The `ProcurementMissionIntel` class (Intel variant) might work since it stores data on the contact's memory using `$mpm_commodityName` and `$mpm_quantity` keys - but this is a different class.

**Recommendation:** Consider accepting this limitation or implementing a compiled plugin for full ProcurementMission support.
