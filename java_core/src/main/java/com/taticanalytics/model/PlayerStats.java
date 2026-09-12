package com.taticanalytics.model;

public class PlayerStats {
    private final double totalDistance;
    private final double maxSpeed;

    public PlayerStats(double totalDistance, double maxSpeed) {
        this.totalDistance = totalDistance;
        this.maxSpeed = maxSpeed;
    }

    public double getTotalDistance() {
        return this.totalDistance;
    }

    public double getMaxSpeed() {
        return this.maxSpeed;
    }

    public double getMaxSpeedKmh() {
        return this.maxSpeed * 3.6;
    }
}