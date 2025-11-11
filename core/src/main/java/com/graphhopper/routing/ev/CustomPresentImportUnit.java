package com.graphhopper.routing.ev;

import com.graphhopper.util.PMap;

public class CustomPresentImportUnit {
    
    public static ImportUnit create() {
        return ImportUnit.create(
            "custom_present",
            CustomPresentImportUnit::createEncodedValue,
            null  // Kein Standard-TagParser, wird in CustomGraphHopper.buildOSMParsers hinzugefügt
        );
    }
    
    private static EncodedValue createEncodedValue(PMap properties) {
        return CustomEV.customPresent();
    }
}
