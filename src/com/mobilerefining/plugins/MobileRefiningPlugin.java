package com.mobilerefining.plugins;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import org.json.JSONObject;

public class MobileRefiningPlugin extends BaseModPlugin {

    public static final String ABILITY_ID = "mobile_refining";

    public static float ORE_TO_METAL_RATIO = 3f;
    public static float CAPITAL_REFINE_RATE = 10f;
    public static float CRUISER_REFINE_RATE = 5f;
    public static float DESTROYER_REFINE_RATE = 2f;
    public static float FRIGATE_REFINE_RATE = 1f;
    public static int HULLMOD_COST = 40;

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
            HULLMOD_COST = config.optInt("hullmodCost", HULLMOD_COST);

            try {
                JSONObject rates = config.getJSONObject("refineRates");
                if (rates != null) {
                    CAPITAL_REFINE_RATE = (float) rates.optDouble("CAPITAL", CAPITAL_REFINE_RATE);
                    CRUISER_REFINE_RATE = (float) rates.optDouble("CRUISER", CRUISER_REFINE_RATE);
                    DESTROYER_REFINE_RATE = (float) rates.optDouble("DESTROYER", DESTROYER_REFINE_RATE);
                    FRIGATE_REFINE_RATE = (float) rates.optDouble("FRIGATE", FRIGATE_REFINE_RATE);
                }
            } catch (org.json.JSONException ex) {
                Global.getLogger(MobileRefiningPlugin.class).warn("Failed to load refineRates: " + ex.getMessage());
            }
        }
    }

    private void grantAbilityToPlayer() {
        if (!Global.getSector().getPlayerFleet().hasAbility(ABILITY_ID)) {
            Global.getSector().getCharacterData().addAbility(ABILITY_ID);
        }
    }
}