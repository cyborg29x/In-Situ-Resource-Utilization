package com.mobilerefining.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.mobilerefining.plugins.MobileRefiningPlugin;

public class MobileRefineryHullMod extends BaseHullMod {

    @Override
    public boolean shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
        return false;
    }

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        float cargoMult = 1f - MobileRefiningPlugin.CARGO_SPACE_TAKEN;
        stats.getCargoMod().modifyMult(id, cargoMult);
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        String percent = String.format("%.0f%%", MobileRefiningPlugin.CARGO_SPACE_TAKEN * 100f);
        tooltip.addPara("Allows this ship to contribute %s of its cargo capacity as resource processing capacity for mobile refining operations.", opad, Misc.getHighlightColor(), percent);
        tooltip.addPara("As it is installed into the ship's cargo space, it reduces its cargo capacity by %s. This reduction does not apply to processing capacity.", opad, Misc.getHighlightColor(), percent);
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0 || index == 1) {
            return String.format("%.0f%%", MobileRefiningPlugin.CARGO_SPACE_TAKEN * 100f);
        }
        return null;
    }
}
