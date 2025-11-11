package com.graphhopper.routing.ev;

import com.graphhopper.util.PMap;

public class CustomPresentImportUnit {
    
    public static ImportUnit create() {
        return create("custom_present");
    }
    
    public static ImportUnit create(String name) {
        return ImportUnit.create(
            name,
            properties -> createEncodedValue(name, properties),
            null  // Kein Standard-TagParser, wird in CustomGraphHopper.buildOSMParsers hinzugefügt
        );
    }
    
    private static EncodedValue createEncodedValue(String name, PMap properties) {
        // storeTwoDirections=false: Wert gilt für beide Richtungen
        return new SimpleBooleanEncodedValue(name, false);
    }
}
