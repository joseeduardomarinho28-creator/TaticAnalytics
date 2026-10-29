package com.taticanalytics.util;

// PURPOSE:
// This is a "Utility / Constants Class". Its sole job is to hold fixed global 
// configuration values (dimensions, thresholds) so we avoid scattering "magic numbers" 
// (like raw numbers `105.0` or `2.0`) throughout our business logic.
//
// DESIGN PATTERN: Utility / Constant Holder
// We mark the class as `final` because we never want anyone to inherit/extend it.
public final class MatchConstants {

    // DESIGN PATTERN: Preventing Instantiation
    // Because all constants here are `static`, you never need to create an object 
    // like `new MatchConstants()`. To enforce this architectural rule, we define a 
    // `private` constructor. If another class tries `new MatchConstants()`, 
    // Java will throw a compile-time error.
    private MatchConstants() {}

    // JAVA CONCEPT: Constants (`public static final`)
    // - `public`: Accessible from any other package/class in the project.
    // - `static`: Belongs to the Class itself, NOT to an object instance. 
    //   Accessed via `MatchConstants.FIELD_LENGTH`.
    // - `final`: Value cannot be changed once assigned (immutable constant).
    // CONVENTION: Constants in Java are traditionally written in ALL_CAPS with underscores (SCREAMING_SNAKE_CASE).
    
    // Pitch dimensions in meters (standard FIFA pitch approximation: 105x68 meters)
    public static final double FIELD_LENGTH = 105.0;
    public static final double FIELD_WIDTH = 68.0;
    
    // Maximum distance in meters a player can be from the ball to be credited with control/possession
    public static final double BALL_CONTROL_RADIUS = 2.0;

    // Default resolution/granularity for spatial heatmaps (10 rows, 15 columns of grid cells)
    public static final int DEFAULT_GRID_ROWS = 10;
    public static final int DEFAULT_GRID_COLS = 15;

    // TA-76: Measurement tolerance, in meters, for coordinates that fall slightly outside the pitch.
    // The CV pipeline estimates positions from video, so a player standing on the touchline can be
    // reported as x = 105.4 purely because of measurement noise. The coordinate validator uses this
    // margin to tell noise apart from a truly broken value:
    //   - up to this far outside the pitch  -> the value is CLAMPED to the nearest edge and kept;
    //   - farther than this                 -> the entity is DISCARDED with a WARN, because the
    //                                          value can no longer be trusted (e.g. broken calibration).
    // With the 105x68 pitch above, the accepted range is therefore x in [-2, 107] and y in [-2, 70].
    //
    // LEARNING NOTE: this has the same VALUE as BALL_CONTROL_RADIUS but a different MEANING
    // (a measurement tolerance, not a distance to the ball). They are kept as two separate constants
    // on purpose, so tuning one of them never silently changes the other. Never reuse a constant
    // just because its number happens to match: name it after what it MEANS.
    public static final double COORDINATE_MARGIN = 2.0;

    // TA-76: Share of the entity readings in ONE tracking file that may need correction
    // (clamped or discarded) before the loader logs an ALERT. 0.05 means 5%.
    // A few corrections are normal measurement noise. When more than 5% of the readings are
    // affected, the problem is probably systematic (e.g. a wrong camera calibration in the CV
    // pipeline), so someone should look at the data source instead of trusting the "fixed" values.
    public static final double COORDINATE_ALERT_RATIO = 0.05;
}