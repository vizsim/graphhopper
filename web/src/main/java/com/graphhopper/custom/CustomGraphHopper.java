// web/src/main/java/com/graphhopper/custom/CustomGraphHopper.java
package com.graphhopper.custom;

import com.graphhopper.GraphHopper;
import com.graphhopper.reader.osm.custom.CustomCsvLoader;
import com.graphhopper.reader.osm.custom.CustomPresentParser;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.OSMParsers;
import com.graphhopper.util.PMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class CustomGraphHopper extends GraphHopper {

    public CustomGraphHopper() {
        super();
        // Setze eine erweiterte ImportRegistry, die custom_present kennt
        setImportRegistry(new ImportRegistry() {
            private final ImportRegistry defaultRegistry = new DefaultImportRegistry();
            
            @Override
            public ImportUnit createImportUnit(String name) {
                if ("custom_present".equals(name)) {
                    return CustomPresentImportUnit.create();
                }
                return defaultRegistry.createImportUnit(name);
            }
        });
    }

    @Override
    protected OSMParsers buildOSMParsers(Map<String, PMap> encodedValuesWithProps,
                                         Map<String, ImportUnit> activeImportUnits,
                                         Map<String, List<String>> restrictionVehicleTypesByProfile,
                                         List<String> ignoredHighways) {
        OSMParsers parsers = super.buildOSMParsers(encodedValuesWithProps, activeImportUnits,
                restrictionVehicleTypesByProfile, ignoredHighways);

        // WICHTIG: Hole das EncodedValue vom EncodingManager, nicht eine neue Instanz!
        BooleanEncodedValue customPresentEV = getEncodingManager().getBooleanEncodedValue("custom_present");
        
        Long2ByteOpenHashMap map = CustomCsvLoader.load(Path.of("data/custom/custom_values.csv"));
        parsers.addWayTagParser(new CustomPresentParser(customPresentEV, map));
        return parsers;
    }
}
