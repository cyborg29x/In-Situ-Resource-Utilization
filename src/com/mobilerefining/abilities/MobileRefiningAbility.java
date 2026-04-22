package com.mobilerefining.abilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.abilities.BaseToggleAbility;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
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

        float oreRate = getTotalRefiningCapacity(fleet);
        if (oreRate <= 0) return;

        CargoAPI cargo = fleet.getCargo();
        float availableOre = cargo.getCommodityQuantity("ore");
        float availableSpace = cargo.getSpaceLeft();

        Map<String, Object> persistentData = Global.getSector().getPersistentData();
        Float oreFractionObj = (Float) persistentData.get(PERSISTENT_KEY_ORE_FRACTION);
        Float metalFractionObj = (Float) persistentData.get(PERSISTENT_KEY_METAL_FRACTION);

        float oreFraction = (oreFractionObj != null) ? oreFractionObj : 0f;
        float metalFraction = (metalFractionObj != null) ? metalFractionObj : 0f;

        oreFraction += oreRate * days;

        while (oreFraction >= 1f && availableOre >= 1f) {
            cargo.removeCommodity("ore", 1f);
            metalFraction += 1f / MobileRefiningPlugin.ORE_TO_METAL_RATIO;
            oreFraction -= 1f;
            availableOre -= 1f;
        }

        if (availableSpace >= 1f) {
            while (metalFraction >= 1f && availableSpace >= 1f) {
                cargo.addCommodity("metals", 1f);
                metalFraction -= 1f;
                availableSpace -= 1f;
            }
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

    private float getTotalRefiningCapacity(CampaignFleetAPI fleet) {
        float totalRate = 0f;

        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.getVariant().hasHullMod(HULLMOD_ID)) {
                HullSize size = member.getHullSpec().getHullSize();
                switch (size) {
                    case CAPITAL_SHIP:
                        totalRate += MobileRefiningPlugin.CAPITAL_REFINE_RATE;
                        break;
                    case CRUISER:
                        totalRate += MobileRefiningPlugin.CRUISER_REFINE_RATE;
                        break;
                    case DESTROYER:
                        totalRate += MobileRefiningPlugin.DESTROYER_REFINE_RATE;
                        break;
                    default:
                        totalRate += MobileRefiningPlugin.FRIGATE_REFINE_RATE;
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
                float metalRate = rate / MobileRefiningPlugin.ORE_TO_METAL_RATIO;
                tooltip.addPara("Output: %s metal/day", pad, highlight, String.format("%.1f", metalRate));
            } else {
                tooltip.addPara("No ships with Mobile Refinery hullmod in fleet.", pad, highlight);
            }
        }
    }
}