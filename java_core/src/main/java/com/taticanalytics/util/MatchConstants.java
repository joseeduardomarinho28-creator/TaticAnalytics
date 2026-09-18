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
}