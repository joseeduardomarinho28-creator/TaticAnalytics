package com.taticanalytics.model;

public class Ball extends Entity {
    private double speed;

    public Ball(int id, double x, double y, double speed) {
        super (id, x, y);
        this.speed = speed;
    }

    public double getSpeed() {
        return this.speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }
}