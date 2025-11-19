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
 * (Quelle: https://github.com/FixMyBerlin/tilda-geo/blob/main/processing/topics/roads_bikelanes/bikelanes/BikelaneCategories.lua).
 */
public class BicycleInfraParser implements TagParser {
    
    private final EnumEncodedValue<BicycleInfra> bicycleInfraEnc;
    
    public BicycleInfraParser(EnumEncodedValue<BicycleInfra> bicycleInfraEnc) {
        this.bicycleInfraEnc = bicycleInfraEnc;
    }
    
    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        // Kategorisiere für beide Richtungen (forward und backward)
        BicycleInfra forward = categorizeForDirection(way, true);
        BicycleInfra backward = categorizeForDirection(way, false);
        
        bicycleInfraEnc.setEnum(false, edgeId, edgeIntAccess, forward);
        bicycleInfraEnc.setEnum(true, edgeId, edgeIntAccess, backward);
    }
    
    /**
     * Kategorisiert einen Way basierend auf seinen Tags für eine bestimmte Fahrtrichtung.
     * Die Reihenfolge ist wichtig - first match wins!
     * Basiert auf der Precedence Order von BikelaneCategories.lua
     * 
     * @param way Der OSM Way
     * @param forward true für Vorwärtsrichtung, false für Rückwärtsrichtung
     * @return Die passende BicycleInfra Kategorie für diese Richtung
     */
    private BicycleInfra categorizeForDirection(ReaderWay way, boolean forward) {
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
        BicycleInfra busLaneType = getSharedBusLaneTypeForDirection(way, forward);
        if (busLaneType != null) {
            return busLaneType;
        }
        
        // 6. Fußgängerzone mit Fahrrad frei
        if (isPedestrianAreaBicycleYes(way)) {
            return BicycleInfra.PEDESTRIAN_AREA_BICYCLE_YES;
        }
        
        // 7. Gemeinsamer Fahrstreifen
        if (isSharedMotorVehicleLaneForDirection(way, forward)) {
            return BicycleInfra.SHARED_MOTOR_VEHICLE_LANE;
        }
        
        // 8. Radfahrstreifen in Mittellage (zwischen Fahrspuren)
        if (isCyclewayOnHighwayBetweenLanes(way)) {
            return BicycleInfra.CYCLEWAY_ON_HIGHWAY_BETWEEN_LANES;
        }
        
        // 9a. Radfahrstreifen oder Schutzstreifen - Schutzstreifen (advisory)
        BicycleInfra advisory = getCyclewayOnHighwayAdvisoryForDirection(way, forward);
        if (advisory != null) {
            return advisory;
        }
        
        // 9b. Radfahrstreifen oder Schutzstreifen - Radfahrstreifen (exclusive)
        BicycleInfra exclusive = getCyclewayOnHighwayExclusiveForDirection(way, forward);
        if (exclusive != null) {
            return exclusive;
        }
        
        // 9c. Radfahrstreifen oder Schutzstreifen - nicht spezifiziert
        BicycleInfra advisoryOrExclusive = getCyclewayOnHighwayAdvisoryOrExclusiveForDirection(way, forward);
        if (advisoryOrExclusive != null) {
            return advisoryOrExclusive;
        }
        
        // 10. Radwege (cycleway) - baulich getrennt
        BicycleInfra cyclewayType = getCyclewayTypeForDirection(way, forward);
        if (cyclewayType != null) {
            return cyclewayType;
        }
        
        // 11. Gemeinsame oder getrennte Geh- und Radwege
        BicycleInfra footAndCyclewayType = getFootAndCyclewayType(way);
        if (footAndCyclewayType != null) {
            return footAndCyclewayType;
        }
        
        // 12. Gehweg mit Radwegfreigabe
        BicycleInfra footwayType = getFootwayBicycleYesTypeForDirection(way, forward);
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
     * 3a. Basis-Check: Prüft ob es ein Radfahrstreifen/Schutzstreifen auf der Fahrbahn ist.
     * Wird von den spezifischen Methoden aufgerufen.
     */
    private boolean isCyclewayOnHighwayAdvisoryOrExclusive(ReaderWay way) {
        // Prüfe alle möglichen cycleway Tags: cycleway, cycleway:right, cycleway:left, cycleway:both
        String cycleway = way.getTag("cycleway");
        String cyclewayRight = way.getTag("cycleway:right");
        String cyclewayLeft = way.getTag("cycleway:left");
        String cyclewayBoth = way.getTag("cycleway:both");
        
        // Mindestens eines muss "lane" oder "opposite_lane" sein
        boolean hasLane = "lane".equals(cycleway) || "opposite_lane".equals(cycleway) ||
                          "lane".equals(cyclewayRight) || "opposite_lane".equals(cyclewayRight) ||
                          "lane".equals(cyclewayLeft) || "opposite_lane".equals(cyclewayLeft) ||
                          "lane".equals(cyclewayBoth) || "opposite_lane".equals(cyclewayBoth);
        
        if (!hasLane) {
            return false;
        }
        
        // Spezialfall: Angstweichen (cyclewayOnHighwayBetweenLanes)
        // Wenn "|lane|" in cycleway:lanes aber NICHT am Ende, dann ist es keine normale lane
        String cyclewayLanes = way.getTag("cycleway:lanes");
        String bicycleLanes = way.getTag("bicycle:lanes");
        
        if (hasCyclewayOnHighwayBetweenLanesConditions(way, cyclewayLanes, bicycleLanes)) {
            // Prüfe ob es ZUSÄTZLICH noch eine normale lane am Ende gibt
            if (cyclewayLanes != null && cyclewayLanes.contains("|lane|") && !cyclewayLanes.endsWith("|lane")) {
                return false; // Nur Angstweiche, keine normale lane
            }
            if (bicycleLanes != null && bicycleLanes.contains("|designated|") && !bicycleLanes.endsWith("|designated")) {
                return false; // Nur Angstweiche, keine normale lane
            }
        }
        
        return true;
    }
    
    /**
     * 3b. Prüft ob es ein Schutzstreifen (advisory lane) ist.
     */
    /**
     * 3a. Prüft ob es ein Schutzstreifen (advisory lane) ist - richtungsabhängig.
     * forward=true: prüft cycleway:right, cycleway:both, cycleway
     * forward=false: prüft cycleway:left, cycleway:both, cycleway
     * Berücksichtigt cycleway:left:oneway=no und cycleway:right:oneway=no für bidirektionale Radwege
     */
    private BicycleInfra getCyclewayOnHighwayAdvisoryForDirection(ReaderWay way, boolean forward) {
        if (!isCyclewayOnHighwayAdvisoryOrExclusive(way)) {
            return null;
        }
        
        // Bestimme welche Seiten-Tags für diese Richtung relevant sind
        boolean hasAdvisory = false;
        
        // Prüfe rechte Seite
        String laneRight = way.getTag("cycleway:right:lane");
        String onewayRight = way.getTag("cycleway:right:oneway");
        boolean rightBidirectional = "no".equals(onewayRight);
        boolean checkRight = forward || rightBidirectional;
        
        if (checkRight && "advisory".equals(laneRight)) {
            hasAdvisory = true;
        }
        
        // Prüfe linke Seite
        String laneLeft = way.getTag("cycleway:left:lane");
        String onewayLeft = way.getTag("cycleway:left:oneway");
        boolean leftBidirectional = "no".equals(onewayLeft);
        boolean checkLeft = !forward || leftBidirectional;
        
        if (checkLeft && "advisory".equals(laneLeft)) {
            hasAdvisory = true;
        }
        
        // Prüfe beide Seiten und generisches Tag (gelten immer)
        String laneBoth = way.getTag("cycleway:both:lane");
        String lane = way.getTag("cycleway:lane");
        
        if ("advisory".equals(laneBoth) || "advisory".equals(lane)) {
            hasAdvisory = true;
        }
        
        return hasAdvisory ? BicycleInfra.CYCLEWAY_ON_HIGHWAY_ADVISORY : null;
    }
    
    /**
     * 3b. Prüft ob es ein Radfahrstreifen (exclusive lane) ist - richtungsabhängig.
     * forward=true: prüft cycleway:right, cycleway:both, cycleway
     * forward=false: prüft cycleway:left, cycleway:both, cycleway
     * Berücksichtigt cycleway:left:oneway=no und cycleway:right:oneway=no für bidirektionale Radwege
     */
    private BicycleInfra getCyclewayOnHighwayExclusiveForDirection(ReaderWay way, boolean forward) {
        if (!isCyclewayOnHighwayAdvisoryOrExclusive(way)) {
            return null;
        }
        
        boolean hasExclusive = false;
        
        // Prüfe rechte Seite
        String laneRight = way.getTag("cycleway:right:lane");
        String onewayRight = way.getTag("cycleway:right:oneway");
        boolean rightBidirectional = "no".equals(onewayRight);
        boolean checkRight = forward || rightBidirectional;
        
        if (checkRight && "exclusive".equals(laneRight)) {
            hasExclusive = true;
        }
        
        // Prüfe linke Seite
        String laneLeft = way.getTag("cycleway:left:lane");
        String onewayLeft = way.getTag("cycleway:left:oneway");
        boolean leftBidirectional = "no".equals(onewayLeft);
        boolean checkLeft = !forward || leftBidirectional;
        
        if (checkLeft && "exclusive".equals(laneLeft)) {
            hasExclusive = true;
        }
        
        // Prüfe beide Seiten und generisches Tag (gelten immer)
        String laneBoth = way.getTag("cycleway:both:lane");
        String lane = way.getTag("cycleway:lane");
        
        if ("exclusive".equals(laneBoth) || "exclusive".equals(lane)) {
            hasExclusive = true;
        }
        
        return hasExclusive ? BicycleInfra.CYCLEWAY_ON_HIGHWAY_EXCLUSIVE : null;
    }
    
    /**
     * 3c. Prüft ob es ein Radfahrstreifen oder Schutzstreifen ist (nicht spezifiziert) - richtungsabhängig.
     * forward=true: prüft cycleway:right, cycleway:both, cycleway
     * forward=false: prüft cycleway:left, cycleway:both, cycleway
     * Berücksichtigt cycleway:left:oneway=no und cycleway:right:oneway=no für bidirektionale Radwege
     */
    private BicycleInfra getCyclewayOnHighwayAdvisoryOrExclusiveForDirection(ReaderWay way, boolean forward) {
        // Diese Methode prüft nur ob cycleway=lane vorhanden ist (ohne advisory/exclusive Spezifizierung)
        boolean hasLane = false;
        
        // Prüfe rechte Seite
        String cyclewayRight = way.getTag("cycleway:right");
        String onewayRight = way.getTag("cycleway:right:oneway");
        boolean rightBidirectional = "no".equals(onewayRight);
        boolean checkRight = forward || rightBidirectional;
        
        if (checkRight && ("lane".equals(cyclewayRight) || "opposite_lane".equals(cyclewayRight))) {
            hasLane = true;
        }
        
        // Prüfe linke Seite
        String cyclewayLeft = way.getTag("cycleway:left");
        String onewayLeft = way.getTag("cycleway:left:oneway");
        boolean leftBidirectional = "no".equals(onewayLeft);
        boolean checkLeft = !forward || leftBidirectional;
        
        if (checkLeft && ("lane".equals(cyclewayLeft) || "opposite_lane".equals(cyclewayLeft))) {
            hasLane = true;
        }
        
        // Prüfe beide Seiten und generisches Tag (gelten immer)
        String cyclewayBoth = way.getTag("cycleway:both");
        String cycleway = way.getTag("cycleway");
        
        if ("lane".equals(cyclewayBoth) || "opposite_lane".equals(cyclewayBoth) ||
            "lane".equals(cycleway) || "opposite_lane".equals(cycleway)) {
            hasLane = true;
        }
        
        return hasLane ? BicycleInfra.CYCLEWAY_ON_HIGHWAY_ADVISORY_OR_EXCLUSIVE : null;
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
    /**
     * 4. Prüft baulich getrennte Radwege - richtungsabhängig.
     * Gibt CYCLEWAY_ADJOINING, CYCLEWAY_ISOLATED oder CYCLEWAY_ADJOINING_OR_ISOLATED zurück.
     * forward=true: prüft cycleway:right, cycleway:both, cycleway
     * forward=false: prüft cycleway:left, cycleway:both, cycleway
     */
    private BicycleInfra getCyclewayTypeForDirection(ReaderWay way, boolean forward) {
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
        // Dies ist nicht richtungsabhängig, da der gesamte Way ein Radweg ist
        if ("cycleway".equals(highway) && isSidepath != null) {
            isCycleway = true;
        }
        
        // highway=cycleway mit track-Tagging
        // Auch nicht richtungsabhängig
        if ("cycleway".equals(highway) && ("track".equals(cycleway) || "opposite_track".equals(cycleway))) {
            isCycleway = true;
        }
        
        // cycleway:side=track - HIER ist die Richtungsabhängigkeit wichtig!
        if (forward) {
            if ("track".equals(way.getTag("cycleway:right")) || 
                "track".equals(way.getTag("cycleway:both"))) {
                isCycleway = true;
            }
        } else {
            if ("track".equals(way.getTag("cycleway:left")) || 
                "track".equals(way.getTag("cycleway:both"))) {
                isCycleway = true;
            }
        }
        
        // Auch generisches cycleway=track prüfen (gilt für beide Richtungen)
        if ("track".equals(cycleway)) {
            isCycleway = true;
        }
        
        // Verkehrszeichen DE:237 (nur auf erlaubten highway-Typen)
        // Nicht richtungsabhängig, da es den gesamten Way betrifft
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
     * 6. Prüft Gehweg mit Radwegfreigabe ("Gehweg, Fahrrad frei") - richtungsabhängig.
     * Gibt FOOTWAY_BICYCLE_YES_* zurück.
     * forward=true: prüft sidewalk:right:bicycle, sidewalk:both:bicycle, oder highway=footway/path
     * forward=false: prüft sidewalk:left:bicycle, sidewalk:both:bicycle, oder highway=footway/path
     */
    private BicycleInfra getFootwayBicycleYesTypeForDirection(ReaderWay way, boolean forward) {
        String highway = way.getTag("highway");
        boolean hasBicycleAccess = false;
        boolean isSidewalk = false;
        
        // Fall 1: sidewalk:right/left/both:bicycle=yes (richtungsabhängig)
        // Diese Tags stehen am Straßen-Way, nicht am Gehweg selbst
        
        // Prüfe rechte Seite (forward direction)
        String sidewalkRightBicycle = way.getTag("sidewalk:right:bicycle");
        if (forward && "yes".equals(sidewalkRightBicycle)) {
            hasBicycleAccess = true;
            isSidewalk = true;
        }
        
        // Prüfe linke Seite (backward direction)
        String sidewalkLeftBicycle = way.getTag("sidewalk:left:bicycle");
        if (!forward && "yes".equals(sidewalkLeftBicycle)) {
            hasBicycleAccess = true;
            isSidewalk = true;
        }
        
        // Prüfe beide Seiten (gilt für beide Richtungen)
        String sidewalkBothBicycle = way.getTag("sidewalk:both:bicycle");
        if ("yes".equals(sidewalkBothBicycle)) {
            hasBicycleAccess = true;
            isSidewalk = true;
        }
        
        // Falls via sidewalk:* erkannt, gib adjoining zurück (ist per Definition straßenbegleitend)
        if (isSidewalk && hasBicycleAccess) {
            return BicycleInfra.FOOTWAY_BICYCLE_YES_ADJOINING;
        }
        
        // Fall 2: highway=footway oder highway=path (separate Geometrie)
        // Nicht richtungsabhängig, da der gesamte Way ein Gehweg ist
        if (!"footway".equals(highway) && !"path".equals(highway)) {
            return null;
        }
        
        String bicycle = way.getTag("bicycle");
        String trafficSign = way.getTag("traffic_sign");
        
        // Muss bicycle=yes haben oder Verkehrszeichen DE:1022-10 (Fahrrad frei)
        hasBicycleAccess = "yes".equals(bicycle) || 
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
     * 9. Prüft Busspur-Kategorien - richtungsabhängig.
     * Gibt SHARED_BUS_LANE_BUS_WITH_BIKE oder SHARED_BUS_LANE_BIKE_WITH_BUS zurück.
     * forward=true: prüft cycleway:right, cycleway:both, cycleway, busway:right
     * forward=false: prüft cycleway:left, cycleway:both, cycleway, busway:left
     */
    private BicycleInfra getSharedBusLaneTypeForDirection(ReaderWay way, boolean forward) {
        String highway = way.getTag("highway");
        String cycleway = way.getTag("cycleway");
        String trafficSign = way.getTag("traffic_sign");
        
        // Fall 1: highway=cycleway (transformierte Geometrie in osm2pgsql, aber nicht in GraphHopper)
        // In GraphHopper bleiben die originalen Tags erhalten
        if ("cycleway".equals(highway)) {
            if ("share_busway".equals(cycleway) || "opposite_share_busway".equals(cycleway)) {
                return BicycleInfra.SHARED_BUS_LANE_BUS_WITH_BIKE;
            }
            
            if (trafficSign != null && trafficSign.startsWith("DE:245")) {
                if (trafficSign.contains("1022-10") || trafficSign.contains("1022-14")) {
                    return BicycleInfra.SHARED_BUS_LANE_BUS_WITH_BIKE;
                }
            }
            
            String lane = way.getTag("lane");
            if ("share_busway".equals(lane)) {
                return BicycleInfra.SHARED_BUS_LANE_BIKE_WITH_BUS;
            }
            
            if (trafficSign != null && trafficSign.startsWith("DE:237")) {
                if (trafficSign.contains("1024-14") || trafficSign.contains("1026-32")) {
                    return BicycleInfra.SHARED_BUS_LANE_BIKE_WITH_BUS;
                }
            }
        }
        
        // Fall 2: cycleway:right/left/both=share_busway (richtungsabhängig)
        boolean hasBusLane = false;
        BicycleInfra busLaneType = null;
        
        // Prüfe rechte Seite
        String cyclewayRight = way.getTag("cycleway:right");
        String buswayRight = way.getTag("busway:right");
        if (forward && ("share_busway".equals(cyclewayRight) || "share_busway".equals(buswayRight))) {
            hasBusLane = true;
            busLaneType = BicycleInfra.SHARED_BUS_LANE_BUS_WITH_BIKE;
        }
        
        // Prüfe linke Seite
        String cyclewayLeft = way.getTag("cycleway:left");
        String buswayLeft = way.getTag("busway:left");
        if (!forward && ("share_busway".equals(cyclewayLeft) || "share_busway".equals(buswayLeft))) {
            hasBusLane = true;
            busLaneType = BicycleInfra.SHARED_BUS_LANE_BUS_WITH_BIKE;
        }
        
        // Prüfe beide Seiten (gilt für beide Richtungen)
        String cyclewayBoth = way.getTag("cycleway:both");
        String buswayBoth = way.getTag("busway:both");
        if ("share_busway".equals(cyclewayBoth) || "share_busway".equals(buswayBoth)) {
            hasBusLane = true;
            busLaneType = BicycleInfra.SHARED_BUS_LANE_BUS_WITH_BIKE;
        }
        
        // Generisches Tag (gilt für beide Richtungen)
        if ("share_busway".equals(cycleway) || "opposite_share_busway".equals(cycleway)) {
            hasBusLane = true;
            busLaneType = BicycleInfra.SHARED_BUS_LANE_BUS_WITH_BIKE;
        }
        
        if (hasBusLane) {
            return busLaneType;
        }
        
        return null;
    }
    
    /**
     * 10. Prüft gemeinsamer Fahrstreifen (Piktogramme auf Fahrbahn) - richtungsabhängig.
     * forward=true: prüft cycleway:right, cycleway:both, cycleway
     * forward=false: prüft cycleway:left, cycleway:both, cycleway
     */
    private boolean isSharedMotorVehicleLaneForDirection(ReaderWay way, boolean forward) {
        String highway = way.getTag("highway");
        String cycleway = way.getTag("cycleway");
        
        // Fall 1: highway=cycleway (transformierte Geometrie)
        if ("cycleway".equals(highway) && "shared_lane".equals(cycleway)) {
            return true;
        }
        
        // Fall 2: cycleway:right/left/both=shared_lane (richtungsabhängig)
        
        // Prüfe rechte Seite
        String cyclewayRight = way.getTag("cycleway:right");
        if (forward && "shared_lane".equals(cyclewayRight)) {
            return true;
        }
        
        // Prüfe linke Seite
        String cyclewayLeft = way.getTag("cycleway:left");
        if (!forward && "shared_lane".equals(cyclewayLeft)) {
            return true;
        }
        
        // Prüfe beide Seiten (gilt für beide Richtungen)
        String cyclewayBoth = way.getTag("cycleway:both");
        if ("shared_lane".equals(cyclewayBoth)) {
            return true;
        }
        
        // Generisches Tag (gilt für beide Richtungen)
        if ("shared_lane".equals(cycleway)) {
            return true;
        }
        
        return false;
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