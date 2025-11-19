package com.graphhopper.routing.ev;

/**
 * Enum für verschiedene Fahrrad-Infrastruktur-Typen basierend auf OSM-Tags.
 * Die Kategorien sind von den FixMyBerlin Tilda Geo Kategorien übernommen.
 * (Quelle: https://github.com/FixMyBerlin/tilda-geo/blob/main/processing/topics/roads_bikelanes/bikelanes/BikelaneCategories.lua).
 */
public enum BicycleInfra {
    /** Keine spezielle Fahrradinfrastruktur vorhanden */
    NONE("none"),
    
    /** Fahrradstraße */
    BICYCLE_ROAD("bicycleRoad"),
    
    /** Fahrradstraße mit Anlieger frei oder "Kfz frei" */
    BICYCLE_ROAD_VEHICLE_DESTINATION("bicycleRoad_vehicleDestination"),
    
    /** Straßenquerung */
    CROSSING("crossing"),
    
    /** Radweg (baulich von der Fahrbahn abgesetzt aber straßenbegleitend) */
    CYCLEWAY_ADJOINING("cycleway_adjoining"),
    
    /** Radweg, selbstständig geführt (durch Wald und Feld) */
    CYCLEWAY_ISOLATED("cycleway_isolated"),
    
    /** Radweg (Straßenbegleitend oder selbstständig geführt) - Fallback wenn is_sidepath fehlt */
    CYCLEWAY_ADJOINING_OR_ISOLATED("cycleway_adjoiningOrIsolated"),
    
    /** Radweg-Routing-Verbindungsstück */
    CYCLEWAY_LINK("cyclewayLink"),
    
    /** Schutzstreifen */
    CYCLEWAY_ON_HIGHWAY_ADVISORY("cyclewayOnHighway_advisory"),
    
    /** Radfahrstreifen */
    CYCLEWAY_ON_HIGHWAY_EXCLUSIVE("cyclewayOnHighway_exclusive"),
    
    /** Radfahrstreifen oder Schutzstreifen - Fallback wenn cycleway:*:lane fehlt */
    CYCLEWAY_ON_HIGHWAY_ADVISORY_OR_EXCLUSIVE("cyclewayOnHighway_advisoryOrExclusive"),
    
    /** Radfahrstreifen in Mittellage (Fahrradweiche, "Angstweiche") */
    CYCLEWAY_ON_HIGHWAY_BETWEEN_LANES("cyclewayOnHighwayBetweenLanes"),
    
    /** Getrennter Rad- und Gehweg, straßenbegleitend */
    FOOT_AND_CYCLEWAY_SEGREGATED_ADJOINING("footAndCyclewaySegregated_adjoining"),
    
    /** Getrennter Rad- und Gehweg, selbstständig geführt */
    FOOT_AND_CYCLEWAY_SEGREGATED_ISOLATED("footAndCyclewaySegregated_isolated"),
    
    /** Getrennter Rad- und Gehweg - Fallback wenn is_sidepath fehlt */
    FOOT_AND_CYCLEWAY_SEGREGATED_ADJOINING_OR_ISOLATED("footAndCyclewaySegregated_adjoiningOrIsolated"),
    
    /** Gemeinsamer Geh- und Radweg, straßenbegleitend */
    FOOT_AND_CYCLEWAY_SHARED_ADJOINING("footAndCyclewayShared_adjoining"),
    
    /** Gemeinsamer Geh- und Radweg, selbstständig geführt */
    FOOT_AND_CYCLEWAY_SHARED_ISOLATED("footAndCyclewayShared_isolated"),
    
    /** Gemeinsamer Geh- und Radweg - Fallback wenn is_sidepath fehlt */
    FOOT_AND_CYCLEWAY_SHARED_ADJOINING_OR_ISOLATED("footAndCyclewayShared_adjoiningOrIsolated"),
    
    /** Gehweg mit Radwegfreigabe, straßenbegleitend */
    FOOTWAY_BICYCLE_YES_ADJOINING("footwayBicycleYes_adjoining"),
    
    /** Gehweg mit Radwegfreigabe, selbstständig geführt */
    FOOTWAY_BICYCLE_YES_ISOLATED("footwayBicycleYes_isolated"),
    
    /** Gehweg mit Radwegfreigabe - Fallback wenn is_sidepath fehlt */
    FOOTWAY_BICYCLE_YES_ADJOINING_OR_ISOLATED("footwayBicycleYes_adjoiningOrIsolated"),
    
    /** Fußgängerzone, Fahrrad frei */
    PEDESTRIAN_AREA_BICYCLE_YES("pedestrianAreaBicycleYes"),
    
    /** Geschützter Radfahrstreifen (Protected Bike Lane / PBL) */
    CYCLEWAY_ON_HIGHWAY_PROTECTED("cyclewayOnHighwayProtected"),
    
    /** Radfahrstreifen mit Freigabe Busverkehr */
    SHARED_BUS_LANE_BIKE_WITH_BUS("sharedBusLaneBikeWithBus"),
    
    /** Bussonderfahrstreifen mit Fahrrad frei */
    SHARED_BUS_LANE_BUS_WITH_BIKE("sharedBusLaneBusWithBike"),
    
    /** Gemeinsamer Fahrstreifen (Piktogramme auf Fahrbahn) */
    SHARED_MOTOR_VEHICLE_LANE("sharedMotorVehicleLane"),
    
    /** Führungsform unklar - Tags in OSM nicht ausreichend */
    NEEDS_CLARIFICATION("needsClarification");

    private final String name;

    BicycleInfra(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static BicycleInfra find(String name) {
        if (name == null) return NONE;
        
        for (BicycleInfra infra : values()) {
            if (infra.name.equalsIgnoreCase(name)) {
                return infra;
            }
        }
        return NONE;
    }
}
