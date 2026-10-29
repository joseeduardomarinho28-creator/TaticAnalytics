package com.taticanalytics.validation;

import com.taticanalytics.util.MatchConstants;

// PURPOSE (TA-76):
// Decides what to do with an (x, y) position read from the tracking data, based on where it falls
// in relation to the pitch. The tracking loaders call this class for every entity, so the rule lives
// in ONE place instead of being copied into each loader (a copy could easily drift out of sync).
//
// DESIGN PATTERN: Utility class (same idea as `MatchConstants`)
// - `final`: nobody can extend it.
// - `private` constructor: nobody can write `new CoordinateValidator()`.
// - `static` methods: they are called directly on the class, e.g. `CoordinateValidator.clamp(...)`.
// This fits because the class holds NO state: the same input always gives the same output,
// and counting how many values were clamped or discarded is the job of the caller.
public final class CoordinateValidator {

    private CoordinateValidator() {}

    // PURPOSE:
    // Keeps `value` inside the range [0, max]: a value above `max` becomes `max`, a value below 0
    // becomes 0, and a value already inside the range is returned unchanged.
    // Examples: clamp(50.0, 105.0) = 50.0 | clamp(105.4, 105.0) = 105.0 | clamp(-1.3, 105.0) = 0.0
    //
    // JAVA CONCEPT: nesting `Math.min` and `Math.max`
    // `Math.min(value, max)` caps the TOP: the result can never be bigger than `max`.
    // `Math.max(0.0, ...)` caps the BOTTOM: the result can never be smaller than 0.
    // The inner call runs first and its result is passed to the outer one.
    //
    // SYNTAX: `value` and `max` are PARAMETERS: placeholders that receive the values of each call.
    // Inside the method we use their NAMES, never fixed numbers.
    public static double clamp(double value, double max) {
        return Math.max(0.0, Math.min(value, max));
    }

    // PURPOSE:
    // Tells WHERE the point (x, y) is: inside the pitch, outside but within the tolerance margin,
    // or too far away. It only classifies; what to do with each answer (keep, clamp or discard
    // the entity) is decided by the caller.
    //
    // EXECUTION FLOW: the ORDER of the checks matters.
    public static CoordinateStatus classify(double x, double y) {

        // 1) NaN ("not a number") goes first.
        // GOTCHA: EVERY comparison with NaN is false (`NaN < 0` and `NaN > 0` are both false).
        // If it were not caught here, it would slip through all the checks below and end up
        // classified as a valid position. `Double.isNaN` is the only reliable way to detect it.
        // (Infinity does not have this problem: `Infinity > 107` is true, so check 2 catches it.)
        if (Double.isNaN(x) || Double.isNaN(y)) {
            return CoordinateStatus.OUT_OF_RANGE;
        }

        // 2) Too far outside the pitch: further than the margin on at least one side.
        // JAVA CONCEPT: `||` (OR) is true when at least ONE side is true. The accepted range
        // is [-margin, length + margin] for x and [-margin, width + margin] for y.
        // The limits come from `MatchConstants`, so there are no "magic numbers" here.
        boolean xTooFar = x < -MatchConstants.COORDINATE_MARGIN
                || x > MatchConstants.FIELD_LENGTH + MatchConstants.COORDINATE_MARGIN;
        boolean yTooFar = y < -MatchConstants.COORDINATE_MARGIN
                || y > MatchConstants.FIELD_WIDTH + MatchConstants.COORDINATE_MARGIN;

        if (xTooFar || yTooFar) {
            return CoordinateStatus.OUT_OF_RANGE;
        }

        // 3) Inside the pitch: BOTH coordinates must be inside.
        // JAVA CONCEPT: `&&` (AND) is true only when ALL sides are true.
        // The edges count as inside (`>=` and `<=`), so (105.0, 68.0) is still INSIDE.
        boolean xInside = x >= 0.0 && x <= MatchConstants.FIELD_LENGTH;
        boolean yInside = y >= 0.0 && y <= MatchConstants.FIELD_WIDTH;

        if (xInside && yInside) {
            return CoordinateStatus.INSIDE;
        }

        // 4) Everything left is outside the pitch, but no further than the margin:
        // measurement noise, which the caller will fix with `clamp`.
        return CoordinateStatus.WITHIN_MARGIN;
    }
}