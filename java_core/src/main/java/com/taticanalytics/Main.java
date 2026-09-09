package com.taticanalytics;

import com.taticanalytics.model.Player;

public class Main {
    public static void main(String[] args) {
        // 1. Creating a Player object (triggers the Constructor)
        Player p1 = new Player(1, 12.5, 45.0, "Flamengo", 10);

        // 2. Using Getters to read the data (inherited + own)
        System.out.println("--- PLAYER DATA ---");
        System.out.println("ID: " + p1.getId());
        System.out.println("Shirt: " + p1.getNumber());
        System.out.println("Team: " + p1.getTeam());
        System.out.println("Position X/Y: " + p1.getX() + " / " + p1.getY());

        // 3. Using a Setter to update the X position (Entity method)
        p1.setX(15.0);
        System.out.println("New Position X: " + p1.getX());
    }
}