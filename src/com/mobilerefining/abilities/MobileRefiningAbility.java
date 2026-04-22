package com.mobilerefining.abilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.abilities.BaseToggleAbility;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.mobilerefining.plugins.MobileRefiningPlugin;
import java.awt.Color;
import java.util.Map;

public class MobileRefiningAbility extends BaseToggleAbility {

    public static final String HULLMOD_ID = "mobile_refinery";
    private static final String PERSISTENT_KEY_ORE_FRACTION = "MobileRefining_oreFraction";
    private static final String PERSISTENT_KEY_METAL_FRACTION = "MobileRefining_metalFraction";

    @Override
    protected void activateImpl() {
    }

    @Override
    protected void applyEffect(float amount, float level) {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return;

        float days = Global.getSector().getClock().convertToDays(amount);
        if (days <= 0) return;

        float totalBudget = getTotalProcessingBudget(fleet);
        if (totalBudget <= 0) return;

        CargoAPI cargo = fleet.getCargo();
        float availableOre = cargo.getCommodityQuantity("ore");
        float availableSpace = cargo.getSpaceLeft();

        Map<String, Object> persistentData = Global.getSector().getPersistentData();
        Float oreFractionObj = (Float) persistentData.get(PERSISTENT_KEY_ORE_FRACTION);
        Float metalFractionObj = (Float) persistentData.get(PERSISTENT_KEY_METAL_FRACTION);

        float oreFraction = (oreFractionObj != null) ? oreFractionObj : 0f;
        float metalFraction = (metalFractionObj != null) ? metalFractionObj : 0f;

        oreFraction += (totalBudget * days) / MobileRefiningPlugin.ORE_PRICE;

        int maxOreToProcess = (int) Math.min(oreFraction, availableOre);
        if (maxOreToProcess > 0) {
            cargo.removeCommodity("ore", maxOreToProcess);
            metalFraction += maxOreToProcess / MobileRefiningPlugin.ORE_TO_METAL_RATIO;
            oreFraction -= maxOreToProcess;
        }

        int maxMetalToAdd = (int) Math.min(metalFraction, availableSpace);
        if (maxMetalToAdd > 0) {
            cargo.addCommodity("metals", maxMetalToAdd);
            metalFraction -= maxMetalToAdd;
        }

        persistentData.put(PERSISTENT_KEY_ORE_FRACTION, oreFraction);
        persistentData.put(PERSISTENT_KEY_METAL_FRACTION, metalFraction);
    }

    @Override
    protected void deactivateImpl() {
    }

    @Override
    protected void cleanupImpl() {
    }

    private float getTotalProcessingBudget(CampaignFleetAPI fleet) {
        float totalBudget = 0f;

        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.getVariant().hasHullMod(HULLMOD_ID)) {
                float baseCargo = member.getHullSpec().getCargo();
                float cargoCapacity = member.getStats().getCargoMod().computeEffective(baseCargo);
                totalBudget += cargoCapacity * MobileRefiningPlugin.BUDGET_PERCENT;
            }
        }

        return totalBudget;
    }

    @Override
    public boolean isUsable() {
        return super.isUsable() && canActivate();
    }

    @Override
    public void pressButton() {
        if (isActive()) {
            deactivate();
        } else {
            if (canActivate()) {
                activate();
            }
        }
    }

    private boolean canActivate() {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return false;

        return getTotalProcessingBudget(fleet) > 0f;
    }

    @Override
    public boolean showActiveIndicator() {
        return turnedOn;
    }

    @Override
    public boolean showProgressIndicator() {
        return turnedOn;
    }

    @Override
    public boolean hasTooltip() {
        return true;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        float pad = 3f;
        Color highlight = Misc.getHighlightColor();

        tooltip.addTitle(getSpec().getName());

        tooltip.addPara("Convert ore to metal using ships equipped with the Mobile Refinery hullmod.", pad);

        CampaignFleetAPI fleet = getFleet();
        if (fleet != null) {
            float budget = getTotalProcessingBudget(fleet);
            if (budget > 0) {
                tooltip.addPara("Processing budget: %s credits/day", pad, highlight, String.format("%.1f", budget));
                float maxOrePerDay = budget / MobileRefiningPlugin.ORE_PRICE;
                float metalPerDay = maxOrePerDay / MobileRefiningPlugin.ORE_TO_METAL_RATIO;
                tooltip.addPara("Max ore processed: %s/day", pad, highlight, String.format("%.1f", maxOrePerDay));
                tooltip.addPara("Output: %s metal/day", pad, highlight, String.format("%.1f", metalPerDay));
            } else {
                tooltip.addPara("No ships with Mobile Refinery hullmod in fleet.", pad, highlight);
            }
        }
    }
}