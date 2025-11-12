package com.graphhopper.reader.osm.custom;

import com.graphhopper.routing.ev.BicycleInfraEV;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.ImportUnit;
import com.graphhopper.util.PMap;

/**
 * ImportUnit für das bicycle_infra EncodedValue.
 */
public class BicycleInfraImportUnit {
    
    public static ImportUnit create() {
        return create(BicycleInfraEV.KEY);
    }
    
    public static ImportUnit create(String name) {
        return ImportUnit.create(
            name,
            properties -> createEncodedValue(name, properties),
            null  // Kein Standard-TagParser, wird in CustomGraphHopper.buildOSMParsers hinzugefügt
        );
    }
    
    private static EncodedValue createEncodedValue(String name, PMap properties) {
        if (!BicycleInfraEV.KEY.equals(name)) {
            throw new IllegalArgumentException("BicycleInfraImportUnit can only create '" + 
                BicycleInfraEV.KEY + "' but was asked to create '" + name + "'");
        }
        return BicycleInfraEV.create();
    }
}

