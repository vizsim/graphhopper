package com.graphhopper.routing.ev;

public final class CustomEV {
    private CustomEV() {}
    public static BooleanEncodedValue customPresent() {
        // Bit-Name: "custom_present"
        // storeTwoDirections=false: Wert gilt für beide Richtungen (spart Speicher!)
        return new SimpleBooleanEncodedValue("custom_present", false);
    }
}
