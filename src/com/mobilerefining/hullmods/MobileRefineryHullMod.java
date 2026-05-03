package com.mobilerefining.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.mobilerefining.plugins.MobileRefiningPlugin;

public class MobileRefineryHullMod extends BaseHullMod {

    public static final float SMOD_CARGO_SPACE_TAKEN = 0.15f;
    public static final float SMOD_CARGO_REDUCTION = 0.20f;
    public static final int SMOD_BURN_PENALTY = 1;

    public static float getCargoCompensationFactor(ShipAPI ship) {
        boolean sMod = ship != null && ship.getVariant().getSMods().contains("mobile_refinery");
        return 1f / (1f - (sMod ? SMOD_CARGO_REDUCTION : MobileRefiningPlugin.CARGO_SPACE_TAKEN));
    }

    public static float getCargoCompensationFactor(MutableShipStatsAPI stats) {
        boolean sMod = stats != null && stats.getVariant() != null && 
                     stats.getVariant().getSMods().contains("mobile_refinery");
        return 1f / (1f - (sMod ? SMOD_CARGO_REDUCTION : MobileRefiningPlugin.CARGO_SPACE_TAKEN));
    }

    public static float getProcessingPercent(MutableShipStatsAPI stats) {
        boolean sMod = stats != null && stats.getVariant() != null && 
                     stats.getVariant().getSMods().contains("mobile_refinery");
        return sMod ? SMOD_CARGO_SPACE_TAKEN : MobileRefiningPlugin.CARGO_SPACE_TAKEN;
    }

    @Override
    public boolean shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
        return false;
    }

    @Override
    public boolean hasSModEffect() {
        return true;
    }

    @Override
    public boolean isSModEffectAPenalty() {
        return true;
    }

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        boolean sMod = isSMod(stats);
        float cargoReduction;
        if (sMod) {
            cargoReduction = SMOD_CARGO_REDUCTION;
            stats.getMaxBurnLevel().modifyFlat(id, -SMOD_BURN_PENALTY);
        } else {
            cargoReduction = MobileRefiningPlugin.CARGO_SPACE_TAKEN;
        }
        stats.getCargoMod().modifyMult(id, 1f - cargoReduction);
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        String percent = String.format("%.0f%%", MobileRefiningPlugin.CARGO_SPACE_TAKEN * 100f);
        tooltip.addPara("Allows this ship to contribute %s of its cargo capacity as resource processing capacity for mobile refining operations.", opad, Misc.getHighlightColor(), percent);
        tooltip.addPara("As it is installed into the ship's cargo space, it reduces its cargo capacity by %s. This reduction does not apply to processing capacity.", opad, Misc.getHighlightColor(), percent);
    }

    @Override
    public void addSModEffectSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec, boolean isForBuildInList) {
        float opad = 10f;
        String processPercent = String.format("%.0f%%", SMOD_CARGO_SPACE_TAKEN * 100f);
        String reductionPercent = String.format("%.0f%%", SMOD_CARGO_REDUCTION * 100f);
        tooltip.addPara("Processing capacity increased to %s of cargo capacity.", opad, Misc.getHighlightColor(), processPercent);
        tooltip.addPara("Cargo capacity reduction increased to %s.", opad, Misc.getHighlightColor(), reductionPercent);
        tooltip.addPara("Base burn level reduced by %s.", opad, Misc.getHighlightColor(), String.valueOf(SMOD_BURN_PENALTY));
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0 || index == 1) {
            return String.format("%.0f%%", MobileRefiningPlugin.CARGO_SPACE_TAKEN * 100f);
        }
        return null;
    }

    @Override
    public String getSModDescriptionParam(int index, HullSize hullSize, ShipAPI ship) {
        if (index == 0) {
            return String.format("%.0f%%", SMOD_CARGO_SPACE_TAKEN * 100f);
        }
        if (index == 1) {
            return String.format("%.0f%%", SMOD_CARGO_REDUCTION * 100f);
        }
        if (index == 2) {
            return String.valueOf(SMOD_BURN_PENALTY);
        }
        return null;
    }
}
