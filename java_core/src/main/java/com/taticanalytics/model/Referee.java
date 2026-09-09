package com.taticanalytics.model;

public class Referee extends Entity {
    private String role;

    public Referee(int id, double x, double y, String role) {
        super(id, x, y);
        this.role = role;
    }

    public String getRole() {
        return this.role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}