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

### 2. Two Mission Types Exist

There are TWO different procurement mission types:

1. **`ProcurementMission`** (bar mission variant) - obtained from bar NPCs
2. **`ProcurementMissionIntel`** (Intel variant) - obtained from Intel panel

The Intel variant (`ProcurementMissionIntel`) works correctly because it stores data on the contact's memory using `$mpm_commodityName` and `$mpm_quantity` keys, which are accessible via public API.

### 3. Reflection Blocked Even in Compiled Plugin

Despite being a compiled Java plugin (not Janino), reflection is blocked:
```
java.lang.SecurityException: File access and reflection are not allowed to scripts.
```

This occurs when trying to access fields on:
- The ProcurementMission object from `$proCom_ref`
- The `deliveryContact` field from the intel object itself
- Any field on the ProcurementMission class

The SecurityException suggests the ProcurementMission object itself is treated as a "script" context by the game's security system.

### 4. Memory Key Analysis

#### Person Memory Keys Found:
```
[$voice, $requiredForMissions, $requiredForMissions_proCom, $proCom_ref, $missionId]
```

**Missing keys:** `$proCom_commodityId` and `$proCom_quantity` were NOT found.

#### $proCom_ref Contains:
- The actual ProcurementMission object (same as the intel object)
- Reflection on this object is blocked by SecurityException

#### $requiredForMissions_proCom Contains:
- Just a boolean (`true`) - not useful for commodity data

### 5. Rules.csv Analysis

The game's `rules.csv` reveals how the data flows:

```csv
proComBlurb,proCom_blurb,,,"""I need to procure a quantity of $proCom_commodityName."""
proComOfferBeginBar,DialogOptionSelected,$option == proCom_startBar,"$missionId = proCom
```

Key memory variables used in rules:
- `$proCom_commodityName` - display name (e.g., "Ore")
- `$proCom_quantity` - quantity needed
- `$proCom_commodityId` - commodity ID (e.g., "ore")
- `$proCom_pricePerUnit` - price per unit
- `$proCom_totalPrice` - total price
- `$proCom_marketName` - delivery market name
- `$proCom_ref` - reference to ProcurementMission object

**Critical Finding:** These memory keys are set by the rule script when the player **interacts with the mission** (starts the dialog by selecting "Talk to the person"). They are NOT populated when the mission is simply created.

The data flow is:
1. Mission is created → memory keys are NOT populated
2. Player interacts with mission (starts dialog) → rule script populates memory keys
3. Mission continues → memory keys remain available while player is in dialog context

This explains why `$proCom_commodityId` and `$proCom_quantity` are null - the player hasn't interacted with this mission yet.

### 6. Field Listing Attempt

Attempted to list all declared fields on ProcurementMission via reflection - also blocked by SecurityException.

### 7. Stage Investigation

The ProcurementMission class has a `Stage` enum, but it doesn't contain commodity/quantity information.

### 8. Public API Methods Tested (NEW)

Tested various public API methods on ProcurementMission:

| Method | Returns | Contains Quantity? |
|--------|---------|-------------------|
| `getSmallDescriptionTitle()` | "Ore Procurement" | No - commodity type only |
| `getName()` | "Procurement - Ore" | No - commodity type only |
| `getBaseName()` | "Ore Procurement" | No - commodity type only |
| `getIntelTags()` | [Important, Missions, Accepted, hegemony] | No |

**Commodity type CAN be extracted** from these methods by parsing the string:
- "Ore Procurement" → strip " Procurement" → "Ore" → map to "ore" using DISPLAY_NAME_TO_ID

### 9. Protected Fields with No Public Getters (NEW)

Decompiled ProcurementMission class reveals:

```java
protected String commodityId;  // e.g., "ore"
protected int quantity;          // e.g., 1000
```

These fields are **protected** with **no public getter methods**. The class has:
- `getBaseName()` - returns "Ore Procurement" (commodity type only)
- `getSpec()` - returns CommoditySpecAPI but is **protected**
- No `getQuantity()` method exists

### 10. Why Memory Keys Never Appear (NEW)

Even after player interacts with the mission, the memory keys don't appear on the person's memory. This is because:

In BaseHubMission, the `set()` method writes to **transient** `interactionMemory`:
```java
public void set(String key, Object value) {
    this.interactionMemory.set(key, value, 0.0F);  // transient field!
}
```

The `interactionMemory` is only populated during dialog interaction via `updateInteractionData()`, and it's **not persisted** to the person's memory.

### 11. Comparison with DeliveryMissionIntel (NEW)

**Why DeliveryMissionIntel works:**
```java
String name = deliveryIntel.getName();  // "Delivery - Ore"
int quantity = deliveryIntel.getEvent().getQuantity();  // Public method chain!
```

**Why ProcurementMission doesn't:**
- `ProcurementMission` has protected fields with no public getters
- `ProcurementMissionIntel` has `getQuantity()` but it's **protected** (not public)

| Mission Type | Quantity Access | Works? |
|--------------|-----------------|--------|
| DeliveryMissionIntel | `getEvent().getQuantity()` | ✓ Yes |
| ProcurementMissionIntel | `getQuantity()` (protected) | ✗ No |
| ProcurementMission (bar) | No public getter | ✗ No |

### 12. Mock TooltipMakerAPI Approach

Attempted to call `createSmallDescription()` with a mock TooltipMakerAPI to capture rendered text. However:

- TooltipMakerAPI is a complex interface with 100+ methods
- Creating a mock would require implementing many methods
- The text is rendered to UI, not returned as string
- This approach was not fully tested due to complexity

## Current Status

### What Works
- `ProcurementMissionIntel` (Intel variant) - works via `$mpm_commodityName` and `$mpm_quantity` memory keys
- `DeliveryMissionIntel` - works via public API methods (`getEvent().getQuantity()`)
- `CheapCommodityMission` - works via reflection (compiled class)
- `ProcurementMission` (bar) - commodity type can be extracted from `getSmallDescriptionTitle()` or `getName()`

### What Doesn't Work
- `ProcurementMission` (bar variant) - quantity cannot be obtained via public API
- All reflection attempts blocked by SecurityException
- Memory keys never appear on person (transient interactionMemory)
- Protected fields with no public getters

## Solutions Implemented

### Solution 1: Extract Commodity Type (Partial Fix)

Commodity type CAN be extracted from public API:

1. Call `getSmallDescriptionTitle()` → returns "Ore Procurement"
2. Strip " Procurement" suffix → "Ore"
3. Map to commodity ID using existing `DISPLAY_NAME_TO_ID` map → "ore"

This provides partial functionality - the mod can track WHICH commodity is needed, but not the exact quantity.

### Solution 2: Accept Quantity Limitation

For the bar variant `ProcurementMission`, quantity tracking is not possible via public API. Options:
- Document this as a known limitation
- Track only commodity type (not quantity)
- Use mock TooltipMakerAPI approach (complex, untested)

## Conclusion

The `ProcurementMission` class (bar mission variant) cannot be fully tracked using the available public API because:

1. Memory keys `$proCom_commodityId` and `$proCom_quantity` are only set on transient `interactionMemory` during dialog, not persistently stored
2. Reflection is blocked by SecurityException even in compiled plugins
3. The protected fields (`commodityId`, `quantity`) have no public getter methods
4. The data exists in the rule script's memory context, not persistently on the person

**Partial fix implemented:** Commodity type can be extracted from `getSmallDescriptionTitle()` or `getName()`.

**Recommendation:**
- Implement commodity type extraction (works via public API)
- Document quantity as known limitation for bar variant
- Focus on `ProcurementMissionIntel` (Intel variant) which works fully