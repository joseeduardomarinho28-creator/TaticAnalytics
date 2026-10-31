package com.taticanalytics.service.strategy;

import com.taticanalytics.util.MatchConstants;

// PURPOSE (TA-77):
// Bundles ALL the inputs that any metric calculation may need, so every strategy can have the SAME method
// signature: `calculate(frames, params)`. Each strategy reads only the fields it cares about:
//   - distance/speed -> `entityId`;
//   - possession     -> `radiusMeters`;
//   - heatmap        -> `entityId`, `rows`, `cols`, `fieldWidth`, `fieldHeight`.
//
// DESIGN PATTERN: Parameter Object
// Without it, each strategy would need a different list of parameters, and a single interface method
// could not describe all of them. Putting the parameters in one object keeps the interface small and
// lets us add a new parameter later without changing the signature of every strategy.
//
// JAVA CONCEPT: Records (Java 16+)
// A `record` is a compact immutable class: the compiler generates the constructor, the accessors
// (`params.entityId()`, `params.rows()`...), `equals`, `hashCode` and `toString`.
//
// JAVA CONCEPT: static factory methods
// A record has one big constructor with 6 values, and it would be easy to mix up their order.
// The `forPlayer`, `forPossession` and `forHeatmap` methods build the object for a specific use and fill the
// fields that the use does not need with harmless defaults. The name of the method tells WHAT is being built,
// which is much clearer than `new MetricParams(7, 2.0, 10, 15, 105.0, 68.0)`.
public record MetricParams(
    int entityId,
    double radiusMeters,
    int rows,
    int cols,
    double fieldWidth,
    double fieldHeight
) {

    // The entity id is irrelevant for possession: the value -1 is never a real id (ids are 0 or more).
    private static final int NO_ENTITY = -1;

    // Parameters for a distance/speed calculation: only the player matters.
    public static MetricParams forPlayer(int entityId) {
        return new MetricParams(entityId, MatchConstants.BALL_CONTROL_RADIUS,
            MatchConstants.DEFAULT_GRID_ROWS, MatchConstants.DEFAULT_GRID_COLS,
            MatchConstants.FIELD_LENGTH, MatchConstants.FIELD_WIDTH);
    }

    // Parameters for a possession calculation: only the control radius matters.
    public static MetricParams forPossession(double radiusMeters) {
        return new MetricParams(NO_ENTITY, radiusMeters,
            MatchConstants.DEFAULT_GRID_ROWS, MatchConstants.DEFAULT_GRID_COLS,
            MatchConstants.FIELD_LENGTH, MatchConstants.FIELD_WIDTH);
    }

    // Parameters for a heatmap: the player and the grid/field dimensions matter.
    public static MetricParams forHeatmap(int entityId, int rows, int cols, double fieldWidth, double fieldHeight) {
        return new MetricParams(entityId, MatchConstants.BALL_CONTROL_RADIUS, rows, cols, fieldWidth, fieldHeight);
    }
}