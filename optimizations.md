# Optimization Opportunities

This document outlines potential performance optimizations identified but not yet implemented.

## 1. Unoptimized Cargo Operations

**Location:** `MobileRefiningAbility.java:54-67`

**Issue:** The while loops process cargo one unit at a time, which is called every game tick (~60/sec). This creates excessive method call overhead.

**Current Code:**
```java
while (oreFraction >= 1f && availableOre >= 1f) {
    cargo.removeCommodity("ore", 1f);
    metalFraction += 1f / ORE_TO_METAL_RATIO;
    oreFraction -= 1f;
    availableOre -= 1f;
}

while (metalFraction >= 1f && availableSpace >= 1f) {
    cargo.addCommodity("metals", 1f);
    metalFraction -= 1f;
    availableSpace -= 1f;
}
```

**Recommended Fix:** Calculate the maximum batch amount and process once:
```java
float maxOreToProcess = Math.min(oreFraction, availableOre);
if (maxOreToProcess > 0) {
    cargo.removeCommodity("ore", maxOreToProcess);
    metalFraction += maxOreToProcess / ORE_TO_METAL_RATIO;
    oreFraction -= maxOreToProcess;
    // ...
}
```

---

## 2. Repeated Fleet Member Iteration

**Location:** `MobileRefiningAbility.java:84-102` and `127-133`

**Issue:** 
- `getTotalRefiningCapacity()` iterates all ships every tick
- `canActivate()` iterates all ships separately  
- Both call `getFleetData().getMembersListCopy()` creating new list copies each time

Ship hullmod assignments don't change frequently during gameplay, so this can be cached.

**Recommended Fix:** Add caching with invalidation on fleet changes:
- Use a cached value that's invalidated when ships are added/removed from the fleet
- Or cache during the first call and refresh only when needed

---

## Status

- **Optimization 1:** Not implemented
- **Optimization 2:** Not implemented

## Other Notes

The JSON configuration loading was successfully implemented to load settings from `data/config/settings.json`.