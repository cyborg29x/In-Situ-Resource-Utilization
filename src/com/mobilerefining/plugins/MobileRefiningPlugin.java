package com.mobilerefining.plugins;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

public class MobileRefiningPlugin extends BaseModPlugin {

    public static final String ABILITY_ID = "mobile_refining";

    @Override
    public void onGameLoad(boolean newGame) {
        grantAbilityToPlayer();
    }

    private void grantAbilityToPlayer() {
        if (!Global.getSector().getPlayerFleet().hasAbility(ABILITY_ID)) {
            Global.getSector().getCharacterData().addAbility(ABILITY_ID);
        }
    }
}