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
    private static final String PERSISTENT_KEY_SUPPLIES_FRACTION = "MobileRefining_suppliesFraction";

    @Override
    protected void activateImpl() {
    }

    @Override
    protected void applyEffect(float amount, float level) {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) {
            return;
        }

        float days = Global.getSector().getClock().convertToDays(amount);
        if (days <= 0) {
            return;
        }

        float totalBudget = getTotalProcessingBudget(fleet);
        if (totalBudget <= 0) {
            return;
        }

        CargoAPI cargo = fleet.getCargo();
        float availableOre = cargo.getCommodityQuantity("ore");
        float availableMetals = cargo.getCommodityQuantity("metals");

        Map<String, Object> persistentData = Global.getSector().getPersistentData();
        Float oreFractionObj = (Float) persistentData.get(PERSISTENT_KEY_ORE_FRACTION);
        Float metalFractionObj = (Float) persistentData.get(PERSISTENT_KEY_METAL_FRACTION);
        Float suppliesFractionObj = (Float) persistentData.get(PERSISTENT_KEY_SUPPLIES_FRACTION);

        float oreFraction = (oreFractionObj != null) ? oreFractionObj : 0f;
        float metalFraction = (metalFractionObj != null) ? metalFractionObj : 0f;
        float suppliesFraction = (suppliesFractionObj != null) ? suppliesFractionObj : 0f;

        float dailySupplyConsumption = calculateDailySupplyConsumption(fleet);
        float supplyNeed = dailySupplyConsumption * days;
        float metalNeededForSupplies = supplyNeed / MobileRefiningPlugin.METAL_TO_SUPPLIES_RATIO;
        float metalAvailable = availableMetals + metalFraction;

        float supplyBudget = totalBudget * days;
        float budgetNeededForSupplies = metalNeededForSupplies * MobileRefiningPlugin.METAL_PRICE;
        boolean canAffordFullSupplies = supplyBudget >= budgetNeededForSupplies;
        float metalBudgetForSupplies = canAffordFullSupplies ? metalNeededForSupplies : (supplyBudget / MobileRefiningPlugin.METAL_PRICE);
        float metalUsableForSupplies = Math.min(metalAvailable, metalBudgetForSupplies);

        if (metalUsableForSupplies > 0) {
            float suppliesProduced = metalUsableForSupplies * MobileRefiningPlugin.METAL_TO_SUPPLIES_RATIO;

            float remainingMetals = metalFraction - metalUsableForSupplies;
            if (remainingMetals >= 0) {
                metalFraction = remainingMetals;
            } else {
                float metalToRemove = Math.min(availableMetals, -remainingMetals);
                if (metalToRemove >= 1f) {
                    cargo.removeCommodity("metals", (int) metalToRemove);
                }
                metalFraction = remainingMetals + (int) metalToRemove;
            }

            suppliesFraction += suppliesProduced;
            suppliesFraction = addFractionToCargo(cargo, "supplies", suppliesFraction, cargo.getSpaceLeft());
        }

        float remainingBudget = totalBudget * days - (metalUsableForSupplies * MobileRefiningPlugin.METAL_PRICE);

        if (remainingBudget > 0 && availableOre > 0) {
            float newOreFraction = remainingBudget / MobileRefiningPlugin.ORE_PRICE;
            oreFraction += newOreFraction;
        }

        float maxOreToProcess = Math.min(oreFraction, availableOre);
        if (maxOreToProcess >= 1f) {
            int oreToRemove = (int) maxOreToProcess;
            cargo.removeCommodity("ore", oreToRemove);
            float metalFromOre = oreToRemove * MobileRefiningPlugin.ORE_TO_METAL_RATIO;
            metalFraction += metalFromOre;
            oreFraction -= maxOreToProcess;
        }

        metalFraction = addFractionToCargo(cargo, "metals", metalFraction, cargo.getSpaceLeft());

        suppliesFraction = addFractionToCargo(cargo, "supplies", suppliesFraction, cargo.getSpaceLeft());

        persistentData.put(PERSISTENT_KEY_ORE_FRACTION, oreFraction);
        persistentData.put(PERSISTENT_KEY_METAL_FRACTION, metalFraction);
        persistentData.put(PERSISTENT_KEY_SUPPLIES_FRACTION, suppliesFraction);
    }

    private float calculateDailySupplyConsumption(CampaignFleetAPI fleet) {
        float totalSupplies = 0f;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            totalSupplies += member.getStats().getSuppliesPerMonth().getModifiedValue() / 30f;
        }
        return totalSupplies;
    }

    private float addFractionToCargo(CargoAPI cargo, String commodity, float fraction, float maxQuantityToAdd) {
        float quantityToAdd = Math.min(fraction, maxQuantityToAdd);
        if (quantityToAdd >= 1f) {
            int quantity = (int) quantityToAdd;
            cargo.addCommodity(commodity, quantity);
            return fraction - quantity;
        }
        return fraction;
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
                float effectiveCargo = member.getStats().getCargoMod().computeEffective(baseCargo);
                float compensatedCargo = effectiveCargo * MobileRefiningPlugin.CARGO_COMPENSATION_FACTOR;
                totalBudget += compensatedCargo * MobileRefiningPlugin.BUDGET_PERCENT;
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
        float opad = 10f;
        Color highlight = Misc.getHighlightColor();

        tooltip.addTitle(getSpec().getName());

        tooltip.addPara("Convert ore to metal and metal to supplies using ships equipped with the Mobile Refinery hullmod.", opad);

        CampaignFleetAPI fleet = getFleet();
        if (fleet != null) {
            float budget = getTotalProcessingBudget(fleet);
            if (budget > 0) {
                tooltip.addPara("Processing budget: %s credits/day", opad, highlight, String.format("%.1f", budget));
                float dailySupplyConsumption = calculateDailySupplyConsumption(fleet);
                float metalAvailableFromBudget = budget / MobileRefiningPlugin.METAL_PRICE;
                float metalNeededForSupplies = dailySupplyConsumption / MobileRefiningPlugin.METAL_TO_SUPPLIES_RATIO;
                float metalUsableForSupplies = Math.min(metalNeededForSupplies, metalAvailableFromBudget);
                float suppliesPerDay = metalUsableForSupplies * MobileRefiningPlugin.METAL_TO_SUPPLIES_RATIO;
                float remainingBudget = budget - metalUsableForSupplies * MobileRefiningPlugin.METAL_PRICE;
                if (remainingBudget < 0) remainingBudget = 0;
                float maxOrePerDay = remainingBudget / MobileRefiningPlugin.ORE_PRICE;
                float metalPerDay = maxOrePerDay * MobileRefiningPlugin.ORE_TO_METAL_RATIO;
                tooltip.addPara("Supply demand: %s/day", opad, highlight, String.format("%.1f", dailySupplyConsumption));
                tooltip.addPara("Max supplies from metal: %s/day", opad, highlight, String.format("%.1f", suppliesPerDay));
                if (metalPerDay > 0) {
                    tooltip.addPara("Max ore processed: %s/day", opad, highlight, String.format("%.1f", maxOrePerDay));
                    tooltip.addPara("Max metal output: %s/day", opad, highlight, String.format("%.1f", metalPerDay));
                }
            } else {
                tooltip.addPara("No ships with Mobile Refinery hullmod in fleet.", opad, highlight);
            }
        }
    }
}