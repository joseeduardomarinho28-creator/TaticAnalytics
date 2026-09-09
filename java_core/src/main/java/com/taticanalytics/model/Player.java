package com.taticanalytics.model;

public class Player extends Entity {
    private String team;
    private int number;

    public Player(int id, double x, double y, String team, int number) {
        super(id, x, y);
        this.team = team;
        this.number = number;
    }

    public String getTeam() {
        return this.team;
    }

    public int getNumber() {
        return this.number;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    public void setNumber(int number) {
        this.number = number;
    }
    
}