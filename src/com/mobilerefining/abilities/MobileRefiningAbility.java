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

public class MobileRefiningAbility extends BaseToggleAbility {

    public static final String HULLMOD_ID = "mobile_refinery";

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

        float totalCredits = totalBudget * days;
        float valueSpentOnVolatiles = 0f;

        if (fleet.isInHyperspace() && cargo.getFuel() < cargo.getMaxFuel() * 0.8f && totalCredits > 0) {
            float dailyFuelConsumption = Misc.getFuelPerDay(fleet, fleet.getCurrBurnLevel());
            float fuelNeeded = dailyFuelConsumption * days;

            float currentFuel = cargo.getFuel();
            float maxFuel = cargo.getMaxFuel();
            float maxAllowedFuel = Math.max(maxFuel * 0.8f, maxFuel - 500f);
            float fuelSpace = Math.max(0, maxAllowedFuel - currentFuel);

            float fuelToProduce = Math.min(fuelNeeded, fuelSpace);

            if (fuelToProduce > 0) {
                float volatilesRequired = fuelToProduce / MobileRefiningPlugin.VOLATILES_TO_FUEL_RATIO;
                float availableVolatiles = cargo.getCommodityQuantity("volatiles");
                float volatilesAvailableForProcessing = Math.max(0, availableVolatiles - 30f);
                float budgetByFuelSpace = volatilesRequired * MobileRefiningPlugin.VOLATILES_PRICE;
                float budgetByVolatiles = volatilesAvailableForProcessing * MobileRefiningPlugin.VOLATILES_PRICE;
                float effectiveBudget = Math.min(totalCredits, Math.min(budgetByFuelSpace, budgetByVolatiles));

                valueSpentOnVolatiles = processResource(cargo, effectiveBudget,
                    "volatiles", MobileRefiningPlugin.VOLATILES_PRICE,
                    "fuel", MobileRefiningPlugin.VOLATILES_TO_FUEL_RATIO);
            } else {
                valueSpentOnVolatiles = 0;
            }
        }

        float remainingCredits = totalCredits - valueSpentOnVolatiles;

        float supplyNeed = calculateSupplyNeed(fleet, days, cargo);

        float remainingBudget = remainingCredits;

        float maxMetalBudget = supplyNeed / MobileRefiningPlugin.METAL_TO_SUPPLIES_RATIO * MobileRefiningPlugin.METAL_PRICE;
        float budgetForMetals = Math.min(remainingBudget, maxMetalBudget);
        float metalsSpent = processResource(cargo, budgetForMetals,
            "metals", MobileRefiningPlugin.METAL_PRICE,
            "supplies", MobileRefiningPlugin.METAL_TO_SUPPLIES_RATIO);
        remainingBudget -= metalsSpent;

        float metalSuppliesProduced = metalsSpent / MobileRefiningPlugin.METAL_PRICE * MobileRefiningPlugin.METAL_TO_SUPPLIES_RATIO;
        float remainingNeed = Math.max(0, supplyNeed - metalSuppliesProduced);
        float maxTransplutonicsBudget = remainingNeed / MobileRefiningPlugin.TRANSPLUTONICS_TO_SUPPLIES_RATIO * MobileRefiningPlugin.TRANSPLUTONICS_PRICE;
        float budgetForTransplutonics = Math.min(remainingBudget, maxTransplutonicsBudget);
        remainingBudget -= processResource(cargo, budgetForTransplutonics,
            "rare_metals", MobileRefiningPlugin.TRANSPLUTONICS_PRICE,
            "supplies", MobileRefiningPlugin.TRANSPLUTONICS_TO_SUPPLIES_RATIO);

        float currentFuel = cargo.getFuel();
        float maxFuel = cargo.getMaxFuel();
        float maxAllowedFuel = Math.max(maxFuel * 0.8f, maxFuel - 500f);
        float fuelSpace = Math.max(0, maxAllowedFuel - currentFuel);

        if (fuelSpace > 0 && remainingBudget > 0) {
            float volatilesRequired = fuelSpace / MobileRefiningPlugin.VOLATILES_TO_FUEL_RATIO;
            float availableVolatiles = cargo.getCommodityQuantity("volatiles");
            float volatilesAvailableForProcessing = Math.max(0, availableVolatiles - 30f);
            float budgetByFuelSpace = volatilesRequired * MobileRefiningPlugin.VOLATILES_PRICE;
            float budgetByVolatiles = volatilesAvailableForProcessing * MobileRefiningPlugin.VOLATILES_PRICE;
            float effectiveBudget = Math.min(remainingBudget, Math.min(budgetByFuelSpace, budgetByVolatiles));
            remainingBudget -= processResource(cargo, effectiveBudget,
                "volatiles", MobileRefiningPlugin.VOLATILES_PRICE,
                "fuel", MobileRefiningPlugin.VOLATILES_TO_FUEL_RATIO);
        }

        remainingBudget -= processResource(cargo, remainingBudget,
            "metals", MobileRefiningPlugin.METAL_PRICE,
            "supplies", MobileRefiningPlugin.METAL_TO_SUPPLIES_RATIO);

        float remainingBudgetAfterOre = remainingBudget - processResource(cargo, remainingBudget,
            "ore", MobileRefiningPlugin.ORE_PRICE,
            "metals", MobileRefiningPlugin.ORE_TO_METAL_RATIO);

        float remainingBudgetAfterTransplutonics = remainingBudgetAfterOre - processResource(cargo, remainingBudgetAfterOre,
            "rare_ore", MobileRefiningPlugin.TRANSPLUTONIC_ORE_PRICE,
            "rare_metals", MobileRefiningPlugin.TRANSPLUTONIC_ORE_TO_TRANSPLUTONICS_RATIO);

        processResource(cargo, remainingBudgetAfterTransplutonics,
            "organics", MobileRefiningPlugin.ORGANICS_PRICE,
            "domestic_goods", MobileRefiningPlugin.ORGANICS_TO_DOMESTIC_GOODS_RATIO);
    }

    private float calculateDailySupplyConsumption(CampaignFleetAPI fleet) {
        return fleet.getLogistics().getShipMaintenanceSupplyCost();
    }

    private float calculateBaseSupplyConsumption(CampaignFleetAPI fleet) {
        float totalSupplies = 0f;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            totalSupplies += member.getStats().getSuppliesPerMonth().getModifiedValue() / 30f;
        }
        return totalSupplies;
    }

    private float calculateMilitaryShipDeploymentSupplyCost(CampaignFleetAPI fleet) {
        float totalDeploymentCost = 0f;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (!member.getVariant().isCivilian()) {
                totalDeploymentCost += member.getStats().getSuppliesToRecover().getModifiedValue();
            }
        }
        return totalDeploymentCost;
    }

    private float processResource(CargoAPI cargo, float budget,
            String inputCommodity, float inputPrice,
            String outputCommodity, float outputRatio) {

        float inputToProcess = 0f;
        if (budget > 0) {
            inputToProcess = budget / inputPrice;
        }

        float available = cargo.getCommodityQuantity(inputCommodity);
        float maxToProcess = Math.min(inputToProcess, available);
        if (maxToProcess > 0) {
            cargo.removeCommodity(inputCommodity, maxToProcess);
            float outputProduced = maxToProcess * outputRatio;
            cargo.addCommodity(outputCommodity, outputProduced);
            return maxToProcess * inputPrice;
        }

        return 0f;
    }

    @Override
    protected void deactivateImpl() {
    }

    @Override
    protected void cleanupImpl() {
    }

    private float calculateSupplyNeed(CampaignFleetAPI fleet, float days, CargoAPI cargo) {
        float dailySupplyConsumption = calculateDailySupplyConsumption(fleet);
        float militaryDeploymentCost = calculateMilitaryShipDeploymentSupplyCost(fleet);
        float currentSupplies = cargo.getSupplies();
        float deploymentCostNeeded = Math.max(0, militaryDeploymentCost - currentSupplies);
        return (dailySupplyConsumption * days) + deploymentCostNeeded;
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
                float availableOrganics = cargo.getCommodityQuantity("organics");
                float availableVolatiles = cargo.getCommodityQuantity("volatiles");

                float metalValue = availableMetals * MobileRefiningPlugin.METAL_PRICE;
                float transplutonicsValue = availableTransplutonics * MobileRefiningPlugin.TRANSPLUTONICS_PRICE;
                float totalValue = metalValue + transplutonicsValue;

                float metalUsableForSupplies = 0f;
                float transplutonicsUsableForSupplies = 0f;

                if (totalValue > 0) {
                    float supplyBudget = budget;

                    float metalBudgetForSupplies = Math.min(supplyBudget, metalValue);
                    metalUsableForSupplies = Math.min(availableMetals, metalBudgetForSupplies / MobileRefiningPlugin.METAL_PRICE);
                    float suppliesFromMetalOnly = metalUsableForSupplies * MobileRefiningPlugin.METAL_TO_SUPPLIES_RATIO;

                    float supplyNeed = calculateSupplyNeed(fleet, 1f, cargo);
                    float remainingSupplyNeed = supplyNeed - suppliesFromMetalOnly;
                    if (remainingSupplyNeed > 0 && availableTransplutonics > 0) {
                        float transplutonicsBudgetForSupplies = Math.min(supplyBudget - metalBudgetForSupplies, transplutonicsValue);
                        transplutonicsUsableForSupplies = Math.min(availableTransplutonics, transplutonicsBudgetForSupplies / MobileRefiningPlugin.TRANSPLUTONICS_PRICE);
                    }
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

                float militaryDeploymentCost = calculateMilitaryShipDeploymentSupplyCost(fleet);
                float baseSupplyCost = calculateBaseSupplyConsumption(fleet);
                float repairSupplyCost = dailySupplyConsumption - baseSupplyCost;
                if (repairSupplyCost > 0) {
                    tooltip.addPara("Supply demand: %s/day (+ %s/day repairs + %s deployment)", opad, highlight, String.format("%.1f", dailySupplyConsumption), String.format("%.1f", repairSupplyCost), String.format("%.1f", militaryDeploymentCost));
                } else {
                    tooltip.addPara("Supply demand: %s/day (+ %s deployment)", opad, highlight, String.format("%.1f", dailySupplyConsumption), String.format("%.1f", militaryDeploymentCost));
                }
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