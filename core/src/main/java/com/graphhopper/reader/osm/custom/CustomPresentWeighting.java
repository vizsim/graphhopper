package com.graphhopper.reader.osm.custom;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.weighting.SpeedWeighting;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;

public class CustomPresentWeighting implements Weighting {
    private final SpeedWeighting baseWeighting;
    private final BooleanEncodedValue ev;
    private final double missingFactor;
    private final long missingPenaltyMillis;

    public CustomPresentWeighting(EncodedValueLookup lookup, PMap hints) {
        DecimalEncodedValue speedEnc = lookup.getDecimalEncodedValue("car_average_speed");
        this.baseWeighting = new SpeedWeighting(speedEnc, TurnCostProvider.NO_TURN_COST_PROVIDER);
        this.ev = lookup.getBooleanEncodedValue("custom_present");
        // Tuning aus config.yml/hints (Fallbacks sinnvoll wählen)
        this.missingFactor = hints.getDouble("missing_penalty_factor", 1.0);     // z.B. 1.2
        this.missingPenaltyMillis = (long) (hints.getDouble("missing_penalty_seconds", 0.0) * 1000);
    }

    @Override
    public double calcMinWeightPerDistance() {
        return baseWeighting.calcMinWeightPerDistance();
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
        double w = baseWeighting.calcEdgeWeight(edgeState, reverse);
        boolean present = edgeState.get(ev);
        if (!present) w *= Math.max(1.0, missingFactor);
        return w;
    }

    @Override
    public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
        long ms = baseWeighting.calcEdgeMillis(edgeState, reverse);
        boolean present = edgeState.get(ev);
        if (!present) ms += Math.max(0, missingPenaltyMillis);
        return ms;
    }

    @Override
    public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
        return baseWeighting.calcTurnWeight(inEdge, viaNode, outEdge);
    }

    @Override
    public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
        return baseWeighting.calcTurnMillis(inEdge, viaNode, outEdge);
    }

    @Override
    public boolean hasTurnCosts() {
        return baseWeighting.hasTurnCosts();
    }

    @Override
    public String getName() {
        return "custom_present";
    }
}
