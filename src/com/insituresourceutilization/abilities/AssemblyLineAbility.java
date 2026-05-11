package com.insituresourceutilization.abilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.abilities.BaseToggleAbility;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.insituresourceutilization.plugins.InSituResourceUtilizationPlugin;
import com.insituresourceutilization.utils.MissionCargoTracker;
import com.insituresourceutilization.hullmods.IntegratedForgeHullMod;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class AssemblyLineAbility extends BaseToggleAbility {

    public static final String HULLMOD_ID = "integrated_forge";

    private static FleetDataCache cachedFleetData = null;

    private static class FleetDataCache {
        float processingBudget;
        float baseSupplyCost;
        float deploymentCost;
        long timestamp;
    }

    private FleetDataCache getFleetData(CampaignFleetAPI fleet) {
        long currentTimestamp = Global.getSector().getClock().getTimestamp();
        if (cachedFleetData != null && cachedFleetData.timestamp == currentTimestamp) {
            return cachedFleetData;
        }

        FleetDataCache cache = new FleetDataCache();
        cache.processingBudget = calculateProcessingBudget(fleet);
        cache.baseSupplyCost = calculateBaseSupplyCost(fleet);
        cache.deploymentCost = calculateDeploymentCost(fleet);
        cache.timestamp = currentTimestamp;

        cachedFleetData = cache;
        return cache;
    }

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

        float totalBudget = getFleetData(fleet).processingBudget;
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
                float volatilesRequired = fuelToProduce / InSituResourceUtilizationPlugin.VOLATILES_TO_FUEL_RATIO;
                float availableVolatiles = MissionCargoTracker.getAvailableQuantity("volatiles", cargo, reservedCommodities);
                float volatilesAvailableForProcessing = Math.max(0, availableVolatiles - InSituResourceUtilizationPlugin.VOLATILE_RESERVE_AMOUNT);
                float budgetByFuelSpace = volatilesRequired * InSituResourceUtilizationPlugin.VOLATILES_PRICE;
                float budgetByVolatiles = volatilesAvailableForProcessing * InSituResourceUtilizationPlugin.VOLATILES_PRICE;
                float effectiveBudget = Math.min(processingCapacity, Math.min(budgetByFuelSpace, budgetByVolatiles));

                processingCapacity -= processResource(cargo, effectiveBudget, reservedCommodities,
                    "volatiles", InSituResourceUtilizationPlugin.VOLATILES_PRICE,
                    "fuel", InSituResourceUtilizationPlugin.VOLATILES_TO_FUEL_RATIO);
            }
        }

        if (processingCapacity <= 0) return;

        float supplyNeed = calculateSupplyNeed(fleet, days, cargo);

        float maxMetalBudget = supplyNeed / InSituResourceUtilizationPlugin.METAL_TO_SUPPLIES_RATIO * InSituResourceUtilizationPlugin.METAL_PRICE;
        float budgetForMetals = Math.min(processingCapacity, maxMetalBudget);
        float metalsSpent = processResource(cargo, budgetForMetals, reservedCommodities,
            "metals", InSituResourceUtilizationPlugin.METAL_PRICE,
            "supplies", InSituResourceUtilizationPlugin.METAL_TO_SUPPLIES_RATIO);
        processingCapacity -= metalsSpent;
        if (processingCapacity <= 0) return;

        float metalSuppliesProduced = metalsSpent / InSituResourceUtilizationPlugin.METAL_PRICE * InSituResourceUtilizationPlugin.METAL_TO_SUPPLIES_RATIO;
        float remainingNeed = Math.max(0, supplyNeed - metalSuppliesProduced);
        float maxTransplutonicsBudget = remainingNeed / InSituResourceUtilizationPlugin.TRANSPLUTONICS_TO_SUPPLIES_RATIO * InSituResourceUtilizationPlugin.TRANSPLUTONICS_PRICE;
        float budgetForTransplutonics = Math.min(processingCapacity, maxTransplutonicsBudget);
        processingCapacity -= processResource(cargo, budgetForTransplutonics, reservedCommodities,
            "rare_metals", InSituResourceUtilizationPlugin.TRANSPLUTONICS_PRICE,
            "supplies", InSituResourceUtilizationPlugin.TRANSPLUTONICS_TO_SUPPLIES_RATIO);
        if (processingCapacity <= 0) return;

        float fuelSpace = calculateFuelSpace(cargo);

        if (fuelSpace > 0) {
            float volatilesRequired = fuelSpace / InSituResourceUtilizationPlugin.VOLATILES_TO_FUEL_RATIO;
            float availableVolatiles = MissionCargoTracker.getAvailableQuantity("volatiles", cargo, reservedCommodities);
            float volatilesAvailableForProcessing = Math.max(0, availableVolatiles - InSituResourceUtilizationPlugin.VOLATILE_RESERVE_AMOUNT);
            float budgetByFuelSpace = volatilesRequired * InSituResourceUtilizationPlugin.VOLATILES_PRICE;
            float budgetByVolatiles = volatilesAvailableForProcessing * InSituResourceUtilizationPlugin.VOLATILES_PRICE;
            float effectiveBudget = Math.min(processingCapacity, Math.min(budgetByFuelSpace, budgetByVolatiles));
            processingCapacity -= processResource(cargo, effectiveBudget, reservedCommodities,
                "volatiles", InSituResourceUtilizationPlugin.VOLATILES_PRICE,
                "fuel", InSituResourceUtilizationPlugin.VOLATILES_TO_FUEL_RATIO);
        }
        if (processingCapacity <= 0) return;

        processingCapacity -= processResource(cargo, processingCapacity, reservedCommodities,
            "metals", InSituResourceUtilizationPlugin.METAL_PRICE,
            "supplies", InSituResourceUtilizationPlugin.METAL_TO_SUPPLIES_RATIO);
        if (processingCapacity <= 0) return;

        processingCapacity -= processResource(cargo, processingCapacity, reservedCommodities,
            "ore", InSituResourceUtilizationPlugin.ORE_PRICE,
            "metals", InSituResourceUtilizationPlugin.ORE_TO_METAL_RATIO);
        if (processingCapacity <= 0) return;

        processingCapacity -= processResource(cargo, processingCapacity, reservedCommodities,
            "rare_ore", InSituResourceUtilizationPlugin.TRANSPLUTONIC_ORE_PRICE,
            "rare_metals", InSituResourceUtilizationPlugin.TRANSPLUTONIC_ORE_TO_TRANSPLUTONICS_RATIO);
        if (processingCapacity <= 0) return;

        processResource(cargo, processingCapacity, reservedCommodities,
            "organics", InSituResourceUtilizationPlugin.ORGANICS_PRICE,
            "domestic_goods", InSituResourceUtilizationPlugin.ORGANICS_TO_DOMESTIC_GOODS_RATIO);
    }

    private float calculateDailySupplyConsumption(CampaignFleetAPI fleet) {
        return fleet.getLogistics().getShipMaintenanceSupplyCost();
    }

    private float calculateBaseSupplyCost(CampaignFleetAPI fleet) {
        float totalSupplies = 0f;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            totalSupplies += member.getStats().getSuppliesPerMonth().getModifiedValue() / 30f;
        }
        return totalSupplies;
    }

    private float calculateDeploymentCost(CampaignFleetAPI fleet) {
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
        float militaryDeploymentCost = getFleetData(fleet).deploymentCost;
        float currentSupplies = cargo.getSupplies();
        float deploymentCostNeeded = Math.max(0, militaryDeploymentCost - currentSupplies);
        return (dailySupplyConsumption * days) + deploymentCostNeeded;
    }

    private float calculateProcessingBudget(CampaignFleetAPI fleet) {
        float totalBudget = 0f;

        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.getVariant().hasHullMod(HULLMOD_ID)) {
                float baseCargo = member.getHullSpec().getCargo();
                float effectiveCargo = member.getStats().getCargoMod().computeEffective(baseCargo);
                float compensationFactor = IntegratedForgeHullMod.getCargoCompensationFactor(member.getStats());
                float processedCargo = effectiveCargo * compensationFactor;
                float processingPercent = IntegratedForgeHullMod.getProcessingPercent(member.getStats());
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

    private String formatAmount(float amount) {
        if (amount == (int) amount) {
            return String.format("%d", (int) amount);
        } else {
            return String.format("%.1f", amount);
        }
    }

    private String formatTimeEstimate(float timeDays) {
        if (timeDays < 1f) {
            return "less than a day";
        } else if (timeDays < 7f) {
            return timeDays < 2f ? "a day" : (int)Math.ceil(timeDays) + " days";
        } else if (timeDays < 14f) {
            return "one week";
        } else if (timeDays < 30f) {
            return (int)Math.ceil(timeDays / 7f) + " weeks";
        } else if (timeDays < 360f) {
            return timeDays < 30f ? "a month" : (int)Math.ceil(timeDays / 30f) + " months";
        } else {
            return "more than a year";
        }
    }

    private String getCommodityName(String commodityId) {
        return InSituResourceUtilizationPlugin.getCommodityName(commodityId);
    }

    private static class ResourceEntry {
        float timeDays;
        String timeEstimate;
        String inputAmount;
        String inputCommodity;
        String outputAmount;
        String outputCommodity;
        String outputPerDay;

        ResourceEntry(float timeDays, String timeEstimate, String inputAmount, String inputCommodity, String outputAmount, String outputCommodity, String outputPerDay) {
            this.timeDays = timeDays;
            this.timeEstimate = timeEstimate;
            this.inputAmount = inputAmount;
            this.inputCommodity = inputCommodity;
            this.outputAmount = outputAmount;
            this.outputCommodity = outputCommodity;
            this.outputPerDay = outputPerDay;
        }
    }

    @Override
    public boolean isUsable() {
        return super.isUsable() && canActivate();
    }

    @Override
    public void pressButton() {
        if (!isActive() && !canActivate()) {
            return;
        }
        super.pressButton();
    }

    private boolean canActivate() {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return false;

        return getFleetData(fleet).processingBudget > 0f;
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
        Color gray = Misc.getGrayColor();
        Color blue = Misc.getBasePlayerColor();
        Color darkBlue = Misc.getDarkPlayerColor();

        String status = isActive() ? " (on)" : " (off)";

        LabelAPI title = tooltip.addTitle(getSpec().getName() + status);
        title.highlightLast(status);
        title.setHighlightColor(gray);

        tooltip.addPara("Enables the %s processing of certain resources into more compact and useful forms, in a way that %s their total value.", opad, highlight, "onboard", "preserves");

        CampaignFleetAPI fleet = getFleet();
        if (fleet != null) {
            float budget = getFleetData(fleet).processingBudget;
            if (budget > 0) {
                CargoAPI cargo = fleet.getCargo();
                float dailySupplyConsumption = calculateDailySupplyConsumption(fleet);
                FleetDataCache fleetData = getFleetData(fleet);
                float baseSupplyCost = fleetData.baseSupplyCost;
                float repairSupplyCost = dailySupplyConsumption - baseSupplyCost;
                float militaryDeploymentCost = fleetData.deploymentCost;
                float fuelPerDay = fleet.isInHyperspace() ? Misc.getFuelPerDay(fleet, fleet.getCurrBurnLevel()) : 0f;

                tooltip.addSectionHeading("Fleet statistics", blue, darkBlue, Alignment.MID, opad);

                tooltip.addPara("Processing capacity of %s credits per day.", opad, highlight, String.valueOf((int)(float)Math.floor(budget)));

                if (repairSupplyCost > 0) {
                    tooltip.addPara("Supply demand of %s per day with additional %s from ongoing repairs. %s supplies will be stockpiled for ship deployment.", opad, highlight, String.format("%.1f", dailySupplyConsumption), String.format("%.1f", repairSupplyCost), String.valueOf((int)(float)Math.floor(militaryDeploymentCost)));
                } else {
                    tooltip.addPara("Supply demand of %s per day. %s supplies will be stockpiled for ship deployment.", opad, highlight, String.format("%.1f", dailySupplyConsumption), String.valueOf((int)(float)Math.floor(militaryDeploymentCost)));
                }

                if (fuelPerDay > 0) {
                    tooltip.addPara("Fuel usage of %s per day.", opad, highlight, String.format("%.1f", fuelPerDay));
                }

                float availableSupplies = cargo.getSupplies();
                float repairCost = fleet.getLogistics().getTotalRepairAndRecoverySupplyCost();
                availableSupplies = Math.max(0, availableSupplies - repairCost);
                float supplyDays = baseSupplyCost > 0 ? availableSupplies / baseSupplyCost : 0f;
                float fuelDays = fuelPerDay > 0 ? cargo.getFuel() / fuelPerDay : 0f;
                if (supplyDays > 0) {
                    String supplyEstimate = formatTimeEstimate(supplyDays);
                    if (fuelDays > 0) {
                        String fuelEstimate = formatTimeEstimate(fuelDays);
                        String supplyRaw = formatTimeEstimate(supplyDays);
                        String fuelRaw = formatTimeEstimate(fuelDays);
                        if (supplyRaw.equals(fuelRaw)) {
                            tooltip.addPara("Fleet carries %s of supplies and fuel.", opad, highlight, supplyEstimate);
                        } else {
                            tooltip.addPara("Fleet carries %s of supplies and %s of fuel.", opad, highlight, supplyEstimate, fuelEstimate);
                        }
                    } else {
                        tooltip.addPara("Fleet carries %s of supplies.", opad, highlight, supplyEstimate);
                    }
                }

                tooltip.addSectionHeading("Resource processing", blue, darkBlue, Alignment.MID, opad);

                Map<String, Float> reservedCommodities = MissionCargoTracker.getAllReservedCommodities();
                float availableVolatiles = MissionCargoTracker.getAvailableQuantity("volatiles", cargo, reservedCommodities);
                float availableMetals = MissionCargoTracker.getAvailableQuantity("metals", cargo, reservedCommodities);
                float availableTransplutonics = MissionCargoTracker.getAvailableQuantity("rare_metals", cargo, reservedCommodities);
                float availableOre = MissionCargoTracker.getAvailableQuantity("ore", cargo, reservedCommodities);
                float availableOrganics = MissionCargoTracker.getAvailableQuantity("organics", cargo, reservedCommodities);
                float transOreAvailable = MissionCargoTracker.getAvailableQuantity("rare_ore", cargo, reservedCommodities);

                float timeIncrement = Global.getSector().getClock().convertToDays(1f);
                float remainingCapacity = budget;
                List<ResourceEntry> entries = new ArrayList<>();

                float fuelSpace = calculateFuelSpace(cargo);
                float volatilesAvailableForProcessing = Math.max(0, availableVolatiles - InSituResourceUtilizationPlugin.VOLATILE_RESERVE_AMOUNT);

                if (fleet.isInHyperspace() && cargo.getFuel() < cargo.getMaxFuel() * 0.8f && fuelSpace > 0 && volatilesAvailableForProcessing > 0 && remainingCapacity > 0) {
                    float volatilesBudget = Math.min(remainingCapacity, volatilesAvailableForProcessing * InSituResourceUtilizationPlugin.VOLATILES_PRICE);
                    float dailyRate = remainingCapacity / InSituResourceUtilizationPlugin.VOLATILES_PRICE;
                    float timeDays = dailyRate > 0 ? volatilesAvailableForProcessing / dailyRate : 0f;
                    String timeEstimate = formatTimeEstimate(timeDays);
                    String inputAmount = String.valueOf((int)(float)Math.floor(volatilesAvailableForProcessing));
                    String outputAmount = String.valueOf((int)(float)Math.floor(volatilesAvailableForProcessing * InSituResourceUtilizationPlugin.VOLATILES_TO_FUEL_RATIO));
                    String inputCommodity = getCommodityName("volatiles");
                    String outputCommodity = getCommodityName("fuel");
                    String outputPerDay = formatAmount(timeDays >= 1f ? dailyRate * InSituResourceUtilizationPlugin.VOLATILES_TO_FUEL_RATIO : 0f);
                    entries.add(new ResourceEntry(timeDays, timeEstimate, inputAmount, inputCommodity, outputAmount, outputCommodity, outputPerDay));
                    remainingCapacity -= volatilesBudget;
                }

                float volatilesAfterHyperspace = Math.max(0, availableVolatiles - InSituResourceUtilizationPlugin.VOLATILE_RESERVE_AMOUNT);

                float remainingCapacityBeforeMetals = remainingCapacity;
                float metalBudget = Math.min(remainingCapacity, availableMetals * InSituResourceUtilizationPlugin.METAL_PRICE * timeIncrement);
                if (availableMetals > 0 && remainingCapacity > 0 && metalBudget < availableMetals * InSituResourceUtilizationPlugin.METAL_PRICE) {
                    float dailyRate = remainingCapacity / InSituResourceUtilizationPlugin.METAL_PRICE;
                    float timeDays = dailyRate > 0 ? availableMetals / dailyRate : 0f;
                    String timeEstimate = formatTimeEstimate(timeDays);
                    String inputAmount = String.valueOf((int)(float)Math.floor(availableMetals));
                    String outputAmount = String.valueOf((int)(float)Math.floor(availableMetals * InSituResourceUtilizationPlugin.METAL_TO_SUPPLIES_RATIO));
                    String inputCommodity = getCommodityName("metals");
                    String outputCommodity = getCommodityName("supplies");
                    String outputPerDay = formatAmount(timeDays >= 1f ? dailyRate * InSituResourceUtilizationPlugin.METAL_TO_SUPPLIES_RATIO : 0f);
                    entries.add(new ResourceEntry(timeDays, timeEstimate, inputAmount, inputCommodity, outputAmount, outputCommodity, outputPerDay));
                    remainingCapacity -= metalBudget;
                }

                float capacityAfterMetals = remainingCapacity;
                float metalBudgetForTransCalc = 0f;
                if (availableMetals > 0 && remainingCapacityBeforeMetals > 0 && metalBudget < availableMetals * InSituResourceUtilizationPlugin.METAL_PRICE) {
                    metalBudgetForTransCalc = Math.min(remainingCapacityBeforeMetals, availableMetals * InSituResourceUtilizationPlugin.METAL_PRICE);
                    capacityAfterMetals = remainingCapacityBeforeMetals - metalBudgetForTransCalc;
                }

                float supplyNeed = calculateSupplyNeed(fleet, 1f, cargo);
                float metalSuppliesProduced = metalBudgetForTransCalc / InSituResourceUtilizationPlugin.METAL_PRICE * InSituResourceUtilizationPlugin.METAL_TO_SUPPLIES_RATIO;
                float remainingNeed = Math.max(0, supplyNeed - metalSuppliesProduced);
                float maxTransBudget = remainingNeed / InSituResourceUtilizationPlugin.TRANSPLUTONICS_TO_SUPPLIES_RATIO * InSituResourceUtilizationPlugin.TRANSPLUTONICS_PRICE;
                float transBudget = Math.min(capacityAfterMetals, Math.min(availableTransplutonics * InSituResourceUtilizationPlugin.TRANSPLUTONICS_PRICE, maxTransBudget));

                if (availableTransplutonics > 0 && capacityAfterMetals > 0 && transBudget < availableTransplutonics * InSituResourceUtilizationPlugin.TRANSPLUTONICS_PRICE * timeIncrement) {
                    float dailyRate = transBudget / InSituResourceUtilizationPlugin.TRANSPLUTONICS_PRICE;
                    float timeDays = dailyRate > 0 ? availableTransplutonics / dailyRate : 0f;
                    String timeEstimate = formatTimeEstimate(timeDays);
                    String inputAmount = String.valueOf((int)(float)Math.floor(availableTransplutonics));
                    String outputAmount = String.valueOf((int)(float)Math.floor(availableTransplutonics * InSituResourceUtilizationPlugin.TRANSPLUTONICS_TO_SUPPLIES_RATIO));
                    String inputCommodity = getCommodityName("rare_metals");
                    String outputCommodity = getCommodityName("supplies");
                    String outputPerDay = formatAmount(timeDays >= 1f ? dailyRate * InSituResourceUtilizationPlugin.TRANSPLUTONICS_TO_SUPPLIES_RATIO : 0f);
                    entries.add(new ResourceEntry(timeDays, timeEstimate, inputAmount, inputCommodity, outputAmount, outputCommodity, outputPerDay));
                    remainingCapacity -= transBudget;
                }

                float fuelSpaceUncapped = calculateFuelSpace(cargo);
                float inputToProcess = remainingCapacity > 0 ? remainingCapacity / InSituResourceUtilizationPlugin.VOLATILES_PRICE : 0f;
                float volatilesForUncapped = Math.min(inputToProcess, volatilesAfterHyperspace);
                if (fuelSpaceUncapped > 0 && volatilesForUncapped > 0) {
                    float effectiveBudget = volatilesForUncapped * InSituResourceUtilizationPlugin.VOLATILES_PRICE;
                    float outputProduced = volatilesAfterHyperspace * InSituResourceUtilizationPlugin.VOLATILES_TO_FUEL_RATIO;
                    float dailyRate = remainingCapacity > 0 ? remainingCapacity / InSituResourceUtilizationPlugin.VOLATILES_PRICE : 0f;
                    float timeDays = dailyRate > 0 ? volatilesAfterHyperspace / dailyRate : 0f;
                    String timeEstimate = formatTimeEstimate(timeDays);
                    String inputAmount = String.valueOf((int)(float)Math.floor(volatilesAfterHyperspace));
                    String outputAmount = String.valueOf((int)(float)Math.floor(outputProduced));
                    String inputCommodity = getCommodityName("volatiles");
                    String outputCommodity = getCommodityName("fuel");
                    String outputPerDay = formatAmount(timeDays >= 1f ? dailyRate * InSituResourceUtilizationPlugin.VOLATILES_TO_FUEL_RATIO : 0f);
                    entries.add(new ResourceEntry(timeDays, timeEstimate, inputAmount, inputCommodity, outputAmount, outputCommodity, outputPerDay));
                    remainingCapacity -= effectiveBudget;
                }

                if (availableOre > 0 && remainingCapacity > 0) {
                    float oreBudget = Math.min(remainingCapacity, availableOre * InSituResourceUtilizationPlugin.ORE_PRICE);
                    float dailyRate = remainingCapacity / InSituResourceUtilizationPlugin.ORE_PRICE;
                    float timeDays = dailyRate > 0 ? availableOre / dailyRate : 0f;
                    String timeEstimate = formatTimeEstimate(timeDays);
                    String inputAmount = String.valueOf((int)(float)Math.floor(availableOre));
                    String outputAmount = String.valueOf((int)(float)Math.floor(availableOre * InSituResourceUtilizationPlugin.ORE_TO_METAL_RATIO));
                    String inputCommodity = getCommodityName("ore");
                    String outputCommodity = getCommodityName("metals");
                    String outputPerDay = formatAmount(timeDays >= 1f ? dailyRate * InSituResourceUtilizationPlugin.ORE_TO_METAL_RATIO : 0f);
                    entries.add(new ResourceEntry(timeDays, timeEstimate, inputAmount, inputCommodity, outputAmount, outputCommodity, outputPerDay));
                    remainingCapacity -= oreBudget;
                }

                if (transOreAvailable > 0 && remainingCapacity > 0) {
                    float transOreBudget = Math.min(remainingCapacity, transOreAvailable * InSituResourceUtilizationPlugin.TRANSPLUTONIC_ORE_PRICE);
                    float dailyRate = remainingCapacity / InSituResourceUtilizationPlugin.TRANSPLUTONIC_ORE_PRICE;
                    float timeDays = dailyRate > 0 ? transOreAvailable / dailyRate : 0f;
                    String timeEstimate = formatTimeEstimate(timeDays);
                    String inputAmount = String.valueOf((int)(float)Math.floor(transOreAvailable));
                    String outputAmount = String.valueOf((int)(float)Math.floor(transOreAvailable * InSituResourceUtilizationPlugin.TRANSPLUTONIC_ORE_TO_TRANSPLUTONICS_RATIO));
                    String inputCommodity = getCommodityName("rare_ore");
                    String outputCommodity = getCommodityName("rare_metals");
                    String outputPerDay = formatAmount(timeDays >= 1f ? dailyRate * InSituResourceUtilizationPlugin.TRANSPLUTONIC_ORE_TO_TRANSPLUTONICS_RATIO : 0f);
                    entries.add(new ResourceEntry(timeDays, timeEstimate, inputAmount, inputCommodity, outputAmount, outputCommodity, outputPerDay));
                    remainingCapacity -= transOreBudget;
                }

                if (availableOrganics > 0 && remainingCapacity > 0) {
                    float organicsBudget = Math.min(remainingCapacity, availableOrganics * InSituResourceUtilizationPlugin.ORGANICS_PRICE);
                    float dailyRate = remainingCapacity / InSituResourceUtilizationPlugin.ORGANICS_PRICE;
                    float timeDays = dailyRate > 0 ? availableOrganics / dailyRate : 0f;
                    String timeEstimate = formatTimeEstimate(timeDays);
                    String inputAmount = String.valueOf((int)(float)Math.floor(availableOrganics));
                    String outputAmount = String.valueOf((int)(float)Math.floor(availableOrganics * InSituResourceUtilizationPlugin.ORGANICS_TO_DOMESTIC_GOODS_RATIO));
                    String inputCommodity = getCommodityName("organics");
                    String outputCommodity = getCommodityName("domestic_goods");
                    String outputPerDay = formatAmount(timeDays >= 1f ? dailyRate * InSituResourceUtilizationPlugin.ORGANICS_TO_DOMESTIC_GOODS_RATIO : 0f);
                    entries.add(new ResourceEntry(timeDays, timeEstimate, inputAmount, inputCommodity, outputAmount, outputCommodity, outputPerDay));
                    remainingCapacity -= organicsBudget;
                }

                Collections.sort(entries, Comparator.comparingDouble(e -> e.timeDays));

                for (ResourceEntry entry : entries) {
                    String sentence;
                    if (entry.timeDays >= 1f) {
                        sentence = "In %s, %s " + entry.inputCommodity.toLowerCase() + " will process into %s " + entry.outputCommodity.toLowerCase() + ", at a rate of %s " + entry.outputCommodity.toLowerCase() + " per day.";
                        tooltip.addPara(sentence, opad, highlight, entry.timeEstimate, entry.inputAmount, entry.outputAmount, entry.outputPerDay);
                    } else {
                        sentence = "In %s, %s " + entry.inputCommodity.toLowerCase() + " will process into %s " + entry.outputCommodity.toLowerCase() + ".";
                        tooltip.addPara(sentence, opad, highlight, entry.timeEstimate, entry.inputAmount, entry.outputAmount);
                    }
                }

                float reservedMetals = cargo.getCommodityQuantity("metals") - availableMetals;
                float reservedTransplutonics = cargo.getCommodityQuantity("rare_metals") - availableTransplutonics;
                float reservedOre = cargo.getCommodityQuantity("ore") - availableOre;
                float reservedOrganics = cargo.getCommodityQuantity("organics") - availableOrganics;
                float reservedVolatiles = cargo.getCommodityQuantity("volatiles") - availableVolatiles
                    + Math.min(InSituResourceUtilizationPlugin.VOLATILE_RESERVE_AMOUNT, availableVolatiles);
                float reservedTransplutonicOre = cargo.getCommodityQuantity("rare_ore") - transOreAvailable;

                if (reservedMetals > 0 || reservedTransplutonics > 0 || reservedOre > 0 || reservedOrganics > 0 || reservedVolatiles > 0 || reservedTransplutonicOre > 0) {
                    tooltip.addSectionHeading("Reserved commodities", blue, darkBlue, Alignment.MID, opad);

                    List<Object[]> reservedList = new ArrayList<>();
                    if (reservedMetals > 0) reservedList.add(new Object[]{"Metals", reservedMetals, (float)reservedMetals * InSituResourceUtilizationPlugin.METAL_PRICE});
                    if (reservedTransplutonics > 0) reservedList.add(new Object[]{"Transplutonics", reservedTransplutonics, (float)reservedTransplutonics * InSituResourceUtilizationPlugin.TRANSPLUTONICS_PRICE});
                    if (reservedOre > 0) reservedList.add(new Object[]{"Ore", reservedOre, (float)reservedOre * InSituResourceUtilizationPlugin.ORE_PRICE});
                    if (reservedOrganics > 0) reservedList.add(new Object[]{"Organics", reservedOrganics, (float)reservedOrganics * InSituResourceUtilizationPlugin.ORGANICS_PRICE});
                    if (reservedVolatiles > 0) reservedList.add(new Object[]{"Volatiles", reservedVolatiles, (float)reservedVolatiles * InSituResourceUtilizationPlugin.VOLATILES_PRICE});
                    if (reservedTransplutonicOre > 0) reservedList.add(new Object[]{"Transplutonic Ore", reservedTransplutonicOre, (float)reservedTransplutonicOre * InSituResourceUtilizationPlugin.TRANSPLUTONIC_ORE_PRICE});

                    Collections.sort(reservedList, (a, b) -> Float.compare((Float)b[2], (Float)a[2]));

                    for (Object[] entry : reservedList) {
                        String name = (String)entry[0];
                        name = name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
                        float amount = (Float)entry[1];
                        tooltip.addPara(name + ": %s.", opad, highlight, String.valueOf((int)(float)Math.floor(amount)));
                    }

                    tooltip.addPara("*The listed resources are reserved for active missions or non-processing use and will not be consumed.", gray, opad);
                }
            } else {
                tooltip.addPara("Your fleet is not currently capable of processing resources.", Misc.getNegativeHighlightColor(), opad);
            }
        }
        addIncompatibleToTooltip(tooltip, expanded);
    }
}