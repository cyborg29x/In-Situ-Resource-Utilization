# Ore to Supplies Implementation

## Overview

This document describes the implementation of direct ore-to-supplies conversion in the Assembly Line ability, which processes ore into supplies to meet daily supply needs before falling back to metals and transplutonics.

## Processing Order (applyEffect)

1. **Volatiles → Fuel** (lines 77-97): Only in hyperspace, if fuel < 80% capacity

2. **Metals → Supplies** (lines 115-121): Priority conversion, limited by supplyNeed
   - Budget: `Math.min(processingCapacity, supplyNeed / METAL_TO_SUPPLIES_RATIO * METAL_PRICE)`
   - Consumes metals from cargo, produces supplies

3. **Ore → Supplies** (lines 123-134): Direct conversion to cover remaining supply need
   - Calculates `oreToConvert = max(0, supplyNeed - availableMetals) / oreToMetalRatio`
   - Limited to: `Math.min(processingCapacity / 2f, oreToConvert * ORE_PRICE)`
   - Uses `effectiveOreToSuppliesRatio = oreToMetalRatio * METAL_TO_SUPPLIES_RATIO`
   - Direct ore → supplies conversion (not via metals)

4. **Transplutonics → Supplies** (lines 139-146): To cover remaining supply need after metals and ore
   - `remainingNeed = max(0, supplyNeed - metalSuppliesProduced - effectiveOreToSuppliesProduced)`
   - Budget: `Math.min(processingCapacity, remainingNeed / TRANSPLUTONICS_TO_SUPPLIES_RATIO * TRANSPLUTONICS_PRICE)`

5. **Volatiles → Fuel** (lines 150-161): Unlimited, fills remaining fuel space

6. **Ore → Metals** (lines 163-166): Excess ore conversion
   - Processes remaining ore not used in step 3
   - Produces metals for cargo

7. **Rare Ore → Transplutonics** (lines 168-171): Excess transplutonic ore conversion

8. **Metals → Supplies** (lines 173-176): Excess metals conversion
   - Note: This step was moved from earlier in the sequence

9. **Organics → Domestic Goods** (lines 178-180): Excess organics conversion

## Key Changes from Previous Implementation

### applyEffect Changes

1. **Added ore-to-supplies conversion** (new step 3):
   - Calculates `oreToConvert` based on supplyNeed minus availableMetals
   - Uses direct conversion ratio: `oreToMetalRatio * METAL_TO_SUPPLIES_RATIO`
   - Budget limited to `processingCapacity / 2f` (half of remaining capacity)

2. **Updated remainingNeed calculation**:
   - Now accounts for both metals and ore conversions
   - Formula: `max(0, supplyNeed - metalSuppliesProduced - effectiveOreToSuppliesProduced)`

3. **Moved metals-to-supplies step**:
   - Previously ran early in the sequence
   - Now runs at step 8 (after ore→metals and rare_ore→transplutonics)

4. **Added logging** for debugging:
   - START: processingCapacity, supplyNeed, availableMetals, availableOre, usableMetals, oreToConvert
   - metalsBudget, metalsSpent
   - oreBudget, oreSpent, effectiveOreToSuppliesRatio
   - processingCapacity after ore, effectiveOreToSuppliesProduced
   - metalSuppliesProduced, effectiveOreToSuppliesProduced, remainingNeed
   - budgetForTransplutonics, transSpent, processingCapacity after trans

### Tooltip Changes

1. **Added ore-to-supplies tooltip line** (lines 452-462):
   - Shows ore → supplies conversion
   - Uses `oreToConvert` (amount needed to cover supply need)
   - Budget: `Math.min(remainingCapacity / 2f, oreToConvert * ORE_PRICE)`
   - Output: `oreToConvert * effectiveOreToSuppliesRatio`
   - Daily rate: `oreBudget / ORE_PRICE / timeIncrement`

2. **Updated metals-to-supplies tooltip** (lines 465-477):
   - Budget: `Math.min(remainingCapacity, availableMetals * METAL_PRICE * timeIncrement)`
   - Output: `floor(availableMetals * METAL_TO_SUPPLIES_RATIO)` (rounded down)
   - Daily rate: `metalsToProcess` (amount that can be processed given budget)
   - Uses `metalsToProcess = min(availableMetals, metalBudget / METAL_PRICE)`

3. **Updated remaining ore-to-metals tooltip** (lines 517-527):
   - Now shows remaining ore after ore-to-supplies conversion
   - Formula: `max(0, availableOre - oreToConvert)`
   - Budget: `Math.min(remainingCapacity, remainingOre * ORE_PRICE)`

4. **Moved supplyNeed calculation**:
   - Now calculated before ore and metals processing
   - Used for both ore-to-supplies and metals-to-supplies calculations

## Buffer System (Not Yet Implemented)

Planned feature: Add persistent buffers for metals and transplutonics to carry small amounts between frames without using fleet inventory.

- Metal buffer max: `dailySupplyNeed * SUPPLIES_PRICE / METAL_PRICE`
- Transplutonics buffer max: `dailySupplyNeed * SUPPLIES_PRICE / TRANSPLUTONICS_PRICE`

The buffers would be:
1. Filled by ore→metals and rare_ore→transplutonics steps (buffer first, overflow to cargo)
2. Consumed by metals→supplies and transplutonics→supplies steps (buffer first, then cargo)
3. Synced at start of each frame to maintain buffer levels

## Ratios Used

- `ORE_TO_METAL_RATIO = ORE_PRICE / METAL_PRICE = 10/30 = 0.333`
- `METAL_TO_SUPPLIES_RATIO = METAL_PRICE / SUPPLIES_PRICE = 30/100 = 0.3`
- `effectiveOreToSuppliesRatio = ORE_TO_METAL_RATIO * METAL_TO_SUPPLIES_RATIO = 0.333 * 0.3 = 0.1`
- `TRANSPLUTONICS_TO_SUPPLIES_RATIO = TRANSPLUTONICS_PRICE / SUPPLIES_PRICE = 200/100 = 2.0`