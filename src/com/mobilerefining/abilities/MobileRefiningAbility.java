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
    private static final String PERSISTENT_KEY_TRANSPLUTONICS_FRACTION = "MobileRefining_transplutonicsFraction";

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
        float availableTransplutonics = cargo.getCommodityQuantity("rare_metals");

        Map<String, Object> persistentData = Global.getSector().getPersistentData();
        Float oreFractionObj = (Float) persistentData.get(PERSISTENT_KEY_ORE_FRACTION);
        Float metalFractionObj = (Float) persistentData.get(PERSISTENT_KEY_METAL_FRACTION);
        Float suppliesFractionObj = (Float) persistentData.get(PERSISTENT_KEY_SUPPLIES_FRACTION);
        Float transplutonicsFractionObj = (Float) persistentData.get(PERSISTENT_KEY_TRANSPLUTONICS_FRACTION);

        float oreFraction = (oreFractionObj != null) ? oreFractionObj : 0f;
        float metalFraction = (metalFractionObj != null) ? metalFractionObj : 0f;
        float suppliesFraction = (suppliesFractionObj != null) ? suppliesFractionObj : 0f;
        float transplutonicsFraction = (transplutonicsFractionObj != null) ? transplutonicsFractionObj : 0f;

        float dailySupplyConsumption = calculateDailySupplyConsumption(fleet);
        float supplyNeed = dailySupplyConsumption * days;
        float metalAvailable = availableMetals + metalFraction;
        float transplutonicsAvailable = availableTransplutonics + transplutonicsFraction;

        Global.getLogger(this.getClass()).info("DEBUG: availableMetals=" + availableMetals + " availableTransplutonics=" + availableTransplutonics + " metalFraction=" + metalFraction + " transplutonicsFraction=" + transplutonicsFraction);
        Global.getLogger(this.getClass()).info("DEBUG: metalAvailable=" + metalAvailable + " transplutonicsAvailable=" + transplutonicsAvailable);

        float metalValue = metalAvailable * MobileRefiningPlugin.METAL_PRICE;
        float transplutonicsValue = transplutonicsAvailable * MobileRefiningPlugin.TRANSPLUTONICS_PRICE;
        float totalValue = metalValue + transplutonicsValue;

        float supplyBudget = totalBudget * days;

        Global.getLogger(this.getClass()).info("DEBUG: metalValue=" + metalValue + " transplutonicsValue=" + transplutonicsValue + " totalValue=" + totalValue + " supplyBudget=" + supplyBudget);

        float metalUsableForSupplies = 0f;
        float transplutonicsUsableForSupplies = 0f;

        if (supplyNeed > 0 && totalValue > 0) {
            float metalBudgetShare;
            float transplutonicsBudgetShare;

            if (metalValue > 0 && transplutonicsValue > 0) {
                metalBudgetShare = supplyBudget * (metalValue / totalValue);
                transplutonicsBudgetShare = supplyBudget * (transplutonicsValue / totalValue);
            } else if (metalValue > 0) {
                metalBudgetShare = supplyBudget;
                transplutonicsBudgetShare = 0f;
            } else {
                metalBudgetShare = 0f;
                transplutonicsBudgetShare = supplyBudget;
            }

            float metalBudgetForSupplies = Math.min(metalBudgetShare, supplyBudget);
            float transplutonicsBudgetForSupplies = Math.min(transplutonicsBudgetShare, supplyBudget - metalBudgetForSupplies);

            metalUsableForSupplies = Math.min(metalAvailable, metalBudgetForSupplies / MobileRefiningPlugin.METAL_PRICE);
            transplutonicsUsableForSupplies = Math.min(transplutonicsAvailable, transplutonicsBudgetForSupplies / MobileRefiningPlugin.TRANSPLUTONICS_PRICE);
            Global.getLogger(this.getClass()).info("DEBUG: metalBudgetForSupplies=" + metalBudgetForSupplies + " transplutonicsBudgetForSupplies=" + transplutonicsBudgetForSupplies);
            Global.getLogger(this.getClass()).info("DEBUG: metalUsableForSupplies=" + metalUsableForSupplies + " transplutonicsUsableForSupplies=" + transplutonicsUsableForSupplies);
        }

        float suppliesFromMetals = metalUsableForSupplies * MobileRefiningPlugin.METAL_TO_SUPPLIES_RATIO;
        float suppliesFromTransplutonics = transplutonicsUsableForSupplies * MobileRefiningPlugin.TRANSPLUTONICS_TO_SUPPLIES_RATIO;
        float totalSuppliesProduced = suppliesFromMetals + suppliesFromTransplutonics;

        if (totalSuppliesProduced > supplyNeed && supplyNeed > 0) {
            float scaleFactor = supplyNeed / totalSuppliesProduced;
            metalUsableForSupplies *= scaleFactor;
            transplutonicsUsableForSupplies *= scaleFactor;
            suppliesFromMetals = metalUsableForSupplies * MobileRefiningPlugin.METAL_TO_SUPPLIES_RATIO;
            suppliesFromTransplutonics = transplutonicsUsableForSupplies * MobileRefiningPlugin.TRANSPLUTONICS_TO_SUPPLIES_RATIO;
            totalSuppliesProduced = supplyNeed;
        }

        if (totalSuppliesProduced > 0) {
            float remainingMetal = metalFraction - metalUsableForSupplies;
            if (remainingMetal >= 0) {
                metalFraction = remainingMetal;
            } else {
                float metalToRemove = Math.min(availableMetals, -remainingMetal);
                if (metalToRemove >= 1f) {
                    cargo.removeCommodity("metals", (int) metalToRemove);
                }
                metalFraction = remainingMetal + (int) metalToRemove;
            }

            float remainingTransplutonics = transplutonicsFraction - transplutonicsUsableForSupplies;
            if (remainingTransplutonics >= 0) {
                transplutonicsFraction = remainingTransplutonics;
            } else {
                float transplutonicsToRemove = Math.min(availableTransplutonics, -remainingTransplutonics);
                if (transplutonicsToRemove >= 1f) {
                    cargo.removeCommodity("rare_metals", (int) transplutonicsToRemove);
                }
                transplutonicsFraction = remainingTransplutonics + (int) transplutonicsToRemove;
            }

            suppliesFraction += totalSuppliesProduced;
            suppliesFraction = addFractionToCargo(cargo, "supplies", suppliesFraction, cargo.getSpaceLeft());
        }

        float remainingBudget = totalBudget * days - (metalUsableForSupplies * MobileRefiningPlugin.METAL_PRICE + transplutonicsUsableForSupplies * MobileRefiningPlugin.TRANSPLUTONICS_PRICE);

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
        persistentData.put(PERSISTENT_KEY_TRANSPLUTONICS_FRACTION, transplutonicsFraction);
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

                CargoAPI cargo = fleet.getCargo();
                float availableMetals = cargo.getCommodityQuantity("metals");
float availableTransplutonics = cargo.getCommodityQuantity("rare_metals");

                Map<String, Object> persistentData = Global.getSector().getPersistentData();
                Float metalFractionObj = (Float) persistentData.get(PERSISTENT_KEY_METAL_FRACTION);
                Float transplutonicsFractionObj = (Float) persistentData.get(PERSISTENT_KEY_TRANSPLUTONICS_FRACTION);
                float metalFraction = (metalFractionObj != null) ? metalFractionObj : 0f;
                float transplutonicsFraction = (transplutonicsFractionObj != null) ? transplutonicsFractionObj : 0f;

                float metalAvailable = availableMetals + metalFraction;
                float transplutonicsAvailable = availableTransplutonics + transplutonicsFraction;

                Global.getLogger(this.getClass()).info("DEBUG TOOLTIP: availableMetals=" + availableMetals + " availableTransplutonics=" + availableTransplutonics + " metalFraction=" + metalFraction + " transplutonicsFraction=" + transplutonicsFraction);

                float metalValue = metalAvailable * MobileRefiningPlugin.METAL_PRICE;
                float transplutonicsValue = transplutonicsAvailable * MobileRefiningPlugin.TRANSPLUTONICS_PRICE;
                float totalValue = metalValue + transplutonicsValue;

                Global.getLogger(this.getClass()).info("DEBUG TOOLTIP: metalValue=" + metalValue + " transplutonicsValue=" + transplutonicsValue + " totalValue=" + totalValue + " budget=" + budget);

                float metalUsableForSupplies = 0f;
                float transplutonicsUsableForSupplies = 0f;

                if (totalValue > 0) {
                    float supplyBudget = budget;
                    float metalBudgetShare;
                    float transplutonicsBudgetShare;

                    if (metalValue > 0 && transplutonicsValue > 0) {
                        metalBudgetShare = supplyBudget * (metalValue / totalValue);
                        transplutonicsBudgetShare = supplyBudget * (transplutonicsValue / totalValue);
                    } else if (metalValue > 0) {
                        metalBudgetShare = supplyBudget;
                        transplutonicsBudgetShare = 0f;
                    } else {
                        metalBudgetShare = 0f;
                        transplutonicsBudgetShare = supplyBudget;
                    }

                    float metalBudgetForSupplies = Math.min(metalBudgetShare, supplyBudget);
                    float transplutonicsBudgetForSupplies = Math.min(transplutonicsBudgetShare, supplyBudget - metalBudgetForSupplies);

                    metalUsableForSupplies = Math.min(metalAvailable, metalBudgetForSupplies / MobileRefiningPlugin.METAL_PRICE);
                    transplutonicsUsableForSupplies = Math.min(transplutonicsAvailable, transplutonicsBudgetForSupplies / MobileRefiningPlugin.TRANSPLUTONICS_PRICE);
                    Global.getLogger(this.getClass()).info("DEBUG TOOLTIP: metalBudgetForSupplies=" + metalBudgetForSupplies + " transplutonicsBudgetForSupplies=" + transplutonicsBudgetForSupplies);
                    Global.getLogger(this.getClass()).info("DEBUG TOOLTIP: metalUsableForSupplies=" + metalUsableForSupplies + " transplutonicsUsableForSupplies=" + transplutonicsUsableForSupplies);
                }

                float suppliesFromMetal = metalUsableForSupplies * MobileRefiningPlugin.METAL_TO_SUPPLIES_RATIO;
                float suppliesFromTransplutonics = transplutonicsUsableForSupplies * MobileRefiningPlugin.TRANSPLUTONICS_TO_SUPPLIES_RATIO;

                float remainingBudget = budget - metalUsableForSupplies * MobileRefiningPlugin.METAL_PRICE - transplutonicsUsableForSupplies * MobileRefiningPlugin.TRANSPLUTONICS_PRICE;
                if (remainingBudget < 0) remainingBudget = 0;
                float maxOrePerDay = remainingBudget / MobileRefiningPlugin.ORE_PRICE;
                float metalPerDay = maxOrePerDay * MobileRefiningPlugin.ORE_TO_METAL_RATIO;

                tooltip.addPara("Supply demand: %s/day", opad, highlight, String.format("%.1f", dailySupplyConsumption));
                tooltip.addPara("Max supplies from metal: %s/day", opad, highlight, String.format("%.1f", suppliesFromMetal));
                tooltip.addPara("Max supplies from transplutonics: %s/day", opad, highlight, String.format("%.1f", suppliesFromTransplutonics));
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