# Changelog

All notable changes to this mod will be documented in this file.

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