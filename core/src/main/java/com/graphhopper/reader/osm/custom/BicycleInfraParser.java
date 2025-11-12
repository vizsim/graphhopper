package com.graphhopper.reader.osm.custom;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BicycleInfra;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.IntsRef;

/**
 * TagParser der Fahrradinfrastruktur-Kategorien basierend auf OSM-Tags setzt.
 * Die Logik ist inspiriert von den FixMyBerlin Tilda Geo BikelaneCategories.
 */
public class BicycleInfraParser implements TagParser {
    
    private final EnumEncodedValue<BicycleInfra> bicycleInfraEnc;
    
    public BicycleInfraParser(EnumEncodedValue<BicycleInfra> bicycleInfraEnc) {
        this.bicycleInfraEnc = bicycleInfraEnc;
    }
    
    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        BicycleInfra category = categorize(way);
        bicycleInfraEnc.setEnum(false, edgeId, edgeIntAccess, category);
    }
    
    /**
     * Kategorisiert einen Way basierend auf seinen Tags.
     * Die Reihenfolge ist wichtig - first match wins!
     */
    private BicycleInfra categorize(ReaderWay way) {
        // 1. Fahrradstraße mit Anlieger/Kfz frei
        BicycleInfra bicycleRoadType = getBicycleRoadType(way);
        if (bicycleRoadType != null) {
            return bicycleRoadType;
        }
        
        // 2. Fußgängerzone mit Fahrrad frei
        if (isPedestrianAreaBicycleYes(way)) {
            return BicycleInfra.PEDESTRIAN_AREA_BICYCLE_YES;
        }
        
        // 3. Busspur-Kategorien
        BicycleInfra busLaneType = getSharedBusLaneType(way);
        if (busLaneType != null) {
            return busLaneType;
        }
        
        // 4. Gemeinsamer Fahrstreifen
        if (isSharedMotorVehicleLane(way)) {
            return BicycleInfra.SHARED_MOTOR_VEHICLE_LANE;
        }
        
        // 5. Radweg-Link
        if (isCyclewayLink(way)) {
            return BicycleInfra.CYCLEWAY_LINK;
        }
        
        // 6. Straßenquerung
        if (isCrossing(way)) {
            return BicycleInfra.CROSSING;
        }
        
        // 7. Geschützter Radfahrstreifen (Protected Bike Lane)
        if (isCyclewayOnHighwayProtected(way)) {
            return BicycleInfra.CYCLEWAY_ON_HIGHWAY_PROTECTED;
        }
        
        // 8. Radfahrstreifen in Mittellage (zwischen Fahrspuren)
        if (isCyclewayOnHighwayBetweenLanes(way)) {
            return BicycleInfra.CYCLEWAY_ON_HIGHWAY_BETWEEN_LANES;
        }
        
        // 9. Radfahrstreifen oder Schutzstreifen
        BicycleInfra laneType = getCyclewayOnHighwayType(way);
        if (laneType != null) {
            return laneType;
        }
        
        // 10. Radwege (cycleway)
        BicycleInfra cyclewayType = getCyclewayType(way);
        if (cyclewayType != null) {
            return cyclewayType;
        }
        
        // 11. Gemeinsame oder getrennte Geh- und Radwege
        BicycleInfra footAndCyclewayType = getFootAndCyclewayType(way);
        if (footAndCyclewayType != null) {
            return footAndCyclewayType;
        }
        
        // 12. Gehweg mit Radwegfreigabe
        BicycleInfra footwayType = getFootwayBicycleYesType(way);
        if (footwayType != null) {
            return footwayType;
        }
        
        // 13. Führungsform unklar (needsClarification)
        if (needsClarification(way)) {
            return BicycleInfra.NEEDS_CLARIFICATION;
        }
        
        // Default: keine spezielle Infrastruktur
        return BicycleInfra.NONE;
    }
    
    /**
     * 1. Prüft ob es eine Fahrradstraße ist.
     * Gibt BICYCLE_ROAD oder BICYCLE_ROAD_VEHICLE_DESTINATION zurück.
     */
    private BicycleInfra getBicycleRoadType(ReaderWay way) {
        boolean isBicycleRoad = "yes".equals(way.getTag("bicycle_road"));
        
        String trafficSign = way.getTag("traffic_sign");
        if (!isBicycleRoad && trafficSign != null && trafficSign.contains("DE:244")) {
            isBicycleRoad = true;
        }
        
        if (!isBicycleRoad) {
            return null;
        }
        
        // Prüfe ob Anlieger/Kfz frei
        if (trafficSign != null && trafficSign.contains("1020-30")) {
            return BicycleInfra.BICYCLE_ROAD_VEHICLE_DESTINATION;
        }
        
        if (trafficSign != null && (trafficSign.contains("Kraftfahrzeuge-frei") || 
                                     trafficSign.contains("Kfz-Verkehr frei") ||
                                     trafficSign.contains("KFZ frei"))) {
            return BicycleInfra.BICYCLE_ROAD_VEHICLE_DESTINATION;
        }
        
        String vehicle = way.getTag("vehicle");
        String motorVehicle = way.getTag("motor_vehicle");
        if ("destination".equals(vehicle) || "destination".equals(motorVehicle) ||
            "yes".equals(vehicle) || "yes".equals(motorVehicle)) {
            return BicycleInfra.BICYCLE_ROAD_VEHICLE_DESTINATION;
        }
        
        return BicycleInfra.BICYCLE_ROAD;
    }
    
    /**
     * 2. Prüft ob es eine Fußgängerzone mit Fahrrad frei ist.
     */
    private boolean isPedestrianAreaBicycleYes(ReaderWay way) {
        if (!"pedestrian".equals(way.getTag("highway"))) {
            return false;
        }
        
        String bicycle = way.getTag("bicycle");
        return "yes".equals(bicycle) || "designated".equals(bicycle);
    }
    
    /**
     * 3. Prüft Radfahrstreifen und Schutzstreifen auf der Fahrbahn.
     * Gibt CYCLEWAY_ON_HIGHWAY_EXCLUSIVE, CYCLEWAY_ON_HIGHWAY_ADVISORY 
     * oder CYCLEWAY_ON_HIGHWAY_ADVISORY_OR_EXCLUSIVE zurück.
     */
    private BicycleInfra getCyclewayOnHighwayType(ReaderWay way) {
        String laneValue = null;
        
        // Prüfe cycleway:*:lane Tag
        if ("lane".equals(way.getTag("cycleway"))) {
            laneValue = way.getTag("cycleway:lane");
        } else if ("lane".equals(way.getTag("cycleway:right"))) {
            laneValue = way.getTag("cycleway:right:lane");
        } else if ("lane".equals(way.getTag("cycleway:left"))) {
            laneValue = way.getTag("cycleway:left:lane");
        } else if ("lane".equals(way.getTag("cycleway:both"))) {
            laneValue = way.getTag("cycleway:both:lane");
        }
        
        if (laneValue == null) {
            return null;
        }
        
        if ("exclusive".equals(laneValue)) {
            return BicycleInfra.CYCLEWAY_ON_HIGHWAY_EXCLUSIVE;
        } else if ("advisory".equals(laneValue)) {
            return BicycleInfra.CYCLEWAY_ON_HIGHWAY_ADVISORY;
        } else {
            // Fallback wenn lane=* aber nicht advisory/exclusive
            return BicycleInfra.CYCLEWAY_ON_HIGHWAY_ADVISORY_OR_EXCLUSIVE;
        }
    }
    
    /**
     * 4. Prüft baulich getrennte Radwege.
     * Gibt CYCLEWAY_ADJOINING, CYCLEWAY_ISOLATED oder CYCLEWAY_ADJOINING_OR_ISOLATED zurück.
     */
    private BicycleInfra getCyclewayType(ReaderWay way) {
        String highway = way.getTag("highway");
        String cycleway = way.getTag("cycleway");
        String trafficSign = way.getTag("traffic_sign");
        String isSidepath = way.getTag("is_sidepath");
        
        boolean isCycleway = false;
        
        // highway=cycleway mit is_sidepath (wichtigster Fall!)
        if ("cycleway".equals(highway) && isSidepath != null) {
            isCycleway = true;
        }
        
        // highway=cycleway mit track-Tagging
        if ("cycleway".equals(highway) && "track".equals(cycleway)) {
            isCycleway = true;
        }
        
        // cycleway:side=track
        if ("track".equals(way.getTag("cycleway:right")) || 
            "track".equals(way.getTag("cycleway:left")) ||
            "track".equals(way.getTag("cycleway:both"))) {
            isCycleway = true;
        }
        
        // Verkehrszeichen DE:237
        if (trafficSign != null && trafficSign.contains("DE:237")) {
            if ("cycleway".equals(highway) || "path".equals(highway)) {
                isCycleway = true;
            }
        }
        
        if (!isCycleway) {
            return null;
        }
        
        // Prüfe is_sidepath für adjoining vs isolated
        if ("yes".equals(isSidepath)) {
            return BicycleInfra.CYCLEWAY_ADJOINING;
        } else if ("no".equals(isSidepath)) {
            return BicycleInfra.CYCLEWAY_ISOLATED;
        } else {
            return BicycleInfra.CYCLEWAY_ADJOINING_OR_ISOLATED;
        }
    }
    
    /**
     * 5. Prüft gemeinsame oder getrennte Geh- und Radwege.
     * Gibt verschiedene FOOT_AND_CYCLEWAY_* Kategorien zurück.
     */
    private BicycleInfra getFootAndCyclewayType(ReaderWay way) {
        String highway = way.getTag("highway");
        String trafficSign = way.getTag("traffic_sign");
        String segregated = way.getTag("segregated");
        String foot = way.getTag("foot");
        String bicycle = way.getTag("bicycle");
        
        // Nur auf cycleway-artigen highways
        if (!"cycleway".equals(highway) && !"path".equals(highway) && 
            !"footway".equals(highway) && !"service".equals(highway) && 
            !"track".equals(highway)) {
            return null;
        }
        
        boolean footAllowed = "designated".equals(foot) || "yes".equals(foot);
        boolean bicycleAllowed = "designated".equals(bicycle) || "yes".equals(bicycle);
        
        if (!footAllowed || !bicycleAllowed) {
            return null;
        }
        
        String isSidepath = way.getTag("is_sidepath");
        
        // Getrennt (segregated=yes oder traffic_sign DE:241)
        if ("yes".equals(segregated) || (trafficSign != null && trafficSign.contains("241"))) {
            if ("yes".equals(isSidepath)) {
                return BicycleInfra.FOOT_AND_CYCLEWAY_SEGREGATED_ADJOINING;
            } else if ("no".equals(isSidepath)) {
                return BicycleInfra.FOOT_AND_CYCLEWAY_SEGREGATED_ISOLATED;
            } else {
                return BicycleInfra.FOOT_AND_CYCLEWAY_SEGREGATED_ADJOINING_OR_ISOLATED;
            }
        }
        
        // Gemeinsam (segregated=no oder traffic_sign DE:240)
        if ("no".equals(segregated) || (trafficSign != null && trafficSign.contains("240"))) {
            if ("yes".equals(isSidepath)) {
                return BicycleInfra.FOOT_AND_CYCLEWAY_SHARED_ADJOINING;
            } else if ("no".equals(isSidepath)) {
                return BicycleInfra.FOOT_AND_CYCLEWAY_SHARED_ISOLATED;
            } else {
                return BicycleInfra.FOOT_AND_CYCLEWAY_SHARED_ADJOINING_OR_ISOLATED;
            }
        }
        
        return null;
    }
    
    /**
     * 6. Prüft Gehweg mit Radwegfreigabe ("Gehweg, Fahrrad frei").
     * Gibt FOOTWAY_BICYCLE_YES_* zurück.
     */
    private BicycleInfra getFootwayBicycleYesType(ReaderWay way) {
        String highway = way.getTag("highway");
        
        // Nur highway=footway oder highway=path
        if (!"footway".equals(highway) && !"path".equals(highway)) {
            return null;
        }
        
        String bicycle = way.getTag("bicycle");
        String trafficSign = way.getTag("traffic_sign");
        
        // Muss bicycle=yes haben oder Verkehrszeichen DE:1022-10 (Fahrrad frei)
        boolean hasBicycleAccess = "yes".equals(bicycle) || 
                                   (trafficSign != null && trafficSign.contains("1022-10"));
        
        if (!hasBicycleAccess) {
            return null;
        }
        
        // Prüfe mtb:scale - wenn > 1, dann nicht relevant
        String mtbScale = way.getTag("mtb:scale");
        if (mtbScale != null) {
            String cleaned = mtbScale.replaceAll("[\\+\\-\\s]", "");
            try {
                int scale = Integer.parseInt(cleaned);
                if (scale > 1) {
                    return null;
                }
                // Wenn mtb:scale vorhanden, brauchen wir traffic_sign oder is_sidepath
                if (trafficSign == null && way.getTag("is_sidepath") == null) {
                    return null;
                }
            } catch (NumberFormatException e) {
                // Ignoriere ungültige Werte
            }
        }
        
        String isSidepath = way.getTag("is_sidepath");
        if ("yes".equals(isSidepath)) {
            return BicycleInfra.FOOTWAY_BICYCLE_YES_ADJOINING;
        } else if ("no".equals(isSidepath)) {
            return BicycleInfra.FOOTWAY_BICYCLE_YES_ISOLATED;
        } else {
            return BicycleInfra.FOOTWAY_BICYCLE_YES_ADJOINING_OR_ISOLATED;
        }
    }
    
    /**
     * 7. Prüft Radweg-Routing-Verbindungsstück.
     */
    private boolean isCyclewayLink(ReaderWay way) {
        return "cycleway".equals(way.getTag("highway")) && 
               "link".equals(way.getTag("cycleway"));
    }
    
    /**
     * 8. Prüft Straßenquerungen (Crossings).
     */
    private boolean isCrossing(ReaderWay way) {
        String highway = way.getTag("highway");
        String bicycle = way.getTag("bicycle");
        
        // highway=cycleway + cycleway=crossing
        if ("cycleway".equals(highway) && "crossing".equals(way.getTag("cycleway"))) {
            return true;
        }
        
        // highway=path + path=crossing + bicycle=yes/designated
        if ("path".equals(highway) && "crossing".equals(way.getTag("path"))) {
            if ("yes".equals(bicycle) || "designated".equals(bicycle)) {
                return true;
            }
        }
        
        // highway=footway + footway=crossing + bicycle=yes/designated
        if ("footway".equals(highway) && "crossing".equals(way.getTag("footway"))) {
            if ("yes".equals(bicycle) || "designated".equals(bicycle)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 9. Prüft Busspur-Kategorien.
     * Gibt SHARED_BUS_LANE_BUS_WITH_BIKE oder SHARED_BUS_LANE_BIKE_WITH_BUS zurück.
     */
    private BicycleInfra getSharedBusLaneType(ReaderWay way) {
        String highway = way.getTag("highway");
        String cycleway = way.getTag("cycleway");
        String lane = way.getTag("lane");
        String trafficSign = way.getTag("traffic_sign");
        
        // Nur auf transformierten highway=cycleway Geometrien
        if (!"cycleway".equals(highway)) {
            return null;
        }
        
        // Bussonderfahrstreifen mit Fahrrad frei (DE:245 mit 1022-10 oder 1022-14)
        if ("share_busway".equals(cycleway) || "opposite_share_busway".equals(cycleway)) {
            return BicycleInfra.SHARED_BUS_LANE_BUS_WITH_BIKE;
        }
        
        if (trafficSign != null && trafficSign.startsWith("DE:245")) {
            if (trafficSign.contains("1022-10") || trafficSign.contains("1022-14")) {
                return BicycleInfra.SHARED_BUS_LANE_BUS_WITH_BIKE;
            }
        }
        
        // Radfahrstreifen mit Freigabe Busverkehr (DE:237 mit 1024-14 oder 1026-32)
        if ("share_busway".equals(lane)) {
            return BicycleInfra.SHARED_BUS_LANE_BIKE_WITH_BUS;
        }
        
        if (trafficSign != null && trafficSign.startsWith("DE:237")) {
            if (trafficSign.contains("1024-14") || trafficSign.contains("1026-32")) {
                return BicycleInfra.SHARED_BUS_LANE_BIKE_WITH_BUS;
            }
        }
        
        return null;
    }
    
    /**
     * 10. Prüft gemeinsamer Fahrstreifen (Piktogramme auf Fahrbahn).
     */
    private boolean isSharedMotorVehicleLane(ReaderWay way) {
        return "cycleway".equals(way.getTag("highway")) && 
               "shared_lane".equals(way.getTag("cycleway"));
    }
    
    /**
     * 11. Prüft Radfahrstreifen in Mittellage (Fahrradweiche, "Angstweiche").
     * Erkennt Radwege zwischen Fahrspuren anhand von *:lanes Tags.
     */
    private boolean isCyclewayOnHighwayBetweenLanes(ReaderWay way) {
        String cyclewayLanes = way.getTag("cycleway:lanes");
        String bicycleLanes = way.getTag("bicycle:lanes");
        
        // Prüfe ob "|lane|" in cycleway:lanes vorkommt (lane zwischen anderen Spuren)
        if (cyclewayLanes != null && cyclewayLanes.contains("|lane|")) {
            return true;
        }
        
        // Prüfe ob "|designated|" in bicycle:lanes vorkommt
        if (bicycleLanes != null && bicycleLanes.contains("|designated|")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 12. Prüft geschützter Radfahrstreifen (Protected Bike Lane / PBL).
     * Erfordert physische Trennung vom motorisierten Verkehr.
     */
    private boolean isCyclewayOnHighwayProtected(ReaderWay way) {
        String isSidepath = way.getTag("is_sidepath");
        
        // Muss ein Sidepath sein
        if (!"yes".equals(isSidepath)) {
            return false;
        }
        
        // Prüfe physische Trennung links
        String separationLeft = way.getTag("separation:left");
        if (isPhysicalSeparation(separationLeft)) {
            return true;
        }
        
        // Prüfe ob links parkende Autos sind (gelten als Trennung)
        String trafficModeLeft = way.getTag("traffic_mode:left");
        if ("parking".equals(trafficModeLeft)) {
            return true;
        }
        
        // Für Gegenrichtungs-Radwege: prüfe Trennung rechts
        String trafficModeRight = way.getTag("traffic_mode:right");
        String separationRight = way.getTag("separation:right");
        if ("motor_vehicle".equals(trafficModeRight) && isPhysicalSeparation(separationRight)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Hilfsmethode: Prüft ob ein separation-Wert physische Trennung darstellt.
     */
    private boolean isPhysicalSeparation(String separation) {
        if (separation == null) {
            return false;
        }
        
        return "bollard".equals(separation) || 
               "flex_post".equals(separation) || 
               "vertical_panel".equals(separation) || 
               "studs".equals(separation) || 
               "bump".equals(separation) || 
               "planter".equals(separation) || 
               "fence".equals(separation) || 
               "jersey_barrier".equals(separation) || 
               "guard_rail".equals(separation);
    }
    
    /**
     * 13. Prüft ob die Führungsform unklar ist (needsClarification).
     * Sammelt Radinfrastruktur mit unzureichenden Tags.
     */
    private boolean needsClarification(ReaderWay way) {
        String highway = way.getTag("highway");
        String bicycle = way.getTag("bicycle");
        String cycleway = way.getTag("cycleway");
        String foot = way.getTag("foot");
        
        // HACK: Filter für cyclewayOnHighwayBetweenLanes auf sides
        if (isCyclewayOnHighwayBetweenLanes(way)) {
            return false;
        }
        
        // cycleway=shared wird als Kampagne behandelt, nicht als Infrastruktur
        if ("shared".equals(cycleway)) {
            return false;
        }
        
        // highway=cycleway ohne weitere Spezifikation
        if ("cycleway".equals(highway)) {
            return true;
        }
        
        // highway=path mit bicycle=designated aber ohne weitere Details
        if ("path".equals(highway) && "designated".equals(bicycle)) {
            // Ausschluss für MTB-Pfade
            if (way.getTag("mtb:scale") != null || "yes".equals(way.getTag("mtb"))) {
                return false;
            }
            
            String surface = way.getTag("surface");
            if ("ground".equals(surface) || "dirt".equals(surface) || 
                "fine_gravel".equals(surface) || "gravel".equals(surface) || 
                "pebblestone".equals(surface) || "earth".equals(surface)) {
                return false;
            }
            
            // Exklusive Radwege (foot=no) auf path
            if ("no".equals(foot)) {
                return true;
            }
            
            return true;
        }
        
        // highway=footway mit bicycle=designated
        if ("footway".equals(highway) && "designated".equals(bicycle)) {
            return true;
        }
        
        return false;
    }
}
