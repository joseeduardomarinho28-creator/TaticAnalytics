package com.taticanalytics.model;

public class Player extends Entity {
    private int teamId;

    public Player(int id, double x, double y, int teamId) {
        super(id, x, y);
        this.teamId = teamId;
    }

    public int getTeamId() {
        return this.teamId;
    }

    public void setTeam(int teamId) {
        this.teamId = teamId;
    }
    
}