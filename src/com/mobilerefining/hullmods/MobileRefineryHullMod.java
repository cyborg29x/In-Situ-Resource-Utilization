package com.mobilerefining.hullmods;

import com.fs.starfarer.api.impl.hullmods.BaseLogisticsHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;
import com.mobilerefining.plugins.MobileRefiningPlugin;

public class MobileRefineryHullMod extends BaseLogisticsHullMod {

    public static final float SMOD_CARGO_SPACE_TAKEN = 0.15f;
    public static final float SMOD_CARGO_REDUCTION = 0.20f;
    public static final int SMOD_BURN_PENALTY = 1;
    public static final float SMOD_SUPPLY_PENALTY = 10f;

    public static float getCargoCompensationFactor(ShipAPI ship) {
        boolean sMod = ship != null && ship.getVariant().getSMods().contains("integrated_forge");
        return 1f / (1f - (sMod ? SMOD_CARGO_REDUCTION : MobileRefiningPlugin.CARGO_SPACE_TAKEN));
    }

    public static float getCargoCompensationFactor(MutableShipStatsAPI stats) {
        boolean sMod = stats != null && stats.getVariant() != null && 
                     stats.getVariant().getSMods().contains("integrated_forge");
        return 1f / (1f - (sMod ? SMOD_CARGO_REDUCTION : MobileRefiningPlugin.CARGO_SPACE_TAKEN));
    }

    public static float getProcessingPercent(MutableShipStatsAPI stats) {
        boolean sMod = stats != null && stats.getVariant() != null && 
                     stats.getVariant().getSMods().contains("integrated_forge");
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
            stats.getSuppliesPerMonth().modifyPercent(id, SMOD_SUPPLY_PENALTY);
        } else {
            cargoReduction = MobileRefiningPlugin.CARGO_SPACE_TAKEN;
        }
        stats.getCargoMod().modifyMult(id, 1f - cargoReduction);
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        Color blue = Misc.getBasePlayerColor();
        Color darkBlue = Misc.getDarkPlayerColor();

        String percent = String.format("%.0f%%", MobileRefiningPlugin.CARGO_SPACE_TAKEN * 100f);
        
        Color yellow = Misc.getHighlightColor();
        tooltip.addPara("Enables the %s processing of certain resources into more compact and useful forms. Unlike that of planetside industries, the machinery is optimized for compactness and power-efficiency and therefore, resources are processed in a way that merely %s their total value.", 
            opad, new Color[] {yellow, yellow}, "onboard", "preserves");
        
        tooltip.addPara("Turns %s of the ship's cargo capacity into resource processing capacity. %s of cargo is equivalent to %s's worth processed %s.", 
            opad, new Color[] {yellow, yellow, yellow, yellow}, 
            percent, "One unit", "one credit", "per day");
        
        tooltip.addPara("As the equipment is installed into the ship's cargo spaces, it reduces cargo capacity by %s. This reduction does %s apply to processing capacity.",
            opad, new Color[] {yellow, yellow},
            percent, "not");

        tooltip.addPara("Usage of surplus flux grid power does not confer additional effects to installed equipment.", opad);

        tooltip.addSectionHeading("Commodity restrictions", blue, darkBlue, Alignment.MID, opad);

        tooltip.addPara("Processes volatiles into fuel, metal and transplutonics into supplies, raw ores into metal and transplutonics, respectively, and organics into domestic goods only.", opad);
    }

    @Override
    public void addSModEffectSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec, boolean isForBuildInList) {
        float opad = 10f;
        
        tooltip.addPara("Permanent integration into ship systems allows for seamless operation of processing machinery at the cost of increased strain on the flux grid. Requires dedicated power conduits that divert power from existing systems.", opad);
        
        String processPercent = String.format("%.0f%%", SMOD_CARGO_SPACE_TAKEN * 100f);
        String reductionPercent = String.format("%.0f%%", SMOD_CARGO_REDUCTION * 100f);
        tooltip.addPara("Processing capacity increased to %s of ship's cargo capacity. Cargo capacity reduction increased to %s. Base burn level reduced by %s. Supply consumption increased by %s.", 
            opad, Misc.getHighlightColor(), processPercent, reductionPercent, String.valueOf(SMOD_BURN_PENALTY), String.valueOf((int) SMOD_SUPPLY_PENALTY) + "%");
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
        if (index == 3) {
            return String.valueOf((int) SMOD_SUPPLY_PENALTY) + "%";
        }
        return null;
    }
}
