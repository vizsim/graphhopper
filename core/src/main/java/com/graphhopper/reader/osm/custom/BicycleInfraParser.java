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
     * Basiert auf der Precedence Order von BikelaneCategories.lua
     */
    private BicycleInfra categorize(ReaderWay way) {
        // 1. Geschützter Radfahrstreifen (Protected Bike Lane) - MUSS GANZ OBEN stehen!
        if (isCyclewayOnHighwayProtected(way)) {
            return BicycleInfra.CYCLEWAY_ON_HIGHWAY_PROTECTED;
        }
        
        // 2. Radweg-Link
        if (isCyclewayLink(way)) {
            return BicycleInfra.CYCLEWAY_LINK;
        }
        
        // 3. Straßenquerung
        if (isCrossing(way)) {
            return BicycleInfra.CROSSING;
        }
        
        // 4. Fahrradstraße mit Anlieger/Kfz frei (muss vor normalem bicycleRoad kommen)
        BicycleInfra bicycleRoadType = getBicycleRoadType(way);
        if (bicycleRoadType != null) {
            return bicycleRoadType;
        }
        
        // 5. Busspur-Kategorien
        BicycleInfra busLaneType = getSharedBusLaneType(way);
        if (busLaneType != null) {
            return busLaneType;
        }
        
        // 6. Fußgängerzone mit Fahrrad frei
        if (isPedestrianAreaBicycleYes(way)) {
            return BicycleInfra.PEDESTRIAN_AREA_BICYCLE_YES;
        }
        
        // 7. Gemeinsamer Fahrstreifen
        if (isSharedMotorVehicleLane(way)) {
            return BicycleInfra.SHARED_MOTOR_VEHICLE_LANE;
        }
        
        // 8. Radfahrstreifen in Mittellage (zwischen Fahrspuren)
        if (isCyclewayOnHighwayBetweenLanes(way)) {
            return BicycleInfra.CYCLEWAY_ON_HIGHWAY_BETWEEN_LANES;
        }
        
        // 9. Radfahrstreifen oder Schutzstreifen (advisory/exclusive)
        BicycleInfra laneType = getCyclewayOnHighwayType(way);
        if (laneType != null) {
            return laneType;
        }
        
        // 10. Radwege (cycleway) - baulich getrennt
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
     * 
     * Wichtig: Diese Methode gibt IMMER die spezifischste Kategorie zurück:
     * - ADVISORY wenn lane=advisory
     * - EXCLUSIVE wenn lane=exclusive  
     * - ADVISORY_OR_EXCLUSIVE wenn lane vorhanden aber nicht spezifiziert
     */
    private BicycleInfra getCyclewayOnHighwayType(ReaderWay way) {
        // Nur auf highway=cycleway (transformierte Geometrie)
        if (!"cycleway".equals(way.getTag("highway"))) {
            return null;
        }
        
        String cycleway = way.getTag("cycleway");
        String lane = way.getTag("lane");
        
        // Muss cycleway=lane oder cycleway=opposite_lane sein
        if (!"lane".equals(cycleway) && !"opposite_lane".equals(cycleway)) {
            return null;
        }
        
        // Spezialfall: Angstweichen (cyclewayOnHighwayBetweenLanes)
        // Wenn "|lane|" in cycleway:lanes aber NICHT am Ende, dann ist es keine normale lane
        String cyclewayLanes = way.getTag("cycleway:lanes");
        String bicycleLanes = way.getTag("bicycle:lanes");
        
        if (hasCyclewayOnHighwayBetweenLanesConditions(way, cyclewayLanes, bicycleLanes)) {
            // Prüfe ob es ZUSÄTZLICH noch eine normale lane am Ende gibt
            if (cyclewayLanes != null && cyclewayLanes.contains("|lane|") && !cyclewayLanes.endsWith("|lane")) {
                return null; // Nur Angstweiche, keine normale lane
            }
            if (bicycleLanes != null && bicycleLanes.contains("|designated|") && !bicycleLanes.endsWith("|designated")) {
                return null; // Nur Angstweiche, keine normale lane
            }
        }
        
        // Jetzt prüfe lane-Typ: advisory, exclusive oder unbekannt
        if ("advisory".equals(lane)) {
            return BicycleInfra.CYCLEWAY_ON_HIGHWAY_ADVISORY;
        } else if ("exclusive".equals(lane)) {
            return BicycleInfra.CYCLEWAY_ON_HIGHWAY_EXCLUSIVE;
        } else {
            // Fallback wenn cycleway=lane aber lane-Typ nicht spezifiziert
            return BicycleInfra.CYCLEWAY_ON_HIGHWAY_ADVISORY_OR_EXCLUSIVE;
        }
    }
    
    /**
     * Hilfsmethode: Prüft ob cyclewayOnHighwayBetweenLanes Bedingungen erfüllt sind
     */
    private boolean hasCyclewayOnHighwayBetweenLanesConditions(ReaderWay way, String cyclewayLanes, String bicycleLanes) {
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
     * 4. Prüft baulich getrennte Radwege.
     * Gibt CYCLEWAY_ADJOINING, CYCLEWAY_ISOLATED oder CYCLEWAY_ADJOINING_OR_ISOLATED zurück.
     */
    private BicycleInfra getCyclewayType(ReaderWay way) {
        String highway = way.getTag("highway");
        String cycleway = way.getTag("cycleway");
        String trafficSign = way.getTag("traffic_sign");
        String isSidepath = way.getTag("is_sidepath");
        
        boolean isCycleway = false;
        
        // GUARD: cycleway=lane ist NICHT cycleway (sondern on-highway)
        if ("lane".equals(cycleway)) {
            return null;
        }
        
        // highway=cycleway mit is_sidepath (wichtigster Fall!)
        if ("cycleway".equals(highway) && isSidepath != null) {
            isCycleway = true;
        }
        
        // highway=cycleway mit track-Tagging
        if ("cycleway".equals(highway) && ("track".equals(cycleway) || "opposite_track".equals(cycleway))) {
            isCycleway = true;
        }
        
        // cycleway:side=track
        if ("track".equals(way.getTag("cycleway:right")) || 
            "track".equals(way.getTag("cycleway:left")) ||
            "track".equals(way.getTag("cycleway:both"))) {
            isCycleway = true;
        }
        
        // Verkehrszeichen DE:237 (nur auf erlaubten highway-Typen)
        if (trafficSign != null && trafficSign.contains("DE:237")) {
            // Whitelist ähnlich wie in Lua (living_street, pedestrian, service, track, bridleway, path, footway, cycleway)
            if ("living_street".equals(highway) || "pedestrian".equals(highway) || 
                "service".equals(highway) || "track".equals(highway) || 
                "bridleway".equals(highway) || "path".equals(highway) || 
                "footway".equals(highway) || "cycleway".equals(highway)) {
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
        String cycleway = way.getTag("cycleway");
        
        // Spezialfall: highway=cycleway mit cycleway=track und segregated
        if ("cycleway".equals(highway) && "track".equals(cycleway)) {
            if ("no".equals(segregated) || (trafficSign != null && trafficSign.contains("240"))) {
                String isSidepath = way.getTag("is_sidepath");
                if ("yes".equals(isSidepath)) {
                    return BicycleInfra.FOOT_AND_CYCLEWAY_SHARED_ADJOINING;
                } else if ("no".equals(isSidepath)) {
                    return BicycleInfra.FOOT_AND_CYCLEWAY_SHARED_ISOLATED;
                } else {
                    return BicycleInfra.FOOT_AND_CYCLEWAY_SHARED_ADJOINING_OR_ISOLATED;
                }
            }
            if ("yes".equals(segregated) || (trafficSign != null && trafficSign.contains("241"))) {
                String isSidepath = way.getTag("is_sidepath");
                if ("yes".equals(isSidepath)) {
                    return BicycleInfra.FOOT_AND_CYCLEWAY_SEGREGATED_ADJOINING;
                } else if ("no".equals(isSidepath)) {
                    return BicycleInfra.FOOT_AND_CYCLEWAY_SEGREGATED_ISOLATED;
                } else {
                    return BicycleInfra.FOOT_AND_CYCLEWAY_SEGREGATED_ADJOINING_OR_ISOLATED;
                }
            }
        }
        
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
            // Nur auf cycleway-like highways (nicht auf service/track ohne weitere Prüfung)
            if ("cycleway".equals(highway) || "path".equals(highway) || "footway".equals(highway)) {
                if ("yes".equals(isSidepath)) {
                    return BicycleInfra.FOOT_AND_CYCLEWAY_SEGREGATED_ADJOINING;
                } else if ("no".equals(isSidepath)) {
                    return BicycleInfra.FOOT_AND_CYCLEWAY_SEGREGATED_ISOLATED;
                } else {
                    return BicycleInfra.FOOT_AND_CYCLEWAY_SEGREGATED_ADJOINING_OR_ISOLATED;
                }
            }
            
            // Edge case: traffic_mode:right=foot für highway=cycleway (separate Geometrie)
            if ("cycleway".equals(highway)) {
                String trafficModeRight = way.getTag("traffic_mode:right");
                String separationRight = way.getTag("separation:right");
                
                // Separation muss entweder fehlen oder "no" sein
                boolean separationOk = separationRight == null || "no".equals(separationRight);
                
                if ("foot".equals(trafficModeRight) && separationOk) {
                    if ("yes".equals(isSidepath)) {
                        return BicycleInfra.FOOT_AND_CYCLEWAY_SEGREGATED_ADJOINING;
                    } else if ("no".equals(isSidepath)) {
                        return BicycleInfra.FOOT_AND_CYCLEWAY_SEGREGATED_ISOLATED;
                    } else {
                        return BicycleInfra.FOOT_AND_CYCLEWAY_SEGREGATED_ADJOINING_OR_ISOLATED;
                    }
                }
            }
        }
        
        // Gemeinsam (segregated=no oder traffic_sign DE:240)
        if ("no".equals(segregated) || (trafficSign != null && trafficSign.contains("240"))) {
            // Erweiterte Whitelist wie in Lua: cycleway, path, footway, service, track
            if ("cycleway".equals(highway) || "path".equals(highway) || 
                "footway".equals(highway) || "service".equals(highway) || 
                "track".equals(highway)) {
                
                if ("yes".equals(isSidepath)) {
                    return BicycleInfra.FOOT_AND_CYCLEWAY_SHARED_ADJOINING;
                } else if ("no".equals(isSidepath)) {
                    return BicycleInfra.FOOT_AND_CYCLEWAY_SHARED_ISOLATED;
                } else {
                    return BicycleInfra.FOOT_AND_CYCLEWAY_SHARED_ADJOINING_OR_ISOLATED;
                }
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
        
        String cyclewayLanes = way.getTag("cycleway:lanes");
        String bicycleLanes = way.getTag("bicycle:lanes");
        
        // HACK: Filter für cyclewayOnHighwayBetweenLanes
        // Wenn diese Bedingungen erfüllt sind, wurde es bereits kategorisiert
        if (hasCyclewayOnHighwayBetweenLanesConditions(way, cyclewayLanes, bicycleLanes)) {
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