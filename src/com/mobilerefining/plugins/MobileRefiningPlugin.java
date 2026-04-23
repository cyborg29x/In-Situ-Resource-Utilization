package com.mobilerefining.plugins;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import org.json.JSONObject;

public class MobileRefiningPlugin extends BaseModPlugin {

    public static final String ABILITY_ID = "mobile_refining";

    public static float ORE_TO_METAL_RATIO = 3f;
    public static float BUDGET_PERCENT = 0.10f;
    public static float CARGO_SPACE_TAKEN = 0.10f;
    public static final int ORE_PRICE = 10;
    public static final int METAL_PRICE = 30;
    public static final int HULLMOD_COST = 40;
    public static final float CARGO_COMPENSATION_FACTOR = 1f / (1f - CARGO_SPACE_TAKEN);

    @Override
    public void onGameLoad(boolean newGame) {
        grantAbilityToPlayer();
    }

    @Override
    public void onEnabled(boolean wasEnabledBefore) {
        try {
            loadConfig();
        } catch (Exception ex) {
            Global.getLogger(MobileRefiningPlugin.class).warn("Failed to load settings.json, using defaults");
        }
    }

    private void loadConfig() throws Exception {
        JSONObject config = Global.getSettings().loadJSON("data/config/settings.json", "mobile_refining");

        if (config != null) {
            ORE_TO_METAL_RATIO = (float) config.optDouble("oreToMetalRatio", ORE_TO_METAL_RATIO);
            BUDGET_PERCENT = (float) config.optDouble("budgetPercent", BUDGET_PERCENT);
        }
    }

    private void grantAbilityToPlayer() {
        if (!Global.getSector().getPlayerFleet().hasAbility(ABILITY_ID)) {
            Global.getSector().getCharacterData().addAbility(ABILITY_ID);
        }
    }
}