package com.taticanalytics.model;

// PURPOSE:
// This class acts as a data container (often called a Data Transfer Object or Value Object).
// It encapsulates the final calculated results of a player's performance. 
// Instead of a method returning an array of raw numbers, it returns this structured object.
public class PlayerStats {
    
    // SYNTAX & OOP CONCEPT: Immutability (`final` keyword)
    // The `final` keyword means that once this variable is assigned a value, 
    // it can NEVER be changed again. 
    // By making the fields `private` AND `final`, we make this entire object "Immutable" (read-only).
    //
    // WHY IMMUTABLE?
    // Because this object represents a historical calculation result. Once the `AnalyticsService` 
    // calculates that a player ran 10km, no other part of the system (like the REST API or UI) 
    // should be allowed to accidentally (or maliciously) change that statistic to 12km.
    private final double totalDistance;
    private final double maxSpeed;

    // OOP CONCEPT: Constructor and Final Field Initialization
    // When fields are marked as `final` without an initial value (like `= 0.0`), 
    // Java strictly requires that they are assigned a value EXACTLY ONCE inside the constructor.
    // If you forget to assign one of them here, the code will not compile.
    public PlayerStats(double totalDistance, double maxSpeed) {
        this.totalDistance = totalDistance;
        this.maxSpeed = maxSpeed;
    }

    // OOP CONCEPT: Getters (Read-Only Access)
    // Because there are no Setter methods in this class, the outside world can 
    // only read the data, reinforcing the object's immutability.
    public double getTotalDistance() {
        return this.totalDistance;
    }

    public double getMaxSpeed() {
        return this.maxSpeed;
    }

    // OOP CONCEPT: Calculated Properties (Derived Data)
    // This is a fantastic design pattern. Instead of storing a third field in memory 
    // for `maxSpeedKmh` (which would take up more RAM and risk getting out of sync with `maxSpeed`),
    // we simply calculate it on the fly whenever someone asks for it.
    // 
    // MATH NOTE: 
    // Tracking data usually provides speed in meters per second (m/s). 
    // Multiplying m/s by 3.6 converts it to kilometers per hour (km/h).
    public double getMaxSpeedKmh() {
        return this.maxSpeed * 3.6;
    }
}