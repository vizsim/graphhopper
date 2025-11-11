package com.graphhopper.reader.osm.custom;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.IntsRef;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;

public class CustomPresentParser implements TagParser {
    private final BooleanEncodedValue ev;
    private final Long2ByteOpenHashMap map;

    public CustomPresentParser(BooleanEncodedValue ev, Long2ByteOpenHashMap map) {
        this.ev = ev;
        this.map = map;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        boolean present = map.get(way.getId()) == 1;
        
        // Da storeTwoDirections=false, gilt der Wert automatisch für beide Richtungen
        ev.setBool(false, edgeId, edgeIntAccess, present);
    }
}

