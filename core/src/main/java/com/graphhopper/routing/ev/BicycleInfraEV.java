package com.graphhopper.routing.ev;

/**
 * Factory für das bicycle_infra EncodedValue.
 * Dieses EncodedValue kategorisiert Fahrradinfrastruktur basierend auf OSM-Tags.
 */
public class BicycleInfraEV {
    
    public static final String KEY = "bicycle_infra";
    
    public static EnumEncodedValue<BicycleInfra> create() {
        return new EnumEncodedValue<>(KEY, BicycleInfra.class, true);
    }
    
    private BicycleInfraEV() {
        // Utility class
    }
}
