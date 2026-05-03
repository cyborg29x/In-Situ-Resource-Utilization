package com.mobilerefining.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.intel.BaseMissionIntel;
import com.fs.starfarer.api.impl.campaign.intel.ProcurementMissionIntel;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.DeliveryMissionIntel;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MissionCargoTracker {

    private static final String CHEAP_COMMODITY_CLASS = "com.fs.starfarer.api.impl.campaign.missions.CheapCommodityMission";

    private static final Map<String, String> DISPLAY_NAME_TO_ID = new HashMap<>();
    static {
        DISPLAY_NAME_TO_ID.put("Transplutonics", "rare_metals");
        DISPLAY_NAME_TO_ID.put("Metals", "metals");
        DISPLAY_NAME_TO_ID.put("Supplies", "supplies");
        DISPLAY_NAME_TO_ID.put("Ore", "ore");
        DISPLAY_NAME_TO_ID.put("Volatiles", "volatiles");
        DISPLAY_NAME_TO_ID.put("Fuel", "fuel");
        DISPLAY_NAME_TO_ID.put("Organics", "organics");
        DISPLAY_NAME_TO_ID.put("Domestic Goods", "domestic_goods");
        DISPLAY_NAME_TO_ID.put("Transplutonic Ore", "rare_ore");
    }

    public static float getAvailableQuantity(String commodityId, CargoAPI cargo) {
        float total = cargo.getCommodityQuantity(commodityId);
        float reserved = getReservedQuantity(commodityId);
        return Math.max(0, total - reserved);
    }

    public static float getAvailableQuantity(String commodityId, CargoAPI cargo, Map<String, Float> reservedMap) {
        float total = cargo.getCommodityQuantity(commodityId);
        float reserved = reservedMap.getOrDefault(commodityId, 0f);
        return Math.max(0, total - reserved);
    }

    public static float getReservedQuantity(String commodityId) {
        Map<String, Float> reserved = getAllReservedCommodities();
        return reserved.getOrDefault(commodityId, 0f);
    }

    public static Map<String, Float> getAllReservedCommodities() {
        Map<String, Float> result = new HashMap<>();

        try {
            List<IntelInfoPlugin> intelList = Global.getSector().getIntelManager().getIntel();

            for (IntelInfoPlugin intel : intelList) {
                if (!isActiveMission(intel)) {
                    continue;
                }

                if (intel instanceof ProcurementMissionIntel) {
                    addProcurementReservation(result, (ProcurementMissionIntel) intel);
                } else if (isCheapCommodityMission(intel)) {
                    addCheapCommodityReservation(result, intel);
                } else if (intel instanceof DeliveryMissionIntel) {
                    addDeliveryReservation(result, intel);
                }
            }
        } catch (Exception e) {
            Global.getLogger(MissionCargoTracker.class).error("Error tracking mission cargo", e);
        }

        return result;
    }

    private static boolean isActiveMission(IntelInfoPlugin intel) {
        if (!(intel instanceof BaseMissionIntel)) {
            return !intel.isEnded();
        }

        BaseMissionIntel mission = (BaseMissionIntel) intel;
        return mission.isAccepted() && !mission.isCompleted() && !mission.isFailed() && !mission.isAbandoned() && !mission.isCancelled();
    }

    private static void addProcurementReservation(Map<String, Float> result, ProcurementMissionIntel intel) {
        try {
            Field quantityField = ProcurementMissionIntel.class.getDeclaredField("quantity");
            quantityField.setAccessible(true);
            float quantity = ((Number) quantityField.get(intel)).floatValue();

            String commodityId = null;
            try {
                Field commodityField = ProcurementMissionIntel.class.getDeclaredField("commodity");
                commodityField.setAccessible(true);
                Object commodityObj = commodityField.get(intel);
                if (commodityObj != null) {
                    java.lang.reflect.Method getIdMethod = commodityObj.getClass().getMethod("getId");
                    commodityId = (String) getIdMethod.invoke(commodityObj);
                }
            } catch (Exception e) {
                // commodity field not accessible, try pickCommodity() as fallback
            }

            if (commodityId == null) {
                try {
                    java.lang.reflect.Method pickMethod = ProcurementMissionIntel.class.getDeclaredMethod("pickCommodity");
                    commodityId = (String) pickMethod.invoke(intel);
                } catch (Exception e) {
                    Global.getLogger(MissionCargoTracker.class).warn("Failed to get commodity ID from ProcurementMissionIntel", e);
                }
            }

            if (commodityId != null) {
                result.merge(commodityId, quantity, Float::sum);
            }
        } catch (Exception e) {
            Global.getLogger(MissionCargoTracker.class).warn("Failed to read ProcurementMissionIntel data", e);
        }
    }

    private static boolean isCheapCommodityMission(IntelInfoPlugin intel) {
        return CHEAP_COMMODITY_CLASS.equals(intel.getClass().getName());
    }

    private static void addCheapCommodityReservation(Map<String, Float> result, IntelInfoPlugin intel) {
        try {
            Class<?> clazz = intel.getClass();

            Field commodityIdField = clazz.getDeclaredField("commodityId");
            commodityIdField.setAccessible(true);
            String commodityId = (String) commodityIdField.get(intel);

            Field quantityField = clazz.getDeclaredField("quantity");
            quantityField.setAccessible(true);
            float quantity = ((Number) quantityField.get(intel)).floatValue();

            result.merge(commodityId, quantity, Float::sum);
        } catch (Exception e) {
            Global.getLogger(MissionCargoTracker.class).warn("Failed to read CheapCommodityMission data", e);
        }
    }

    private static void addDeliveryReservation(Map<String, Float> result, IntelInfoPlugin intel) {
        try {
            if (!(intel instanceof DeliveryMissionIntel)) {
                return;
            }

            DeliveryMissionIntel deliveryIntel = (DeliveryMissionIntel) intel;

            String name = deliveryIntel.getName();
            if (name != null && name.startsWith("Delivery - ")) {
                String commodityName = name.substring("Delivery - ".length());
                String commodityId = DISPLAY_NAME_TO_ID.get(commodityName);

                if (commodityId == null) {
                    Global.getLogger(MissionCargoTracker.class).warn("Unknown commodity display name: " + commodityName);
                    return;
                }

                int quantity = deliveryIntel.getEvent().getQuantity();
                result.merge(commodityId, (float) quantity, Float::sum);
            }
        } catch (Exception e) {
            Global.getLogger(MissionCargoTracker.class).warn("Failed to read Delivery mission data", e);
        }
    }
}