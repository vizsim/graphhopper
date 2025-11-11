package com.graphhopper.reader.osm.custom;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.nio.file.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomCsvLoader {
    private static final Logger logger = LoggerFactory.getLogger(CustomCsvLoader.class);
    
    /**
     * Lädt OSM Way IDs und Attribute aus einer CSV-Datei mit konfigurierbaren Spaltennamen
     * 
     * @param path Pfad zur CSV-Datei
     * @param columnId Name der Spalte mit OSM Way IDs
     * @param columnAttribute Name der Spalte mit Attributen
     * @param trueValues Liste von Werten die zu true führen
     * @return Map mit Way ID → present (1) oder nicht (0)
     */
    public static Long2ByteOpenHashMap load(Path path, String columnId, String columnAttribute, List<String> trueValues) {
        Long2ByteOpenHashMap map = new Long2ByteOpenHashMap();
        map.defaultReturnValue((byte)0); // Default: NICHT vorhanden (0)
        
        if (!Files.exists(path)) {
            logger.warn("CSV file not found: {}. No custom data will be loaded.", path);
            return map;
        }
        
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                logger.warn("CSV file is empty: {}", path);
                return map;
            }
            
            // Parse Header und finde Column-Indizes
            String[] headers = headerLine.split(",");
            Map<String, Integer> columnIndexes = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                columnIndexes.put(headers[i].trim(), i);
            }
            
            logger.info("CSV header: {}", headerLine);
            logger.info("Looking for columns: id='{}', attribute='{}'", columnId, columnAttribute);
            
            Integer idIndex = columnIndexes.get(columnId);
            Integer attrIndex = columnIndexes.get(columnAttribute);
            
            if (idIndex == null) {
                throw new IllegalArgumentException("Column '" + columnId + "' not found in CSV header. Available: " + columnIndexes.keySet());
            }
            if (attrIndex == null) {
                throw new IllegalArgumentException("Column '" + columnAttribute + "' not found in CSV header. Available: " + columnIndexes.keySet());
            }
            
            logger.info("Found id column at index {}, attribute column at index {}", idIndex, attrIndex);
            logger.info("Values considered as true: {}", trueValues);
            
            int lineNum = 1;
            int loadedCount = 0;
            String line;
            
            while ((line = reader.readLine()) != null) {
                lineNum++;
                try {
                    String[] columns = line.split(",");
                    if (columns.length <= Math.max(idIndex, attrIndex)) {
                        logger.warn("Line {} has too few columns (expected at least {}): {}", 
                            lineNum, Math.max(idIndex, attrIndex) + 1, line);
                        continue;
                    }
                    
                    long wayId = Long.parseLong(columns[idIndex].trim());
                    String attr = columns[attrIndex].trim().toLowerCase();
                    
                    byte present = (byte) (trueValues.stream()
                        .anyMatch(v -> v.equalsIgnoreCase(attr)) ? 1 : 0);
                    
                    map.put(wayId, present);
                    loadedCount++;
                    
                    // Log first few entries for verification
                    if (loadedCount <= 5) {
                        logger.info("Loaded: wayId={}, attribute='{}', present={}", wayId, attr, present);
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Line {} has invalid way_id: {}", lineNum, line);
                } catch (Exception e) {
                    logger.warn("Line {} could not be parsed: {} - {}", lineNum, line, e.getMessage());
                }
            }
            
            logger.info("Successfully loaded {} OSM ways from CSV: {}", loadedCount, path);
            logger.info("Default value for missing ways: {}", map.defaultReturnValue());
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to load CSV: " + path, e);
        }
        return map;
    }
    
    /**
     * Legacy-Methode mit Defaults für Rückwärtskompatibilität
     */
    public static Long2ByteOpenHashMap load(Path path) {
        return load(path, "way_id", "attribute", List.of("pano", "regular"));
    }
}
