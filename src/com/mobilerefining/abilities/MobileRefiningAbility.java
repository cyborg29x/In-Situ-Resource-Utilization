package com.mobilerefining.abilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.abilities.BaseToggleAbility;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.mobilerefining.plugins.MobileRefiningPlugin;
import com.mobilerefining.utils.MissionCargoTracker;
import com.mobilerefining.hullmods.MobileRefineryHullMod;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
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
        } else if (timeDays < 365f) {
            return timeDays < 60f ? "a month" : (int)Math.ceil(timeDays / 30f) + " months";
        } else {
            return "more than a year";
        }
    }

    private String getCommodityName(String commodityId) {
        CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(commodityId);
        return spec != null ? spec.getName() : commodityId;
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
        Color gray = Misc.getGrayColor();
        Color blue = Misc.getBasePlayerColor();
        Color darkBlue = Misc.getDarkPlayerColor();

        String status = isActive() ? " (on)" : " (off)";

        LabelAPI title = tooltip.addTitle(getSpec().getName() + status);
        title.highlightLast(status);
        title.setHighlightColor(gray);

        tooltip.addPara("Enables the onboard processing of certain resources into more compact and useful forms, in a way that preserves their total value.", opad);

        CampaignFleetAPI fleet = getFleet();
        if (fleet != null) {
            float budget = getTotalProcessingBudget(fleet);
            if (budget > 0) {
                float dailySupplyConsumption = calculateDailySupplyConsumption(fleet);
                float baseSupplyCost = calculateBaseSupplyConsumption(fleet);
                float repairSupplyCost = dailySupplyConsumption - baseSupplyCost;
                float militaryDeploymentCost = calculateMilitaryShipDeploymentSupplyCost(fleet);
                float fuelPerDay = fleet.isInHyperspace() ? Misc.getFuelPerDay(fleet, fleet.getCurrBurnLevel()) : 0f;
                float burnLevel = fleet.getCurrBurnLevel();

                tooltip.addSectionHeading("Fleet statistics", blue, darkBlue, Alignment.MID, opad);

                tooltip.addPara("Processing capacity of %s credits per day.", opad, highlight, String.format("%.1f", budget));

                if (repairSupplyCost > 0) {
                    tooltip.addPara("Supply demand of %s units per day (+ %s repairs).", opad, highlight, String.format("%.1f", dailySupplyConsumption), String.format("%.1f", repairSupplyCost));
                } else {
                    tooltip.addPara("Supply demand of %s units per day.", opad, highlight, String.format("%.1f", dailySupplyConsumption));
                }

                tooltip.addPara("A total of %s units of supplies will be stockpiled for military ship deployment.", opad, highlight, String.format("%.1f", militaryDeploymentCost));

                if (fuelPerDay > 0) {
                    tooltip.addPara("Fuel usage of %s units per day at a burn level of %s.", opad, highlight, String.format("%.1f", fuelPerDay), String.valueOf(burnLevel));
                }

                tooltip.addSectionHeading("Resource processing", blue, darkBlue, Alignment.MID, opad);

                CargoAPI cargo = fleet.getCargo();
                Map<String, Float> reservedCommodities = MissionCargoTracker.getAllReservedCommodities();
                float availableVolatiles = MissionCargoTracker.getAvailableQuantity("volatiles", cargo, reservedCommodities);
                float availableMetals = MissionCargoTracker.getAvailableQuantity("metals", cargo, reservedCommodities);
                float availableTransplutonics = MissionCargoTracker.getAvailableQuantity("rare_metals", cargo, reservedCommodities);
                float availableOre = MissionCargoTracker.getAvailableQuantity("ore", cargo, reservedCommodities);
                float availableOrganics = MissionCargoTracker.getAvailableQuantity("organics", cargo, reservedCommodities);
                float transOreAvailable = MissionCargoTracker.getAvailableQuantity("rare_ore", cargo, reservedCommodities);

                float remainingCapacity = budget;
                List<ResourceEntry> entries = new ArrayList<>();

                if (fleet.isInHyperspace() && cargo.getFuel() < cargo.getMaxFuel() * 0.8f && availableVolatiles > 0 && remainingCapacity > 0) {
                    float volatilesBudget = Math.min(remainingCapacity, availableVolatiles * MobileRefiningPlugin.VOLATILES_PRICE);
                    float dailyRate = remainingCapacity / MobileRefiningPlugin.VOLATILES_PRICE;
                    float timeDays = dailyRate > 0 ? availableVolatiles / dailyRate : 0f;
                    String timeEstimate = formatTimeEstimate(timeDays);
                    String inputAmount = formatAmount(availableVolatiles);
                    String outputAmount = formatAmount(availableVolatiles * MobileRefiningPlugin.VOLATILES_TO_FUEL_RATIO);
                    String inputCommodity = getCommodityName("volatiles");
                    String outputCommodity = getCommodityName("fuel");
                    String outputPerDay = formatAmount(timeDays >= 1f ? dailyRate * MobileRefiningPlugin.VOLATILES_TO_FUEL_RATIO : 0f);
                    entries.add(new ResourceEntry(timeDays, timeEstimate, inputAmount, inputCommodity, outputAmount, outputCommodity, outputPerDay));
                    remainingCapacity -= volatilesBudget;
                }

                if (availableMetals > 0 && remainingCapacity > 0) {
                    float metalBudget = Math.min(remainingCapacity, availableMetals * MobileRefiningPlugin.METAL_PRICE);
                    float dailyRate = remainingCapacity / MobileRefiningPlugin.METAL_PRICE;
                    float timeDays = dailyRate > 0 ? availableMetals / dailyRate : 0f;
                    String timeEstimate = formatTimeEstimate(timeDays);
                    String inputAmount = formatAmount(availableMetals);
                    String outputAmount = formatAmount(availableMetals * MobileRefiningPlugin.METAL_TO_SUPPLIES_RATIO);
                    String inputCommodity = getCommodityName("metals");
                    String outputCommodity = getCommodityName("supplies");
                    String outputPerDay = formatAmount(timeDays >= 1f ? dailyRate * MobileRefiningPlugin.METAL_TO_SUPPLIES_RATIO : 0f);
                    entries.add(new ResourceEntry(timeDays, timeEstimate, inputAmount, inputCommodity, outputAmount, outputCommodity, outputPerDay));
                    remainingCapacity -= metalBudget;
                }

                if (availableTransplutonics > 0 && remainingCapacity > 0) {
                    float transBudget = Math.min(remainingCapacity, availableTransplutonics * MobileRefiningPlugin.TRANSPLUTONICS_PRICE);
                    float dailyRate = remainingCapacity / MobileRefiningPlugin.TRANSPLUTONICS_PRICE;
                    float timeDays = dailyRate > 0 ? availableTransplutonics / dailyRate : 0f;
                    String timeEstimate = formatTimeEstimate(timeDays);
                    String inputAmount = formatAmount(availableTransplutonics);
                    String outputAmount = formatAmount(availableTransplutonics * MobileRefiningPlugin.TRANSPLUTONICS_TO_SUPPLIES_RATIO);
                    String inputCommodity = getCommodityName("rare_metals");
                    String outputCommodity = getCommodityName("supplies");
                    String outputPerDay = formatAmount(timeDays >= 1f ? dailyRate * MobileRefiningPlugin.TRANSPLUTONICS_TO_SUPPLIES_RATIO : 0f);
                    entries.add(new ResourceEntry(timeDays, timeEstimate, inputAmount, inputCommodity, outputAmount, outputCommodity, outputPerDay));
                    remainingCapacity -= transBudget;
                }

                if (availableOre > 0 && remainingCapacity > 0) {
                    float oreBudget = Math.min(remainingCapacity, availableOre * MobileRefiningPlugin.ORE_PRICE);
                    float dailyRate = remainingCapacity / MobileRefiningPlugin.ORE_PRICE;
                    float timeDays = dailyRate > 0 ? availableOre / dailyRate : 0f;
                    String timeEstimate = formatTimeEstimate(timeDays);
                    String inputAmount = formatAmount(availableOre);
                    String outputAmount = formatAmount(availableOre * MobileRefiningPlugin.ORE_TO_METAL_RATIO);
                    String inputCommodity = getCommodityName("ore");
                    String outputCommodity = getCommodityName("metals");
                    String outputPerDay = formatAmount(timeDays >= 1f ? dailyRate * MobileRefiningPlugin.ORE_TO_METAL_RATIO : 0f);
                    entries.add(new ResourceEntry(timeDays, timeEstimate, inputAmount, inputCommodity, outputAmount, outputCommodity, outputPerDay));
                    remainingCapacity -= oreBudget;
                }

                if (transOreAvailable > 0 && remainingCapacity > 0) {
                    float transOreBudget = Math.min(remainingCapacity, transOreAvailable * MobileRefiningPlugin.TRANSPLUTONIC_ORE_PRICE);
                    float dailyRate = remainingCapacity / MobileRefiningPlugin.TRANSPLUTONIC_ORE_PRICE;
                    float timeDays = dailyRate > 0 ? transOreAvailable / dailyRate : 0f;
                    String timeEstimate = formatTimeEstimate(timeDays);
                    String inputAmount = formatAmount(transOreAvailable);
                    String outputAmount = formatAmount(transOreAvailable * MobileRefiningPlugin.TRANSPLUTONIC_ORE_TO_TRANSPLUTONICS_RATIO);
                    String inputCommodity = getCommodityName("rare_ore");
                    String outputCommodity = getCommodityName("rare_metals");
                    String outputPerDay = formatAmount(timeDays >= 1f ? dailyRate * MobileRefiningPlugin.TRANSPLUTONIC_ORE_TO_TRANSPLUTONICS_RATIO : 0f);
                    entries.add(new ResourceEntry(timeDays, timeEstimate, inputAmount, inputCommodity, outputAmount, outputCommodity, outputPerDay));
                    remainingCapacity -= transOreBudget;
                }

                if (availableOrganics > 0 && remainingCapacity > 0) {
                    float organicsBudget = Math.min(remainingCapacity, availableOrganics * MobileRefiningPlugin.ORGANICS_PRICE);
                    float dailyRate = remainingCapacity / MobileRefiningPlugin.ORGANICS_PRICE;
                    float timeDays = dailyRate > 0 ? availableOrganics / dailyRate : 0f;
                    String timeEstimate = formatTimeEstimate(timeDays);
                    String inputAmount = formatAmount(availableOrganics);
                    String outputAmount = formatAmount(availableOrganics * MobileRefiningPlugin.ORGANICS_TO_DOMESTIC_GOODS_RATIO);
                    String inputCommodity = getCommodityName("organics");
                    String outputCommodity = getCommodityName("domestic_goods");
                    String outputPerDay = formatAmount(timeDays >= 1f ? dailyRate * MobileRefiningPlugin.ORGANICS_TO_DOMESTIC_GOODS_RATIO : 0f);
                    entries.add(new ResourceEntry(timeDays, timeEstimate, inputAmount, inputCommodity, outputAmount, outputCommodity, outputPerDay));
                    remainingCapacity -= organicsBudget;
                }

                Collections.sort(entries, Comparator.comparingDouble(e -> e.timeDays));

                for (ResourceEntry entry : entries) {
                    String sentence;
                    if (entry.timeDays >= 1f) {
                        sentence = "In %s, %s " + entry.inputCommodity.toLowerCase() + " will process into %s " + entry.outputCommodity.toLowerCase() + ", at a rate of %s per day.";
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
                float reservedVolatiles = cargo.getCommodityQuantity("volatiles") - availableVolatiles;

                if (reservedMetals > 0 || reservedTransplutonics > 0 || reservedOre > 0 || reservedOrganics > 0 || reservedVolatiles > 0) {
                    tooltip.addSectionHeading("Reserved commodities", blue, darkBlue, Alignment.MID, opad);
                    tooltip.addPara("The following resources are reserved for active missions:", opad);
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