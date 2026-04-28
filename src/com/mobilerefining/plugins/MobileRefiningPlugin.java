package com.mobilerefining.plugins;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import org.json.JSONObject;

public class MobileRefiningPlugin extends BaseModPlugin {

    public static final String ABILITY_ID = "mobile_refining";

    public static int ORE_PRICE = 10;
    public static int METAL_PRICE = 30;
    public static int SUPPLIES_PRICE = 100;
    public static int TRANSPLUTONICS_PRICE = 200;
    public static int TRANSPLUTONIC_ORE_PRICE = 75;
    public static float ORE_TO_METAL_RATIO = (float) ORE_PRICE / METAL_PRICE;
    public static float METAL_TO_SUPPLIES_RATIO = (float) METAL_PRICE / SUPPLIES_PRICE;
    public static float TRANSPLUTONICS_TO_SUPPLIES_RATIO = (float) TRANSPLUTONICS_PRICE / SUPPLIES_PRICE;
    public static float TRANSPLUTONIC_ORE_TO_TRANSPLUTONICS_RATIO;
    public static int ORGANICS_PRICE = 30;
    public static int DOMESTIC_GOODS_PRICE = 50;
    public static float ORGANICS_TO_DOMESTIC_GOODS_RATIO;
    public static float BUDGET_PERCENT = 0.10f;
    public static float CARGO_SPACE_TAKEN = 0.10f;
    public static final float CARGO_COMPENSATION_FACTOR = 1f / (1f - CARGO_SPACE_TAKEN);

    @Override
    public void onGameLoad(boolean newGame) {
        initPrices();
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

    private void initPrices() {
        CommoditySpecAPI oreSpec = Global.getSettings().getCommoditySpec("ore");
        CommoditySpecAPI metalSpec = Global.getSettings().getCommoditySpec("metals");
        CommoditySpecAPI suppliesSpec = Global.getSettings().getCommoditySpec("supplies");
        CommoditySpecAPI transplutonicsSpec = Global.getSettings().getCommoditySpec("rare_metals");
        CommoditySpecAPI transplutonicOreSpec = Global.getSettings().getCommoditySpec("rare_ore");
        CommoditySpecAPI organicsSpec = Global.getSettings().getCommoditySpec("organics");
        CommoditySpecAPI domesticGoodsSpec = Global.getSettings().getCommoditySpec("domestic_goods");

        if (oreSpec != null) {
            ORE_PRICE = (int) oreSpec.getBasePrice();
        }
        if (metalSpec != null) {
            METAL_PRICE = (int) metalSpec.getBasePrice();
        }
        if (suppliesSpec != null) {
            SUPPLIES_PRICE = (int) suppliesSpec.getBasePrice();
        }
        if (transplutonicsSpec != null) {
            TRANSPLUTONICS_PRICE = (int) transplutonicsSpec.getBasePrice();
        }
        if (transplutonicOreSpec != null) {
            TRANSPLUTONIC_ORE_PRICE = (int) transplutonicOreSpec.getBasePrice();
        }
        if (organicsSpec != null) {
            ORGANICS_PRICE = (int) organicsSpec.getBasePrice();
        }
        if (domesticGoodsSpec != null) {
            DOMESTIC_GOODS_PRICE = (int) domesticGoodsSpec.getBasePrice();
        }

        ORE_TO_METAL_RATIO = (float) ORE_PRICE / METAL_PRICE;
        METAL_TO_SUPPLIES_RATIO = (float) METAL_PRICE / SUPPLIES_PRICE;
        TRANSPLUTONICS_TO_SUPPLIES_RATIO = (float) TRANSPLUTONICS_PRICE / SUPPLIES_PRICE;
        TRANSPLUTONIC_ORE_TO_TRANSPLUTONICS_RATIO = (float) TRANSPLUTONIC_ORE_PRICE / TRANSPLUTONICS_PRICE;
        ORGANICS_TO_DOMESTIC_GOODS_RATIO = (float) ORGANICS_PRICE / DOMESTIC_GOODS_PRICE;
    }

    private void loadConfig() throws Exception {
        JSONObject config = Global.getSettings().loadJSON("data/config/settings.json", "mobile_refining");

        if (config != null) {
            BUDGET_PERCENT = (float) config.optDouble("budgetPercent", BUDGET_PERCENT);
        }
    }

    private void grantAbilityToPlayer() {
        if (!Global.getSector().getPlayerFleet().hasAbility(ABILITY_ID)) {
            Global.getSector().getCharacterData().addAbility(ABILITY_ID);
        }
    }
}