package com.graphhopper.reader.osm.custom;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.*;
import java.util.stream.Stream;

public class CustomCsvLoader {
    private static final Logger logger = LoggerFactory.getLogger(CustomCsvLoader.class);
    
    public static Long2ByteOpenHashMap load(Path path) {
        Long2ByteOpenHashMap map = new Long2ByteOpenHashMap();
        map.defaultReturnValue((byte)0); // Default: NICHT vorhanden (0)
        
        if (!Files.exists(path)) {
            logger.warn("CSV file not found: {}. No custom_present data will be loaded.", path);
            return map;
        }
        
        try (Stream<String> lines = Files.lines(path)) {
            final int[] lineNum = {0};
            final int[] loadedCount = {0};
            
            lines.forEach(l -> {
                lineNum[0]++;
                // Skip header
                if (lineNum[0] == 1) {
                    logger.info("CSV header: {}", l);
                    return;
                }
                
                try {
                    String[] p = l.split(",");
                    if (p.length < 2) {
                        logger.warn("Line {} has invalid format (expected 2 columns): {}", lineNum[0], l);
                        return;
                    }
                    
                    long wayId = Long.parseLong(p[0].trim());
                    String attr = p[1].trim().toLowerCase();
                    byte present = (byte) (attr.equals("pano") || attr.equals("regular") ? 1 : 0);
                    
                    map.put(wayId, present);
                    loadedCount[0]++;
                    
                    // Log first few entries for verification
                    if (loadedCount[0] <= 5) {
                        logger.info("Loaded: wayId={}, attribute='{}', present={}", wayId, attr, present);
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Line {} has invalid way_id: {}", lineNum[0], l);
                }
            });
            
            logger.info("Successfully loaded {} OSM ways from CSV: {}", loadedCount[0], path);
            logger.info("Default value for missing ways: {}", map.defaultReturnValue());
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to load CSV: " + path, e);
        }
        return map;
    }
}
