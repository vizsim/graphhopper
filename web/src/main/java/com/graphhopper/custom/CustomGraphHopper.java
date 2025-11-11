package com.graphhopper.custom;

import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.reader.osm.custom.CustomCsvLoader;
import com.graphhopper.reader.osm.custom.CustomPresentParser;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.OSMParsers;
import com.graphhopper.util.PMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class CustomGraphHopper extends GraphHopper {

    private static final Logger logger = LoggerFactory.getLogger(CustomGraphHopper.class);
    private CustomEncodedValueConfig customConfig;

    public CustomGraphHopper() {
        super();
        // Setze eine erweiterte ImportRegistry, die custom_present kennt
        setImportRegistry(new ImportRegistry() {
            private final ImportRegistry defaultRegistry = new DefaultImportRegistry();
            
            @Override
            public ImportUnit createImportUnit(String name) {
                // Prüfe ob es unser custom EncodedValue ist
                if (customConfig != null && customConfig.getName().equals(name)) {
                    return CustomPresentImportUnit.create(customConfig.getName());
                }
                return defaultRegistry.createImportUnit(name);
            }
        });
    }

    @Override
    public GraphHopper init(GraphHopperConfig ghConfig) {
        // Lade Custom Config aus GraphHopperConfig
        loadCustomConfig(ghConfig);
        return super.init(ghConfig);
    }

    private void loadCustomConfig(GraphHopperConfig ghConfig) {
        customConfig = new CustomEncodedValueConfig();
        
        // Hole das verschachtelte custom_encoded_value Objekt aus der Config
        PMap pmap = ghConfig.asPMap();
        Object customEvObj = pmap.getObject("custom_encoded_value", null);
        
        if (customEvObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> customEvMap = (Map<String, Object>) customEvObj;
            
            customConfig.setName((String) customEvMap.getOrDefault("name", "custom_present"));
            customConfig.setCsvPath((String) customEvMap.getOrDefault("csv_path", "data/custom/custom_values.csv"));
            customConfig.setCsvColumnId((String) customEvMap.getOrDefault("csv_column_id", "way_id"));
            customConfig.setCsvColumnAttribute((String) customEvMap.getOrDefault("csv_column_attribute", "attribute"));
            
            String trueValuesStr = (String) customEvMap.getOrDefault("true_values", "pano,regular");
            customConfig.setTrueValues(List.of(trueValuesStr.split(",")));
        } else {
            // Fallback zu Defaults wenn custom_encoded_value nicht im Config ist
            customConfig.setName("custom_present");
            customConfig.setCsvPath("data/custom/custom_values.csv");
            customConfig.setCsvColumnId("way_id");
            customConfig.setCsvColumnAttribute("attribute");
            customConfig.setTrueValues(List.of("pano", "regular"));
        }
        
        // Debug-Logging
        logger.info("Custom EncodedValue Config:");
        logger.info("  name: {}", customConfig.getName());
        logger.info("  csv_path: {}", customConfig.getCsvPath());
        logger.info("  csv_column_id: {}", customConfig.getCsvColumnId());
        logger.info("  csv_column_attribute: {}", customConfig.getCsvColumnAttribute());
        logger.info("  true_values: {}", customConfig.getTrueValues());
    }

    @Override
    protected OSMParsers buildOSMParsers(Map<String, PMap> encodedValuesWithProps,
                                         Map<String, ImportUnit> activeImportUnits,
                                         Map<String, List<String>> restrictionVehicleTypesByProfile,
                                         List<String> ignoredHighways) {
        OSMParsers parsers = super.buildOSMParsers(encodedValuesWithProps, activeImportUnits,
                restrictionVehicleTypesByProfile, ignoredHighways);

        if (customConfig == null) {
            customConfig = new CustomEncodedValueConfig(); // Fallback auf Defaults
        }

        // WICHTIG: Hole das EncodedValue vom EncodingManager mit dem konfigurierten Namen
        BooleanEncodedValue customPresentEV = getEncodingManager().getBooleanEncodedValue(customConfig.getName());
        
        Long2ByteOpenHashMap map = CustomCsvLoader.load(
            Path.of(customConfig.getCsvPath()),
            customConfig.getCsvColumnId(),
            customConfig.getCsvColumnAttribute(),
            customConfig.getTrueValues()
        );
        parsers.addWayTagParser(new CustomPresentParser(customPresentEV, map));
        return parsers;
    }
}
