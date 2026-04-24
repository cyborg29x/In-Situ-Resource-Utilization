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
import org.apache.log4j.Logger;

public class MobileRefiningAbility extends BaseToggleAbility {

    private static final Logger logger = Global.getLogger(MobileRefiningAbility.class);
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
            logger.warn("Fleet is null, cannot process");
            return;
        }

        float days = Global.getSector().getClock().convertToDays(amount);
        if (days <= 0) {
            logger.warn("Days amount <= 0, skipping: " + days);
            return;
        }

        float totalBudget = getTotalProcessingBudget(fleet);
        if (totalBudget <= 0) {
            logger.warn("Total budget <= 0, skipping: " + totalBudget);
            return;
        }

        logger.info("=== Starting applyEffect ===");
        logger.info("fleet: " + fleet.getName() + ", days: " + days + ", totalBudget: " + totalBudget);

        CargoAPI cargo = fleet.getCargo();
        float availableOre = cargo.getCommodityQuantity("ore");
        float availableMetals = cargo.getCommodityQuantity("metals");
        float availableSpace = cargo.getSpaceLeft();

        logger.info("Initial cargo state - ore: " + availableOre + ", metals: " + availableMetals + ", space: " + availableSpace + ", supplies: " + cargo.getCommodityQuantity("supplies"));

        Map<String, Object> persistentData = Global.getSector().getPersistentData();
        Float oreFractionObj = (Float) persistentData.get(PERSISTENT_KEY_ORE_FRACTION);
        Float metalFractionObj = (Float) persistentData.get(PERSISTENT_KEY_METAL_FRACTION);
        Float suppliesFractionObj = (Float) persistentData.get(PERSISTENT_KEY_SUPPLIES_FRACTION);

        float oreFraction = (oreFractionObj != null) ? oreFractionObj : 0f;
        float metalFraction = (metalFractionObj != null) ? metalFractionObj : 0f;
        float suppliesFraction = (suppliesFractionObj != null) ? suppliesFractionObj : 0f;

        logger.info("Stored fractions - ore: " + oreFraction + ", metal: " + metalFraction + ", supplies: " + suppliesFraction);

        float dailySupplyConsumption = calculateDailySupplyConsumption(fleet);
        float supplyNeed = dailySupplyConsumption * days;
        float metalNeededForSupplies = supplyNeed / MobileRefiningPlugin.METAL_TO_SUPPLIES_RATIO;
        float metalAvailable = availableMetals + metalFraction;

        logger.info("=== Supplies Production ===");
        logger.info("dailySupplyConsumption: " + dailySupplyConsumption + ", supplyNeed: " + supplyNeed);
        logger.info("metalNeededForSupplies: " + metalNeededForSupplies + ", metalAvailable: " + metalAvailable);

        float supplyBudget = totalBudget * days;
        float budgetNeededForSupplies = metalNeededForSupplies * MobileRefiningPlugin.METAL_PRICE;
        boolean canAffordFullSupplies = supplyBudget >= budgetNeededForSupplies;
        float metalBudgetForSupplies = canAffordFullSupplies ? metalNeededForSupplies : (supplyBudget / MobileRefiningPlugin.METAL_PRICE);
        float metalUsableForSupplies = Math.min(metalAvailable, metalBudgetForSupplies);

        logger.info("supplyBudget: " + supplyBudget + ", budgetNeededForSupplies: " + budgetNeededForSupplies + ", canAffordFullSupplies: " + canAffordFullSupplies);
        logger.info("metalBudgetForSupplies: " + metalBudgetForSupplies + ", metalUsableForSupplies: " + metalUsableForSupplies);

        if (metalUsableForSupplies > 0) {
            float suppliesProduced = metalUsableForSupplies * MobileRefiningPlugin.METAL_TO_SUPPLIES_RATIO;
            logger.info("suppliesProduced: " + suppliesProduced);

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
        } else {
            logger.info("SKIPPING supplies production - metalUsableForSupplies: " + metalUsableForSupplies + ", availableSpace: " + availableSpace);
        }

        availableOre = cargo.getCommodityQuantity("ore");
        float remainingBudget = totalBudget * days - (metalUsableForSupplies * MobileRefiningPlugin.METAL_PRICE);
        logger.info("=== Ore Processing ===");
        logger.info("remainingBudget after supplies: " + remainingBudget + ", availableOre: " + availableOre);
        if (remainingBudget > 0 && availableOre > 0) {
            float newOreFraction = remainingBudget / MobileRefiningPlugin.ORE_PRICE;
            logger.info("New oreFraction from budget: " + newOreFraction);
            oreFraction += newOreFraction;
            logger.info("oreFraction after adding: " + oreFraction);
        }

        float maxOreToProcess = Math.min(oreFraction, availableOre);
        logger.info("maxOreToProcess: " + maxOreToProcess + ", oreFraction: " + oreFraction + ", availableOre: " + availableOre);
        if (maxOreToProcess >= 1f) {
            int oreToRemove = (int) maxOreToProcess;
            logger.info("Calling removeCommodity - ore, quantity: " + oreToRemove);
            cargo.removeCommodity("ore", oreToRemove);
            float metalFromOre = oreToRemove * MobileRefiningPlugin.ORE_TO_METAL_RATIO;
            metalFraction += metalFromOre;
            oreFraction -= maxOreToProcess;
            logger.info("Metal produced from ore: " + metalFromOre + ", oreFraction after: " + oreFraction + ", metalFraction after: " + metalFraction);
        } else {
            logger.info("Skipping ore removal - maxOreToProcess < 1");
        }

        availableMetals = cargo.getCommodityQuantity("metals");
        logger.info("=== Metal Production ===");
        logger.info("availableMetals after ore processing: " + availableMetals + ", metalFraction: " + metalFraction);

        metalFraction = addFractionToCargo(cargo, "metals", metalFraction, cargo.getSpaceLeft());

        suppliesFraction = addFractionToCargo(cargo, "supplies", suppliesFraction, cargo.getSpaceLeft());

        persistentData.put(PERSISTENT_KEY_ORE_FRACTION, oreFraction);
        persistentData.put(PERSISTENT_KEY_METAL_FRACTION, metalFraction);
        persistentData.put(PERSISTENT_KEY_SUPPLIES_FRACTION, suppliesFraction);

        logger.info("=== Final State ===");
        logger.info("ore: " + cargo.getCommodityQuantity("ore") + ", metals: " + cargo.getCommodityQuantity("metals") + ", supplies: " + cargo.getCommodityQuantity("supplies"));
        logger.info("oreFraction: " + oreFraction + ", metalFraction: " + metalFraction + ", suppliesFraction: " + suppliesFraction);
        logger.info("=== End applyEffect ===");
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
        float pad = 3f;
        Color highlight = Misc.getHighlightColor();

        tooltip.addTitle(getSpec().getName());

        tooltip.addPara("Convert ore to metal and metal to supplies using ships equipped with the Mobile Refinery hullmod.", pad);

        CampaignFleetAPI fleet = getFleet();
        if (fleet != null) {
            float budget = getTotalProcessingBudget(fleet);
            if (budget > 0) {
                tooltip.addPara("Processing budget: %s credits/day", pad, highlight, String.format("%.1f", budget));
                float dailySupplyConsumption = calculateDailySupplyConsumption(fleet);
                float metalAvailableFromBudget = budget / MobileRefiningPlugin.METAL_PRICE;
                float metalNeededForSupplies = dailySupplyConsumption / MobileRefiningPlugin.METAL_TO_SUPPLIES_RATIO;
                float metalUsableForSupplies = Math.min(metalNeededForSupplies, metalAvailableFromBudget);
                float suppliesPerDay = metalUsableForSupplies * MobileRefiningPlugin.METAL_TO_SUPPLIES_RATIO;
                float remainingBudget = budget - metalUsableForSupplies * MobileRefiningPlugin.METAL_PRICE;
                if (remainingBudget < 0) remainingBudget = 0;
                float maxOrePerDay = remainingBudget / MobileRefiningPlugin.ORE_PRICE;
                float metalPerDay = maxOrePerDay * MobileRefiningPlugin.ORE_TO_METAL_RATIO;
                tooltip.addPara("Supply demand: %s/day", pad, highlight, String.format("%.1f", dailySupplyConsumption));
                tooltip.addPara("Max supplies from metal: %s/day", pad, highlight, String.format("%.1f", suppliesPerDay));
                if (metalPerDay > 0) {
                    tooltip.addPara("Max ore processed: %s/day", pad, highlight, String.format("%.1f", maxOrePerDay));
                    tooltip.addPara("Max metal output: %s/day", pad, highlight, String.format("%.1f", metalPerDay));
                }
            } else {
                tooltip.addPara("No ships with Mobile Refinery hullmod in fleet.", pad, highlight);
            }
        }
    }
}