package com.insituresourceutilization.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseMissionIntel;
import com.fs.starfarer.api.impl.campaign.missions.ProcurementMission;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.DeliveryMissionIntel;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MissionCargoTracker {

    private static final String CHEAP_COMMODITY_CLASS = "com.fs.starfarer.api.impl.campaign.missions.CheapCommodityMission";

    private static Set<String> cachedResult = null;
    private static long cachedTimestamp = -1;

    private static final Set<String> MISSION_CLASS_PREFIXES = new HashSet<>();
    static {
        MISSION_CLASS_PREFIXES.add("DeliveryMissionIntel");
        MISSION_CLASS_PREFIXES.add("ProcurementMission");
        MISSION_CLASS_PREFIXES.add("CheapCommodityMission");
    }

    private static final Map<String, String> DISPLAY_NAME_TO_ID = new HashMap<>();
    static {
        DISPLAY_NAME_TO_ID.put("Transplutonics", "rare_metals");
        DISPLAY_NAME_TO_ID.put("Metals", "metals");
        DISPLAY_NAME_TO_ID.put("Ore", "ore");
        DISPLAY_NAME_TO_ID.put("Volatiles", "volatiles");
        DISPLAY_NAME_TO_ID.put("Organics", "organics");
        DISPLAY_NAME_TO_ID.put("Transplutonic Ore", "rare_ore");
    }

    public static float getAvailableQuantity(String commodityId, CargoAPI cargo) {
        float total = cargo.getCommodityQuantity(commodityId);
        boolean reserved = isCommodityReserved(commodityId);
        return reserved ? 0f : total;
    }

    public static float getAvailableQuantity(String commodityId, CargoAPI cargo, Set<String> reservedSet) {
        float total = cargo.getCommodityQuantity(commodityId);
        return reservedSet.contains(commodityId) ? 0f : total;
    }

    public static boolean isCommodityReserved(String commodityId) {
        Set<String> reserved = getAllReservedCommodities();
        return reserved.contains(commodityId);
    }

    public static Set<String> getAllReservedCommodities() {
        long currentTimestamp = Global.getSector().getClock().getTimestamp();
        if (cachedResult != null && cachedTimestamp == currentTimestamp) {
            return cachedResult;
        }

        Set<String> result = new HashSet<>();

        try {
            List<IntelInfoPlugin> intelList = Global.getSector().getIntelManager().getIntel();

            for (IntelInfoPlugin intel : intelList) {
                if (!isRelevantMissionIntel(intel)) {
                    continue;
                }

                if (!isActiveMission(intel)) {
                    continue;
                }

                if (intel instanceof ProcurementMission) {
                    addProcurementReservation(result, (ProcurementMission) intel);
                } else if (isCheapCommodityMission(intel)) {
                    addCheapCommodityReservation(result, intel);
                } else if (intel instanceof DeliveryMissionIntel) {
                    addDeliveryReservation(result, intel);
                }
            }
        } catch (Exception e) {
            Global.getLogger(MissionCargoTracker.class).error("Error tracking mission cargo", e);
        }

        cachedResult = result;
        cachedTimestamp = currentTimestamp;
        return result;
    }

    private static boolean isRelevantMissionIntel(IntelInfoPlugin intel) {
        String className = intel.getClass().getName();
        for (String prefix : MISSION_CLASS_PREFIXES) {
            if (className.contains(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isActiveMission(IntelInfoPlugin intel) {
        if (!(intel instanceof BaseMissionIntel)) {
            return !intel.isEnded();
        }

        BaseMissionIntel mission = (BaseMissionIntel) intel;
        return mission.isAccepted() && !mission.isCompleted() && !mission.isFailed() && !mission.isAbandoned() && !mission.isCancelled();
    }

    private static void addProcurementReservation(Set<String> result, ProcurementMission intel) {
        try {
            Field contactField = ProcurementMission.class.getDeclaredField("contact");
            contactField.setAccessible(true);
            PersonAPI contact = (PersonAPI) contactField.get(intel);

            if (contact == null) {
                return;
            }

            MemoryAPI memory = contact.getMemoryWithoutUpdate();
            String commodityName = memory.getString("$mpm_commodityName");

            if (commodityName == null) {
                return;
            }

            String commodityId = DISPLAY_NAME_TO_ID.get(commodityName);

            if (commodityId != null) {
                result.add(commodityId);
            }
        } catch (Exception e) {
            Global.getLogger(MissionCargoTracker.class).warn("Failed to read ProcurementMission data via MemoryAPI", e);
        }
    }

    private static boolean isCheapCommodityMission(IntelInfoPlugin intel) {
        return CHEAP_COMMODITY_CLASS.equals(intel.getClass().getName());
    }

    private static void addCheapCommodityReservation(Set<String> result, IntelInfoPlugin intel) {
        try {
            Class<?> clazz = intel.getClass();

            Field commodityIdField = clazz.getDeclaredField("commodityId");
            commodityIdField.setAccessible(true);
            String commodityId = (String) commodityIdField.get(intel);

            if (commodityId != null) {
                result.add(commodityId);
            }
        } catch (Exception e) {
            Global.getLogger(MissionCargoTracker.class).warn("Failed to read CheapCommodityMission data", e);
        }
    }

    private static void addDeliveryReservation(Set<String> result, IntelInfoPlugin intel) {
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

                result.add(commodityId);
            }
        } catch (Exception e) {
            Global.getLogger(MissionCargoTracker.class).warn("Failed to read Delivery mission data", e);
        }
    }
}