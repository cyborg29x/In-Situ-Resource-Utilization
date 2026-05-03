package com.mobilerefining.abilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.abilities.BaseToggleAbility;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.mobilerefining.plugins.MobileRefiningPlugin;
import com.mobilerefining.utils.MissionCargoTracker;
import com.mobilerefining.hullmods.MobileRefineryHullMod;
import java.awt.Color;
import java.util.Map;

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
        Map<String, Float> reservedCommodities = MissionCargoTracker.getAllReservedCommodities();

        float processingCapacity = totalBudget * days;

        if (fleet.isInHyperspace() && cargo.getFuel() < cargo.getMaxFuel() * 0.8f && processingCapacity > 0) {
            float dailyFuelConsumption = Misc.getFuelPerDay(fleet, fleet.getCurrBurnLevel());
            float fuelNeeded = dailyFuelConsumption * days;

            float fuelSpace = calculateFuelSpace(cargo);

            float fuelToProduce = Math.min(fuelNeeded, fuelSpace);

            if (fuelToProduce > 0) {
                float volatilesRequired = fuelToProduce / MobileRefiningPlugin.VOLATILES_TO_FUEL_RATIO;
                float availableVolatiles = MissionCargoTracker.getAvailableQuantity("volatiles", cargo, reservedCommodities);
                float volatilesAvailableForProcessing = Math.max(0, availableVolatiles - 30f);
                float budgetByFuelSpace = volatilesRequired * MobileRefiningPlugin.VOLATILES_PRICE;
                float budgetByVolatiles = volatilesAvailableForProcessing * MobileRefiningPlugin.VOLATILES_PRICE;
                float effectiveBudget = Math.min(processingCapacity, Math.min(budgetByFuelSpace, budgetByVolatiles));

                processingCapacity -= processResource(cargo, effectiveBudget, reservedCommodities,
                    "volatiles", MobileRefiningPlugin.VOLATILES_PRICE,
                    "fuel", MobileRefiningPlugin.VOLATILES_TO_FUEL_RATIO);
            }
        }

        if (processingCapacity <= 0) return;

        float supplyNeed = calculateSupplyNeed(fleet, days, cargo);

        float maxMetalBudget = supplyNeed / MobileRefiningPlugin.METAL_TO_SUPPLIES_RATIO * MobileRefiningPlugin.METAL_PRICE;
        float budgetForMetals = Math.min(processingCapacity, maxMetalBudget);
        float metalsSpent = processResource(cargo, budgetForMetals, reservedCommodities,
            "metals", MobileRefiningPlugin.METAL_PRICE,
            "supplies", MobileRefiningPlugin.METAL_TO_SUPPLIES_RATIO);
        processingCapacity -= metalsSpent;
        if (processingCapacity <= 0) return;

        float metalSuppliesProduced = metalsSpent / MobileRefiningPlugin.METAL_PRICE * MobileRefiningPlugin.METAL_TO_SUPPLIES_RATIO;
        float remainingNeed = Math.max(0, supplyNeed - metalSuppliesProduced);
        float maxTransplutonicsBudget = remainingNeed / MobileRefiningPlugin.TRANSPLUTONICS_TO_SUPPLIES_RATIO * MobileRefiningPlugin.TRANSPLUTONICS_PRICE;
        float budgetForTransplutonics = Math.min(processingCapacity, maxTransplutonicsBudget);
        processingCapacity -= processResource(cargo, budgetForTransplutonics, reservedCommodities,
            "rare_metals", MobileRefiningPlugin.TRANSPLUTONICS_PRICE,
            "supplies", MobileRefiningPlugin.TRANSPLUTONICS_TO_SUPPLIES_RATIO);
        if (processingCapacity <= 0) return;

        float fuelSpace = calculateFuelSpace(cargo);

        if (fuelSpace > 0) {
            float volatilesRequired = fuelSpace / MobileRefiningPlugin.VOLATILES_TO_FUEL_RATIO;
            float availableVolatiles = MissionCargoTracker.getAvailableQuantity("volatiles", cargo, reservedCommodities);
            float volatilesAvailableForProcessing = Math.max(0, availableVolatiles - 30f);
            float budgetByFuelSpace = volatilesRequired * MobileRefiningPlugin.VOLATILES_PRICE;
            float budgetByVolatiles = volatilesAvailableForProcessing * MobileRefiningPlugin.VOLATILES_PRICE;
            float effectiveBudget = Math.min(processingCapacity, Math.min(budgetByFuelSpace, budgetByVolatiles));
            processingCapacity -= processResource(cargo, effectiveBudget, reservedCommodities,
                "volatiles", MobileRefiningPlugin.VOLATILES_PRICE,
                "fuel", MobileRefiningPlugin.VOLATILES_TO_FUEL_RATIO);
        }
        if (processingCapacity <= 0) return;

        processingCapacity -= processResource(cargo, processingCapacity, reservedCommodities,
            "metals", MobileRefiningPlugin.METAL_PRICE,
            "supplies", MobileRefiningPlugin.METAL_TO_SUPPLIES_RATIO);
        if (processingCapacity <= 0) return;

        processingCapacity -= processResource(cargo, processingCapacity, reservedCommodities,
            "ore", MobileRefiningPlugin.ORE_PRICE,
            "metals", MobileRefiningPlugin.ORE_TO_METAL_RATIO);
        if (processingCapacity <= 0) return;

        processingCapacity -= processResource(cargo, processingCapacity, reservedCommodities,
            "rare_ore", MobileRefiningPlugin.TRANSPLUTONIC_ORE_PRICE,
            "rare_metals", MobileRefiningPlugin.TRANSPLUTONIC_ORE_TO_TRANSPLUTONICS_RATIO);
        if (processingCapacity <= 0) return;

        processResource(cargo, processingCapacity, reservedCommodities,
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

    private float processResource(CargoAPI cargo, float budget, Map<String, Float> reservedCommodities,
            String inputCommodity, float inputPrice,
            String outputCommodity, float outputRatio) {

        float available = MissionCargoTracker.getAvailableQuantity(inputCommodity, cargo, reservedCommodities);
        if (available <= 0) return 0f;

        float inputToProcess = 0f;
        if (budget > 0) {
            inputToProcess = budget / inputPrice;
        }

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
                float compensationFactor = MobileRefineryHullMod.getCargoCompensationFactor(member.getStats());
                float processedCargo = effectiveCargo * compensationFactor;
                float processingPercent = MobileRefineryHullMod.getProcessingPercent(member.getStats());
                totalBudget += processedCargo * processingPercent;
            }
        }

        return totalBudget;
    }

    private float calculateFuelSpace(CargoAPI cargo) {
        float currentFuel = cargo.getFuel();
        float maxFuel = cargo.getMaxFuel();
        float maxAllowedFuel = Math.max(maxFuel * 0.8f, maxFuel - 500f);
        return Math.max(0, maxAllowedFuel - currentFuel);
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
                Map<String, Float> reservedCommodities = MissionCargoTracker.getAllReservedCommodities();
                float availableMetals = MissionCargoTracker.getAvailableQuantity("metals", cargo, reservedCommodities);
                float availableTransplutonics = MissionCargoTracker.getAvailableQuantity("rare_metals", cargo, reservedCommodities);
                float availableOre = MissionCargoTracker.getAvailableQuantity("ore", cargo, reservedCommodities);
                float availableOrganics = MissionCargoTracker.getAvailableQuantity("organics", cargo, reservedCommodities);
                float availableVolatiles = MissionCargoTracker.getAvailableQuantity("volatiles", cargo, reservedCommodities);

                float reservedMetals = cargo.getCommodityQuantity("metals") - availableMetals;
                float reservedTransplutonics = cargo.getCommodityQuantity("rare_metals") - availableTransplutonics;
                float reservedOre = cargo.getCommodityQuantity("ore") - availableOre;
                float reservedOrganics = cargo.getCommodityQuantity("organics") - availableOrganics;
                float reservedVolatiles = cargo.getCommodityQuantity("volatiles") - availableVolatiles;

                float metalValue = availableMetals * MobileRefiningPlugin.METAL_PRICE;
                float transplutonicsValue = availableTransplutonics * MobileRefiningPlugin.TRANSPLUTONICS_PRICE;

                float metalUsableForSupplies = 0f;
                float transplutonicsUsableForSupplies = 0f;

                float suppliesFromMetal = 0f;
                if (metalValue + transplutonicsValue > 0) {
                    float metalBudgetForSupplies = Math.min(budget, metalValue);
                    metalUsableForSupplies = Math.min(availableMetals, metalBudgetForSupplies / MobileRefiningPlugin.METAL_PRICE);
                    suppliesFromMetal = metalUsableForSupplies * MobileRefiningPlugin.METAL_TO_SUPPLIES_RATIO;

                    float supplyNeed = calculateSupplyNeed(fleet, 1f, cargo);
                    float remainingSupplyNeed = supplyNeed - suppliesFromMetal;
                    if (remainingSupplyNeed > 0 && availableTransplutonics > 0) {
                        float transplutonicsBudgetForSupplies = Math.min(budget - metalBudgetForSupplies, transplutonicsValue);
                        transplutonicsUsableForSupplies = Math.min(availableTransplutonics, transplutonicsBudgetForSupplies / MobileRefiningPlugin.TRANSPLUTONICS_PRICE);
                    }
                }

                float suppliesFromTransplutonics = transplutonicsUsableForSupplies * MobileRefiningPlugin.TRANSPLUTONICS_TO_SUPPLIES_RATIO;

                float processingCapacity = budget - metalUsableForSupplies * MobileRefiningPlugin.METAL_PRICE - transplutonicsUsableForSupplies * MobileRefiningPlugin.TRANSPLUTONICS_PRICE;
                if (processingCapacity < 0) processingCapacity = 0;
                float maxOrePerDay = processingCapacity / MobileRefiningPlugin.ORE_PRICE;
                float metalPerDay = maxOrePerDay * MobileRefiningPlugin.ORE_TO_METAL_RATIO;
                processingCapacity -= maxOrePerDay * MobileRefiningPlugin.ORE_PRICE;
                if (processingCapacity < 0) processingCapacity = 0;
                float maxTransplutonicOrePerDay = processingCapacity / MobileRefiningPlugin.TRANSPLUTONIC_ORE_PRICE;
                float transplutonicsPerDay = maxTransplutonicOrePerDay * MobileRefiningPlugin.TRANSPLUTONIC_ORE_TO_TRANSPLUTONICS_RATIO;

                processingCapacity -= maxTransplutonicOrePerDay * MobileRefiningPlugin.TRANSPLUTONIC_ORE_PRICE;
                if (processingCapacity < 0) processingCapacity = 0;

                float maxVolatilesPerDay = 0f;
                float fuelPerDay = 0f;
                if (fleet.isInHyperspace() && cargo.getFuel() < cargo.getMaxFuel() * 0.8f) {
                    maxVolatilesPerDay = processingCapacity / MobileRefiningPlugin.VOLATILES_PRICE;
                    fuelPerDay = maxVolatilesPerDay * MobileRefiningPlugin.VOLATILES_TO_FUEL_RATIO;
                }

                processingCapacity -= maxVolatilesPerDay * MobileRefiningPlugin.VOLATILES_PRICE;
                if (processingCapacity < 0) processingCapacity = 0;
                float maxOrganicsPerDay = processingCapacity / MobileRefiningPlugin.ORGANICS_PRICE;
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

                if (reservedMetals > 0 || reservedTransplutonics > 0 || reservedOre > 0 || reservedOrganics > 0 || reservedVolatiles > 0) {
                    tooltip.addPara("---", opad);
                    tooltip.addPara("Reserved for missions:", opad);
                    if (reservedMetals > 0) {
                        tooltip.addPara("  Metals: %s", opad, highlight, String.format("%.1f", reservedMetals));
                    }
                    if (reservedTransplutonics > 0) {
                        tooltip.addPara("  Transplutonics: %s", opad, highlight, String.format("%.1f", reservedTransplutonics));
                    }
                    if (reservedOre > 0) {
                        tooltip.addPara("  Ore: %s", opad, highlight, String.format("%.1f", reservedOre));
                    }
                    if (reservedOrganics > 0) {
                        tooltip.addPara("  Organics: %s", opad, highlight, String.format("%.1f", reservedOrganics));
                    }
                    if (reservedVolatiles > 0) {
                        tooltip.addPara("  Volatiles: %s", opad, highlight, String.format("%.1f", reservedVolatiles));
                    }
                }
            } else {
                tooltip.addPara("No ships with Mobile Refinery hullmod in fleet.", opad, highlight);
            }
        }
    }
}