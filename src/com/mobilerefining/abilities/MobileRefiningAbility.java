package com.mobilerefining.abilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.abilities.BaseToggleAbility;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import java.awt.Color;

public class MobileRefiningAbility extends BaseToggleAbility {

    public static final String HULLMOD_ID = "mobile_refinery";

    private static final float ORE_TO_METAL_RATIO = 3f;
    private static final float CAPITAL_REFINE_RATE = 10f;
    private static final float CRUISER_REFINE_RATE = 5f;
    private static final float DESTROYER_REFINE_RATE = 2f;
    private static final float FRIGATE_REFINE_RATE = 1f;

    @Override
    protected void activateImpl() {
    }

    @Override
    protected void applyEffect(float amount, float level) {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return;

        float days = Global.getSector().getClock().convertToDays(amount);
        if (days <= 0) return;

        float oreRate = getTotalRefiningCapacity(fleet);
        if (oreRate <= 0) return;

        CargoAPI cargo = fleet.getCargo();
        float availableOre = cargo.getCommodityQuantity("ore");

        float oreToProcess = Math.min(oreRate * days, availableOre);
        float metalProduced = oreToProcess / ORE_TO_METAL_RATIO;

        float maxMetalFromOre = availableOre / ORE_TO_METAL_RATIO;
        float maxMetalSpace = cargo.getSpaceLeft();

        metalProduced = Math.min(metalProduced, maxMetalSpace);
        metalProduced = Math.min(metalProduced, maxMetalFromOre);

        float oreNeeded = metalProduced * ORE_TO_METAL_RATIO;
        if (oreNeeded > 0 && metalProduced > 0) {
            cargo.removeCommodity("ore", oreNeeded);
            cargo.addCommodity("metals", metalProduced);
        }
    }

    @Override
    protected void deactivateImpl() {
    }

    @Override
    protected void cleanupImpl() {
    }

    private float getTotalRefiningCapacity(CampaignFleetAPI fleet) {
        float totalRate = 0f;

        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.getVariant().hasHullMod(HULLMOD_ID)) {
                HullSize size = member.getHullSpec().getHullSize();
                switch (size) {
                    case CAPITAL_SHIP:
                        totalRate += CAPITAL_REFINE_RATE;
                        break;
                    case CRUISER:
                        totalRate += CRUISER_REFINE_RATE;
                        break;
                    case DESTROYER:
                        totalRate += DESTROYER_REFINE_RATE;
                        break;
                    default:
                        totalRate += FRIGATE_REFINE_RATE;
                        break;
                }
            }
        }

        return totalRate;
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

        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.getVariant().hasHullMod(HULLMOD_ID)) {
                return true;
            }
        }
        return false;
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
            float rate = getTotalRefiningCapacity(fleet);
            if (rate > 0) {
                tooltip.addPara("Refining capacity: %s ore/day", pad, highlight, String.format("%.1f", rate));
                float metalRate = rate / ORE_TO_METAL_RATIO;
                tooltip.addPara("Output: %s metal/day", pad, highlight, String.format("%.1f", metalRate));
            } else {
                tooltip.addPara("No ships with Mobile Refinery hullmod in fleet.", pad, highlight);
            }
        }
    }
}