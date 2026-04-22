package com.mobilerefining.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.mobilerefining.plugins.MobileRefiningPlugin;

public class MobileRefineryHullMod extends BaseHullMod {

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return String.valueOf(MobileRefiningPlugin.HULLMOD_COST);
        }
        return null;
    }
}
