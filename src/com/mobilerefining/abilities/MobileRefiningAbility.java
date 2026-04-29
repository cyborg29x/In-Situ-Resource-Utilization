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
    private static final String PERSISTENT_KEY_TRANSPLUTONIC_ORE_FRACTION = "MobileRefining_transplutonicOreFraction";
    private static final String PERSISTENT_KEY_ORGANICS_FRACTION = "MobileRefining_organicsFraction";
    private static final String PERSISTENT_KEY_DOMESTIC_GOODS_FRACTION = "MobileRefining_domesticGoodsFraction";
    private static final String PERSISTENT_KEY_VOLATILES_FRACTION = "MobileRefining_volatilesFraction";
    private static final String PERSISTENT_KEY_FUEL_FRACTION = "MobileRefining_fuelFraction";

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
        float availableTransplutonicOre = cargo.getCommodityQuantity("rare_ore");
        float availableOrganics = cargo.getCommodityQuantity("organics");
        float availableDomesticGoods = cargo.getCommodityQuantity("domestic_goods");
        float availableVolatiles = cargo.getCommodityQuantity("volatiles");

        Map<String, Object> persistentData = Global.getSector().getPersistentData();
        Float oreFractionObj = (Float) persistentData.get(PERSISTENT_KEY_ORE_FRACTION);
        Float metalFractionObj = (Float) persistentData.get(PERSISTENT_KEY_METAL_FRACTION);
        Float suppliesFractionObj = (Float) persistentData.get(PERSISTENT_KEY_SUPPLIES_FRACTION);
        Float transplutonicsFractionObj = (Float) persistentData.get(PERSISTENT_KEY_TRANSPLUTONICS_FRACTION);
        Float transplutonicOreFractionObj = (Float) persistentData.get(PERSISTENT_KEY_TRANSPLUTONIC_ORE_FRACTION);
Float organicsFractionObj = (Float) persistentData.get(PERSISTENT_KEY_ORGANICS_FRACTION);
        Float domesticGoodsFractionObj = (Float) persistentData.get(PERSISTENT_KEY_DOMESTIC_GOODS_FRACTION);
        Float volatilesFractionObj = (Float) persistentData.get(PERSISTENT_KEY_VOLATILES_FRACTION);
        Float fuelFractionObj = (Float) persistentData.get(PERSISTENT_KEY_FUEL_FRACTION);
        float oreFraction = (oreFractionObj != null) ? oreFractionObj : 0f;
        float metalFraction = (metalFractionObj != null) ? metalFractionObj : 0f;
        float suppliesFraction = (suppliesFractionObj != null) ? suppliesFractionObj : 0f;
        float transplutonicsFraction = (transplutonicsFractionObj != null) ? transplutonicsFractionObj : 0f;
        float transplutonicOreFraction = (transplutonicOreFractionObj != null) ? transplutonicOreFractionObj : 0f;
        float organicsFraction = (organicsFractionObj != null) ? organicsFractionObj : 0f;
        float domesticGoodsFraction = (domesticGoodsFractionObj != null) ? domesticGoodsFractionObj : 0f;
        float volatilesFraction = (volatilesFractionObj != null) ? volatilesFractionObj : 0f;
        float fuelFraction = (fuelFractionObj != null) ? fuelFractionObj : 0f;

        float totalCredits = totalBudget * days;
        float valueSpentOnVolatiles = 0f;

        if (fleet.isInHyperspace() && cargo.getFuel() < cargo.getMaxFuel() * 0.8f && totalCredits > 0) {
            float dailyFuelConsumption = Misc.getFuelPerDay(fleet, fleet.getCurrBurnLevel());
            float fuelNeeded = dailyFuelConsumption * days;

            float currentFuel = cargo.getFuel();
            float maxFuel = cargo.getMaxFuel();
            float maxAllowedFuel = maxFuel * 0.8f;
            float fuelSpace = maxAllowedFuel - currentFuel;
            if (fuelSpace < 0) fuelSpace = 0;

            float fuelToProduce = Math.min(fuelNeeded, fuelSpace);

            if (fuelToProduce > 0) {
                float volatilesRequired = fuelToProduce / MobileRefiningPlugin.VOLATILES_TO_FUEL_RATIO;

                float fuelValue = fuelToProduce * MobileRefiningPlugin.FUEL_PRICE;
                float maxVolatilesAffordable = fuelValue / MobileRefiningPlugin.VOLATILES_PRICE;
                float maxVolatilesWithBudget = totalCredits / MobileRefiningPlugin.VOLATILES_PRICE;
                float volatilesToProcess = Math.min(volatilesRequired, Math.min(maxVolatilesAffordable, maxVolatilesWithBudget));

                volatilesFraction -= volatilesToProcess;
                float volatilesToRemove = 0f;
                if (volatilesFraction < 0) {
                    volatilesToRemove = Math.min(availableVolatiles, -volatilesFraction);
                    if (volatilesToRemove >= 1f) {
                        cargo.removeCommodity("volatiles", (int) volatilesToRemove);
                    }
                    volatilesFraction += (int) volatilesToRemove;
                }

                float fuelFromVolatiles = volatilesToProcess * MobileRefiningPlugin.VOLATILES_TO_FUEL_RATIO;
                fuelFraction += fuelFromVolatiles;
                fuelFraction = addFuelToCargo(cargo, fuelFraction);

                valueSpentOnVolatiles = volatilesToProcess * MobileRefiningPlugin.VOLATILES_PRICE;
                Global.getLogger(this.getClass()).info("DEBUG: volatilesFraction=" + volatilesFraction + " volatilesToProcess=" + volatilesToProcess + " volatilesToRemove=" + volatilesToRemove + " fuelFromVolatiles=" + fuelFromVolatiles);
            } else {
                valueSpentOnVolatiles = 0;
            }
        }

        float remainingCredits = totalCredits - valueSpentOnVolatiles;

        float dailySupplyConsumption = calculateDailySupplyConsumption(fleet);
        float supplyNeed = dailySupplyConsumption * days;
        float metalAvailable = availableMetals + metalFraction;
        float transplutonicsAvailable = availableTransplutonics + transplutonicsFraction;

        float metalValue = metalAvailable * MobileRefiningPlugin.METAL_PRICE;
        float transplutonicsValue = transplutonicsAvailable * MobileRefiningPlugin.TRANSPLUTONICS_PRICE;
        float totalValue = metalValue + transplutonicsValue;

        float supplyBudget = remainingCredits;

        float metalUsableForSupplies = 0f;
        float transplutonicsUsableForSupplies = 0f;
        float metalValueSpent = 0f;
        float transplutonicsValueSpent = 0f;

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

            metalValueSpent = metalUsableForSupplies * MobileRefiningPlugin.METAL_PRICE;
            transplutonicsValueSpent = transplutonicsUsableForSupplies * MobileRefiningPlugin.TRANSPLUTONICS_PRICE;
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

            metalValueSpent *= scaleFactor;
            transplutonicsValueSpent *= scaleFactor;
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

        float totalValueSpent = metalValueSpent + transplutonicsValueSpent;
        float remainingBudget = remainingCredits - totalValueSpent;

        if (remainingBudget > 0 && totalValueSpent > 0) {
            float metalRatio = metalValueSpent / totalValueSpent;
            float transplutonicsRatio = transplutonicsValueSpent / totalValueSpent;

            float metalReplenishBudget = remainingBudget * metalRatio;
            float transplutonicsReplenishBudget = remainingBudget * transplutonicsRatio;

            float metalReplenish = metalReplenishBudget / MobileRefiningPlugin.METAL_PRICE;
            float transplutonicsReplenish = transplutonicsReplenishBudget / MobileRefiningPlugin.TRANSPLUTONICS_PRICE;

            if (metalReplenish >= 1f && availableMetals > 0) {
                float metalToAdd = Math.min(metalReplenish, availableMetals);
                cargo.addCommodity("metals", (int) metalToAdd);
                metalReplenish -= metalToAdd;
            }
            metalFraction += metalReplenish;

            if (transplutonicsReplenish >= 1f && availableTransplutonics > 0) {
                float transplutonicsToAdd = Math.min(transplutonicsReplenish, availableTransplutonics);
                cargo.addCommodity("rare_metals", (int) transplutonicsToAdd);
                transplutonicsReplenish -= transplutonicsToAdd;
            }
            transplutonicsFraction += transplutonicsReplenish;
        }

        float remainingBudgetAfterReplenish = remainingBudget;

        if (remainingBudgetAfterReplenish > 0 && availableOre > 0) {
            float newOreFraction = remainingBudgetAfterReplenish / MobileRefiningPlugin.ORE_PRICE;
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

        float remainingBudgetAfterOre = remainingBudgetAfterReplenish - (oreFraction * MobileRefiningPlugin.ORE_PRICE);
        if (remainingBudgetAfterOre > 0 && availableTransplutonicOre > 0) {
            float newTransplutonicOreFraction = remainingBudgetAfterOre / MobileRefiningPlugin.TRANSPLUTONIC_ORE_PRICE;
            transplutonicOreFraction += newTransplutonicOreFraction;
        }

        float maxTransplutonicOreToProcess = Math.min(transplutonicOreFraction, availableTransplutonicOre);
        if (maxTransplutonicOreToProcess >= 1f) {
            int transplutonicOreToRemove = (int) maxTransplutonicOreToProcess;
            cargo.removeCommodity("rare_ore", transplutonicOreToRemove);
            float transplutonicsFromOre = transplutonicOreToRemove * MobileRefiningPlugin.TRANSPLUTONIC_ORE_TO_TRANSPLUTONICS_RATIO;
            transplutonicsFraction += transplutonicsFromOre;
            transplutonicOreFraction -= maxTransplutonicOreToProcess;
        }

        transplutonicsFraction = addFractionToCargo(cargo, "rare_metals", transplutonicsFraction, cargo.getSpaceLeft());

        suppliesFraction = addFractionToCargo(cargo, "supplies", suppliesFraction, cargo.getSpaceLeft());

        float remainingBudgetAfterTransplutonics = remainingBudgetAfterOre - (transplutonicOreFraction * MobileRefiningPlugin.TRANSPLUTONIC_ORE_PRICE);

        if (remainingBudgetAfterTransplutonics > 0 && availableOrganics > 0) {
            float newOrganicsFraction = remainingBudgetAfterTransplutonics / MobileRefiningPlugin.ORGANICS_PRICE;
            organicsFraction += newOrganicsFraction;
        }

        float maxOrganicsToProcess = Math.min(organicsFraction, availableOrganics);
        if (maxOrganicsToProcess >= 1f) {
            int organicsToRemove = (int) maxOrganicsToProcess;
            cargo.removeCommodity("organics", organicsToRemove);
            float domesticGoodsFromOrganics = organicsToRemove * MobileRefiningPlugin.ORGANICS_TO_DOMESTIC_GOODS_RATIO;
            domesticGoodsFraction += domesticGoodsFromOrganics;
            organicsFraction -= maxOrganicsToProcess;
        }

        domesticGoodsFraction = addFractionToCargo(cargo, "domestic_goods", domesticGoodsFraction, cargo.getSpaceLeft());

        persistentData.put(PERSISTENT_KEY_ORE_FRACTION, oreFraction);
        persistentData.put(PERSISTENT_KEY_METAL_FRACTION, metalFraction);
        persistentData.put(PERSISTENT_KEY_SUPPLIES_FRACTION, suppliesFraction);
        persistentData.put(PERSISTENT_KEY_TRANSPLUTONICS_FRACTION, transplutonicsFraction);
        persistentData.put(PERSISTENT_KEY_TRANSPLUTONIC_ORE_FRACTION, transplutonicOreFraction);
        persistentData.put(PERSISTENT_KEY_ORGANICS_FRACTION, organicsFraction);
        persistentData.put(PERSISTENT_KEY_DOMESTIC_GOODS_FRACTION, domesticGoodsFraction);
        persistentData.put(PERSISTENT_KEY_VOLATILES_FRACTION, volatilesFraction);
        persistentData.put(PERSISTENT_KEY_FUEL_FRACTION, fuelFraction);
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

    private float addFuelToCargo(CargoAPI cargo, float fraction) {
        float quantityToAdd = Math.min(fraction, cargo.getMaxFuel() - cargo.getFuel());
        if (quantityToAdd > 0) {
            int quantity = (int) quantityToAdd;
            if (quantity >= 1f) {
                cargo.addFuel(quantity);
                return fraction - quantity;
            }
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

        tooltip.addPara("Convert ore to metal and transplutonic ore to transplutonics, then to supplies using ships equipped with the Mobile Refinery hullmod.", opad);

        CampaignFleetAPI fleet = getFleet();
        if (fleet != null) {
            float budget = getTotalProcessingBudget(fleet);
            if (budget > 0) {
                tooltip.addPara("Processing budget: %s credits/day", opad, highlight, String.format("%.1f", budget));
                float dailySupplyConsumption = calculateDailySupplyConsumption(fleet);

                CargoAPI cargo = fleet.getCargo();
                float availableMetals = cargo.getCommodityQuantity("metals");
                float availableTransplutonics = cargo.getCommodityQuantity("rare_metals");
                float availableOre = cargo.getCommodityQuantity("ore");
                float availableTransplutonicOre = cargo.getCommodityQuantity("rare_ore");
                float availableOrganics = cargo.getCommodityQuantity("organics");
                float availableDomesticGoods = cargo.getCommodityQuantity("domestic_goods");
                float availableVolatiles = cargo.getCommodityQuantity("volatiles");

                Map<String, Object> persistentData = Global.getSector().getPersistentData();
                Float metalFractionObj = (Float) persistentData.get(PERSISTENT_KEY_METAL_FRACTION);
                Float transplutonicsFractionObj = (Float) persistentData.get(PERSISTENT_KEY_TRANSPLUTONICS_FRACTION);
                Float transplutonicOreFractionObj = (Float) persistentData.get(PERSISTENT_KEY_TRANSPLUTONIC_ORE_FRACTION);
                Float organicsFractionObj = (Float) persistentData.get(PERSISTENT_KEY_ORGANICS_FRACTION);
                Float domesticGoodsFractionObj = (Float) persistentData.get(PERSISTENT_KEY_DOMESTIC_GOODS_FRACTION);
                Float volatilesFractionObj = (Float) persistentData.get(PERSISTENT_KEY_VOLATILES_FRACTION);
                Float fuelFractionObj = (Float) persistentData.get(PERSISTENT_KEY_FUEL_FRACTION);
                float metalFraction = (metalFractionObj != null) ? metalFractionObj : 0f;
                float transplutonicsFraction = (transplutonicsFractionObj != null) ? transplutonicsFractionObj : 0f;
                float transplutonicOreFraction = (transplutonicOreFractionObj != null) ? transplutonicOreFractionObj : 0f;
                float organicsFraction = (organicsFractionObj != null) ? organicsFractionObj : 0f;
                float domesticGoodsFraction = (domesticGoodsFractionObj != null) ? domesticGoodsFractionObj : 0f;
                float volatilesFraction = (volatilesFractionObj != null) ? volatilesFractionObj : 0f;
                float fuelFraction = (fuelFractionObj != null) ? fuelFractionObj : 0f;

                float metalAvailable = availableMetals + metalFraction;
                float transplutonicsAvailable = availableTransplutonics + transplutonicsFraction;
                float transplutonicOreAvailable = availableTransplutonicOre + transplutonicOreFraction;
                float organicsAvailable = availableOrganics + organicsFraction;
                float domesticGoodsAvailable = availableDomesticGoods + domesticGoodsFraction;

                Global.getLogger(this.getClass()).info("DEBUG TOOLTIP: availableMetals=" + availableMetals + " availableTransplutonics=" + availableTransplutonics + " metalFraction=" + metalFraction + " transplutonicsFraction=" + transplutonicsFraction);
                Global.getLogger(this.getClass()).info("DEBUG TOOLTIP: availableTransplutonicOre=" + availableTransplutonicOre + " transplutonicOreFraction=" + transplutonicOreFraction);

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
                float remainingBudgetAfterOre = remainingBudget - maxOrePerDay * MobileRefiningPlugin.ORE_PRICE;
                if (remainingBudgetAfterOre < 0) remainingBudgetAfterOre = 0;
                float maxTransplutonicOrePerDay = remainingBudgetAfterOre / MobileRefiningPlugin.TRANSPLUTONIC_ORE_PRICE;
                float transplutonicsPerDay = maxTransplutonicOrePerDay * MobileRefiningPlugin.TRANSPLUTONIC_ORE_TO_TRANSPLUTONICS_RATIO;

                float remainingBudgetAfterTransplutonics = remainingBudgetAfterOre - maxTransplutonicOrePerDay * MobileRefiningPlugin.TRANSPLUTONIC_ORE_PRICE;
                if (remainingBudgetAfterTransplutonics < 0) remainingBudgetAfterTransplutonics = 0;

                float maxVolatilesPerDay = 0f;
                float fuelPerDay = 0f;
                if (fleet.isInHyperspace() && cargo.getFuel() < cargo.getMaxFuel() * 0.8f) {
                    maxVolatilesPerDay = remainingBudgetAfterTransplutonics / MobileRefiningPlugin.VOLATILES_PRICE;
                    fuelPerDay = maxVolatilesPerDay * MobileRefiningPlugin.VOLATILES_TO_FUEL_RATIO;
                }

                float remainingBudgetAfterVolatiles = remainingBudgetAfterTransplutonics - maxVolatilesPerDay * MobileRefiningPlugin.VOLATILES_PRICE;
                if (remainingBudgetAfterVolatiles < 0) remainingBudgetAfterVolatiles = 0;
                float maxOrganicsPerDay = remainingBudgetAfterVolatiles / MobileRefiningPlugin.ORGANICS_PRICE;
                float domesticGoodsPerDay = maxOrganicsPerDay * MobileRefiningPlugin.ORGANICS_TO_DOMESTIC_GOODS_RATIO;

                tooltip.addPara("Supply demand: %s/day", opad, highlight, String.format("%.1f", dailySupplyConsumption));
                tooltip.addPara("Max supplies from metal: %s/day", opad, highlight, String.format("%.1f", suppliesFromMetal));
                tooltip.addPara("Max supplies from transplutonics: %s/day", opad, highlight, String.format("%.1f", suppliesFromTransplutonics));
                if (availableOre > 0 && metalPerDay > 0) {
                    tooltip.addPara("Max ore processed: %s/day", opad, highlight, String.format("%.1f", maxOrePerDay));
                    tooltip.addPara("Max metal output: %s/day", opad, highlight, String.format("%.1f", metalPerDay));
                }
                if (transplutonicsPerDay > 0) {
                    tooltip.addPara("Max transplutonic ore processed: %s/day", opad, highlight, String.format("%.1f", maxTransplutonicOrePerDay));
                    tooltip.addPara("Max transplutonics output: %s/day", opad, highlight, String.format("%.1f", transplutonicsPerDay));
                }
                if (availableOrganics > 0 && domesticGoodsPerDay > 0) {
                    tooltip.addPara("Max organics processed: %s/day", opad, highlight, String.format("%.1f", maxOrganicsPerDay));
                    tooltip.addPara("Max domestic goods output: %s/day", opad, highlight, String.format("%.1f", domesticGoodsPerDay));
                }
                if (fleet.isInHyperspace() && availableVolatiles > 0 && fuelPerDay > 0) {
                    tooltip.addPara("Max volatiles processed: %s/day", opad, highlight, String.format("%.1f", maxVolatilesPerDay));
                    tooltip.addPara("Max fuel output: %s/day (80%% cap)", opad, highlight, String.format("%.1f", fuelPerDay));
                }
            } else {
                tooltip.addPara("No ships with Mobile Refinery hullmod in fleet.", opad, highlight);
            }
        }
    }
}