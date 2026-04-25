# Changelog

All notable changes to this mod will be documented in this file.

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