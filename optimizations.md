# Optimization Opportunities

This document tracks performance optimizations identified and their implementation status.

## Implemented Optimizations

### 1. Commodity Name Caching
**Status:** ✅ Implemented

**Location:** `MobileRefiningPlugin.java`

**Changes:**
- Added static `COMMODITY_NAME_CACHE` map
- Added `initCommodityNameCache()` called on game load
- Added public `getCommodityName()` static method
- Updated `MobileRefiningAbility.getCommodityName()` to delegate to cached method

**Impact:** 6 commodity spec lookups per tooltip render → 0 (cache hit)

---

### 2. Reserved Commodities Frame Cache
**Status:** ✅ Implemented

**Location:** `MissionCargoTracker.java`

**Changes:**
- Added `cachedResult` and `cachedTimestamp` fields
- Modified `getAllReservedCommodities()` to check if cached timestamp matches current game time
- Cache updates before returning result

**Impact:** Multiple calls per tick (applyEffect + tooltip) now return cached result instead of re-iterating all intel and using reflection

---

### 3. Unified Fleet Data Cache
**Status:** ✅ Implemented

**Location:** `MobileRefiningAbility.java`

**Changes:**
- Added `FleetDataCache` inner class with `processingBudget`, `baseSupplyCost`, `deploymentCost`, `timestamp`
- Added static `cachedFleetData` field
- Added `getFleetData()` method with timestamp-based caching
- Renamed methods to private helpers:
  - `getTotalProcessingBudget()` → `calculateProcessingBudget()`
  - `calculateBaseSupplyConsumption()` → `calculateBaseSupplyCost()`
  - `calculateMilitaryShipDeploymentSupplyCost()` → `calculateDeploymentCost()`
- Updated all call sites to use unified cache

**Impact:** Fleet member iteration (3 methods × all ships) now cached per game tick instead of called separately

---

### 4. Batch Cargo Operations
**Status:** ✅ Implemented (pre-existing)

**Location:** `MobileRefiningAbility.java:152-173`

The `processResource()` method already uses batch processing:
```java
float maxToProcess = Math.min(inputToProcess, available);
if (maxToProcess > 0) {
    cargo.removeCommodity(inputCommodity, maxToProcess);
    float outputProduced = maxToProcess * outputRatio;
    cargo.addCommodity(outputCommodity, outputProduced);
    return maxToProcess * inputPrice;
}
```

---

## Pending Optimizations

### 5. Repeated getAvailableQuantity() Calls

**Location:** `MobileRefiningAbility.java` and `MissionCargoTracker.java`

**Issue:** `MissionCargoTracker.getAvailableQuantity()` is called multiple times per frame:
- `applyEffect()`: 2 calls for volatiles (lines 87, 100)
- `createTooltip()`: 6 calls for different commodities (lines 368-373)
- `processResource()`: called for each resource processed

Each call invokes `cargo.getCommodityQuantity()` which could be cached.

**Proposed Fix:** Cache cargo commodity quantities per frame using game timestamp as cache key:

```java
// In MissionCargoTracker.java
private static Map<String, Float> cachedCargoQuantities = null;
private static long cargoCacheTimestamp = -1;
private static CargoAPI cachedCargoForQuantities = null;

public static Map<String, Float> getCargoQuantities(CargoAPI cargo) {
    long currentTimestamp = Global.getSector().getClock().getTimestamp();
    if (cachedCargoQuantities != null &&
        cachedCargoForQuantities == cargo &&
        cargoCacheTimestamp == currentTimestamp) {
        return cachedCargoQuantities;
    }

    cachedCargoQuantities = new HashMap<>();
    String[] commodities = {"volatiles", "metals", "rare_metals", "ore", "organics", "rare_ore", "supplies", "fuel", "domestic_goods"};
    for (String id : commodities) {
        cachedCargoQuantities.put(id, cargo.getCommodityQuantity(id));
    }

    cachedCargoForQuantities = cargo;
    cargoCacheTimestamp = currentTimestamp;
    return cachedCargoQuantities;
}
```

Then update `getAvailableQuantity()` to use the cached map.

---

### 6. Duplicate sMod Checks in HullMod

**Location:** `MobileRefineryHullMod.java:19-34`

**Issue:** `getCargoCompensationFactor(MutableShipStatsAPI)` and `getProcessingPercent(MutableShipStatsAPI)` both check sMod status separately, duplicating the `getVariant().getSMods().contains()` call.

**Proposed Fix:** Combine into a single method that returns both values, or add a helper method that caches the sMod boolean.

---

### 7. Duplicate calculateFuelSpace() Call

**Location:** `MobileRefiningAbility.java:81, 96`

**Issue:** `calculateFuelSpace(cargo)` is called twice in `applyEffect()`.

**Proposed Fix:** Cache the result in a local variable after first call.

---

### 8. Duplicate Volatiles Calculation

**Location:** `MobileRefiningAbility.java:77-108`

**Issue:** The fuel processing block (lines 77-96) and the later volatiles→fuel block (lines 96-108) have nearly identical calculation logic for available volatiles, budget calculations, etc.

**Proposed Fix:** Consolidate into a single helper method or reuse calculated values.

---

## Cache Strategy Summary

| Cache | Key | Invalidation |
|-------|-----|--------------|
| Commodity names | commodityId | Never (static, game-load init) |
| Reserved commodities | game timestamp | Auto-refresh when game time advances |
| Fleet data | game timestamp | Auto-refresh when game time advances |
| Cargo quantities (pending) | game timestamp + cargo ref | Auto-refresh when game time advances |