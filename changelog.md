# Changelog

All notable changes to this mod will be documented in this file.

## [0.1.15] - 2026-04-30

- Refactored resource processing into reusable processResource() method
  - Extracted ore→metal, transplutonic ore→transplutonics, organics→domestic goods conversion logic
  - Reduces ~40 lines of duplicated code
  - Maintains accurate budget tracking between conversion steps
- Added removeFractionFromCargo() helper method
  - Handles commodity removal with available cargo cap
  - Returns remaining fraction for tracking
- Renamed result variables to full names: oreResult, transplutonicOreResult, organicsResult

## [0.1.14] - 2026-04-30

- Changed fuel cap calculation to use max(80%, fuel cap - 500)
  - Updated maxAllowedFuel to use Math.max(maxFuel * 0.8f, maxFuel - 500f)
- Added military ship deployment supply cost to supply production
  - Created calculateMilitaryShipDeploymentSupplyCost() function
  - Uses !isCivilian() to detect military/combat ships
  - Uses getModifiedValue() to account for hullmod modifiers (e.g., D-mods)
  - Only adds deployment cost not already covered by existing supplies
  - Updated tooltip to show deployment cost separately: "Supply demand: X/day (+ Y deployment)"
- Changed metal/transplutonics supply production to fallback logic
  - Metal used first to produce supplies up to supplyNeed
  - Transplutonics only used as fallback if metal can't cover full supplyNeed
  - Replaced proportional budget split with sequential priority logic
- Added "last 30 volatiles" rule to volatiles-to-fuel conversion
  - Volatiles-to-fuel conversion now reserves last 30 volatiles before processing
  - Prevents depleting all volatiles during long operations

## [0.1.13] - 2026-04-28

- Added volatiles to fuel conversion pathway
  - Runs before all other conversions (supply production, ore→metal, etc.)
  - Only activates when fleet is in hyperspace and actively consuming fuel
  - Stops when fleet fuel reaches 80% of max capacity
  - Uses VOLATILES_TO_FUEL_RATIO = VOLATILES_PRICE / FUEL_PRICE (250/25 = 10)
  - Added transient volatilesFraction and fuelFraction persistent tracking
  - Added addFuelToCargo() helper method for fuel-specific cargo operations
- Updated tooltip to show volatiles/fuel processing rates
  - Added "Max volatiles processed" and "Max fuel output (80% cap)" display lines
  - Only shows fuel stats when fleet is in hyperspace
- Added VOLATILES_PRICE and FUEL_PRICE constants loaded programmatically
- Fixed fuel not being produced
  - Removed minimum 1-unit threshold for processing volatiles
  - Removed minimum 1-unit threshold for adding fuel to cargo
  - Fractions now accumulate until reaching integer threshold
- Fixed fuel production unbounded by budget
  - Volatiles purchased now based on fuel value, not total budget
  - Processing capped by available budget (totalCredits)
- Fixed volatiles not being consumed from cargo
   - Volatiles from cargo now always removed (integer portion)
   - Fraction tracking properly decremented when using purchased volatiles
- Simplified volatiles processing logic
   - Merged volatilesToSpend and volatilesToProcess into single calculation
   - volatilesFraction now only decreases (removed surplus tracking)

## [0.1.12] - 2026-04-27

- Added organics to domestic goods conversion pathway
  - Fourth pathway converts organics to domestic goods using remaining budget
  - Runs after all other conversions (ore→metal, transplutonic ore→transplutonics)
  - Uses ORGANICS_TO_DOMESTIC_GOODS_RATIO = ORGANICS_PRICE / DOMESTIC_GOODS_PRICE (30/50 = 0.6)
  - Added transient organicsFraction and domesticGoodsFraction persistent tracking
- Updated tooltip to show organics processing rates
  - Added "Max organics processed" and "Max domestic goods output" display lines
- Added ORGANICS_PRICE and DOMESTIC_GOODS_PRICE constants loaded programmatically

## [0.1.11] - 2026-04-26

- Added transplutonic ore to transplutonics conversion pathway
  - Third pathway uses transplutonic ore (rare_ore commodity) for transplutonics production
  - Budget dynamically allocated after metals/transplutonics replenishment
  - Uses TRANSPLUTONIC_ORE_TO_TRANSPLUTONICS_RATIO = TRANSPLUTONIC_ORE_PRICE / TRANSPLUTONICS_PRICE (programmatically calculated)
  - Added transient transplutonicOreFraction persistent tracking
- Redesigned budget allocation for metals and transplutonics
  - Remaining budget after supply production now replenishes consumed materials proportionally
  - Replenishment ratio based on value spent (e.g., if 60% of value from metals, 60% of remaining budget buys metals)
  - Ensures sustainable material consumption over time
- Updated tooltip to show transplutonic ore processing rates
  - Added "Max transplutonic ore processed" and "Max transplutonics output" display lines
  - Updated ability description to mention transplutonic ore conversion
- Added TRANSPLUTONIC_ORE_PRICE constant loaded programmatically

## [0.1.10] - 2026-04-25

- Added transplutonics-to-supplies conversion pathway
  - Second pathway uses transplutonics (rare_metals commodity) for supplies production
  - Budget dynamically split between metals and transplutonics based on their relative value proportions
  - Uses TRANSPLUTONICS_TO_SUPPLIES_RATIO = TRANSPLUTONICS_PRICE / SUPPLIES_PRICE (200/100 = 2.0)
  - Added transient transplutonicsFraction persistent tracking
- Fixed supplies production capping
  - Total supplies now capped to actual fleet demand (supplyNeed)
  - Inputs scaled proportionally when exceeding need
- Fixed tooltip to show accurate availability
  - Now checks actual cargo quantities before calculating potential supplies
  - Shows 0 when no transplutonics in cargo
- Fixed commodity ID
  - Changed transplutonics commodity ID from "transplutonics" to "rare_metals"
  - Prices now loaded via getCommoditySpec("rare_metals")
- Added TRANSPLUTONICS_PRICE constant loaded programmatically

## [0.1.9] - 2026-04-24

- Refactored ratio calculations to use commodity base prices
  - Prices now loaded programmatically via CommoditySpecAPI.getBasePrice()
  - ORE_TO_METAL_RATIO = ORE_PRICE / METAL_PRICE (30/10 = 0.333...)
  - METAL_TO_SUPPLIES_RATIO = METAL_PRICE / SUPPLIES_PRICE (30/100 = 0.3)
  - Automatically adapts if commodity prices change in future patches or from mods
- Removed unused HULLMOD_COST constant
- Simplified settings.json (only budgetPercent remains configurable)
- Made hullmod tooltip "10%" strings dynamic using CARGO_SPACE_TAKEN constant
- Removed all logging from applyEffect() for performance
  - Removed ~40 logger.info() statements in hot path
  - Removed 3 logger.warn() null/zero checks
  - Removed unused Logger import
- Removed redundant cargo API calls
  - Removed duplicate getCommodityQuantity("ore") call
  - Removed unused getCommodityQuantity("metals") call (dead API call)
- Changed ability tooltip padding to 10f and renamed variable to opad

## [0.1.8] - 2026-04-23

- Added metals to supplies conversion with automatic prioritization
  - Fleet's daily supply consumption calculated per-ship and prioritized first
  - Metals converted to supplies before ore to metal processing
  - Uses value-equivalent ratio: 10/3 metals = 1 supplies (100c value)
- Updated tooltip to show supply demand and max supplies output
- Added new constants: METAL_TO_SUPPLIES_RATIO, SUPPLIES_PRICE
- Added suppliesFraction persistent tracking for integer-only operations
- Refactored supplies and metal production using `addFractionToCargo()` helper method
- Simplified supply consumption logic in `applyEffect()`
- Removed redundant `totalMetalsAvailable` variable, now uses `metalAvailable` consistently

### Fixed
- Hullmod tooltip spacing now matches vanilla standard
  - Changed padding from 3f to 10f for consistent paragraph spacing
  - Both addPara() calls now use opad = 10f
- Removed empty gap between title and description
  - Added shouldAddDescriptionToTooltip() override returning false
  - Skips empty desc field from Hull_mods.csv
  - Java now handles entire tooltip description

## [0.1.7] - 2026-04-23

### Changed
- Hullmod now reduces ship's cargo capacity by 10%
  - Equipment is installed into cargo space, reducing storage capacity
  - Uses getCargoMod().modifyMult() to apply 0.90 multiplier
- Processing budget calculation compensates for cargo reduction
  - Reads effective cargo then divides by compensation factor (1 / 0.9 = 1.111...)
  - Budget calculated from full cargo capacity regardless of cargo penalty
- Hullmod description reworded for clarity:
  - "Allows this ship to contribute %s of its cargo capacity as daily processing budget for mobile refining operations. As it is installed into the ship's cargo space, it reduces its cargo capacity by %s."
- getDescriptionParam() now returns "10%" instead of "10"
  - Percentage symbol included in Java code for proper tooltip highlighting

### Technical
- Added CARGO_SPACE_TAKEN constant (0.10)
- Added CARGO_COMPENSATION_FACTOR computed as 1 / (1 - CARGO_SPACE_TAKEN)
- Hullmod CSV description wrapped in double quotes to preserve commas

## [0.1.6] - 2026-04-22

### Changed
- Hullmod now uses cargo-based processing budget instead of hull-size-based rates
  - Processing budget = 10% of ship's cargo capacity per day
  - Credits used to process ore at base price (10c)
  - Converted at ore-to-metal ratio (3 ore → 1 metal)
  - Ore price: 10c, Metal price: 30c (break-even ratio)
- Processing budget now uses final cargo capacity after all modifiers
  - Accounts for Expanded Cargo Holds, Shielded Cargo Holds, D-mods, etc.
  - Uses getCargoMod().computeEffective() instead of base hullSpec cargo

### Fixed
- Hullmod description now correctly shows "10%" instead of showing hullmod cost percentage
  - Changed Hull_mods.csv description to static 10%
  - getDescriptionParam(0) now returns "10" for 10% display
- Fixed ore processing overcount by ~10x
  - Changed ore accumulation formula to divide by ORE_PRICE
  - Budget (credits) converted to ore units before adding to oreFraction

### Technical
- Removed refineRates from settings.json
  - No longer uses hull-size-based refine rates (CAPITAL/CRUISER/DESTROYER/FRIGATE)
  - Processing now scales directly with cargo capacity
- Added BUDGET_PERCENT constant (currently 0.10)
- Added ORE_PRICE (10) and METAL_PRICE (30) constants
- Updated hullmod costs: 8/16/24/40 for Frigate/Destroyer/Cruiser/Capital

## [0.1.5] - 2026-04-22

### Added
- Now using Gradle 9.4.1 for .jar compilation

### Fixed
- Fixed build path configuration for Gradle
  - Configured build.gradle to reference Starsector game JARs directly
  - Uses starfarer.api, starfarer_obf, fs.common_obf, json, and log4j-1.2.9 from game install
  - Points to `C:/Games/Starsector 0.98a-RC8/starsector-core` flatDir repository
- Removed unnecessary bundled resources from JAR output
  - Removed data/**, graphics/**, mod_info.json, MobileRefining.mod from JAR
  - JAR now only contains compiled /com/ classes and META-INF as intended
- Removed Maven Central org.json dependency
  - Now uses game's bundled json.jar for org.json.JSONObject

### Technical
- Upgraded Gradle wrapper to version 9.4.1
- Build produces clean JAR with only compiled Java classes
- Added .vscode/ folder with tasks.json and settings.json for VS Code development
  - Ctrl+Shift+B runs build task
- Enabled Gradle configuration cache for faster repeat builds

## [0.1.4] - 2026-04-22

### Fixed
- Fixed settings.json loading to use proper method
  - Changed from getMergedJSONForMod() to loadJSON() with modId parameter
  - Ensures mod config doesn't accidentally override other mods' settings
  - Properly preserves nested config fields like refineRates

### Optimized
- Replaced while-loop cargo operations with batch processing
  - Removed ore/metal 1-unit-at-a-time processing in favor of batch operations
  - Single cargo.addCommodity/removeCommodity call per tick instead of multiple
  - Maintains integer-only cargo operations with fraction accumulation
- Merged canActivate() into getTotalRefiningCapacity()
  - Eliminated duplicate fleet member iteration
  - Single method handles both capacity calculation and activation check

## [0.1.3] - 2026-04-21

### Fixed
- Fixed IllegalFormatConversionException when displaying tooltip
  - Changed tooltip format specifiers from `%.1f` to `%s` for String highlighting
- Fixed floating-point precision issues in refining calculations
  - Implemented persistent fraction storage for ore and metal
  - Ore is now consumed 1 unit at a time (not batched in fractional amounts)
  - Metal fractions accumulate and only add integer units to cargo when >= 1.0
  - Uses SectorAPI.getPersistentData() for savegame persistence
- Refining now pauses when cargo is full (without disabling ability)
  - No ore is consumed until cargo space becomes available

### Technical
- Added persistent fraction tracking:
  - MobileRefining_oreFraction - stores fractional ore between ticks
  - MobileRefining_metalFraction - stores fractional metal between ticks
- Fractions persist across game saves and app restarts
- Integer-only cargo modifications prevent fractional space loss

## [0.1.2] - 2026-04-21

### Fixed
- Mobile Refining ability now starts OFF and appears with enabled animation correctly
  - Modified isUsable() to check canActivate() - ability now properly disabled when no ships have hullmod
  - Modified pressButton() to use base class activate()/deactivate() methods instead of manual turnedOn toggle
  - Updated showActiveIndicator() and showProgressIndicator() to use turnedOn state
- Added modPlugin entry to mod_info.json to ensure MobileRefiningPlugin loads properly

### Technical
- Proper state management using base class methods ensures deactivate() is called when ability becomes unusable

## [0.1.1] - 2026-04-21

### Fixed
- Mobile Refining ability now appears in Codex and UI for selection
  - Migrated abilities.csv to Starsector 0.98a format
  - Moved abilities.csv from data/abilities/ to data/campaign/
  - Changed ability type from DURATION to TOGGLE
  - Set unlockedAtStart=TRUE
  - Added uiOn/uiOff sprites for proper UI display
- Ability always appears in selection UI but only toggles ON when ships have Mobile Refinery hullmod
  - Changed isUsable() to always return true
  - Added pressButton() override with canActivate() check
  - canActivate() returns true only if fleet has ships with mobile_refinery hullmod
- MobileRefiningPlugin now uses correct API methods:
  - Uses PlayerFleet.hasAbility() for check
  - Uses CharacterData.addAbility() to grant

### Technical
- Simplified abilities.csv format (matching ForgeProduction):
  - name,id,type,tags,activationDays,activationCooldown,durationDays,...
  - Empty timing fields for TOGGLE type
  - uiOn/uiOff sprites specified
- Migrated from BaseDurationAbility to BaseToggleAbility
- Simplified ability logic focusing on usability

### Removed
- Removed rules.csv based ability granting (no longer needed with CSV approach)

## [0.1.0] - 2026-04-20

Initial version

### Added
- Mobile Refining hullmod - allows ships to contribute to mobile refining operations
- Mobile Refining ability - converts ore to metal while in campaign
- Automatic ability granting to player fleet via ModPlugin
- Compiled JAR support for modding

### Technical
- Created VSCode build configuration for compiling and packaging
- Updated to use compiled Java (JAR) instead of Janino-based scripts
- Migrated from data/hullmods to src/ for proper modding support

### Mod Structure
```
MobileRefining_mod/
├── src/com/mobilerefining/
│   ├── abilities/MobileRefiningAbility.java
│   ├── hullmods/MobileRefineryHullMod.java
│   └── plugins/MobileRefiningPlugin.java
├── data/
│   ├── abilities/abilities.csv
│   ├── hullmods/Hull_mods.csv
│   └── config/settings.json
├── graphics/
├── jars/
├── mod_info.json
└── MobileRefining.mod
```