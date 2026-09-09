package com.taticanalytics.model;

public abstract class Entity {
    protected int id;
    protected double x;
    protected double y;

    public Entity(int id, double x, double y) {
        this.id = id;
        this.x = x;
        this.y = y;
    }
    
    public int getId() {
        return this.id;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }
}

