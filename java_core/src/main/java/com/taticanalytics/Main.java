package com.taticanalytics;

import com.taticanalytics.model.Player;
import com.taticanalytics.model.Ball;
import com.taticanalytics.model.Referee;

public class Main {
    public static void main(String[] args) {
        // 1. Creating a Player object (triggers the Constructor)
        Player p1 = new Player(1, 12.5, 45.0, "Flamengo", 10);
        Ball b = new Ball(0, 20.2, 37.5, 10.0);
        Referee ref = new Referee(100, 25.1, 23.8, "Main");

        // 2. Using Getters to read the data (inherited + own)
        System.out.println("--- PLAYER DATA ---");
        System.out.println("ID: " + p1.getId());
        System.out.println("Shirt: " + p1.getNumber());
        System.out.println("Team: " + p1.getTeam());
        System.out.println("Position X/Y: " + p1.getX() + " / " + p1.getY());
        System.out.println("--- BALL DATA ---");
        System.out.println("ID: " + b.getId());
        System.out.println("Position X/Y: " + b.getX() + " / " + b.getY());
        System.out.println("Speed : " + b.getSpeed());
        System.out.println("--- REFEREE DATA ---");
        System.out.println("ID: " + ref.getId());
        System.out.println("Position X/Y: " + ref.getX() + " / " + ref.getY());
        System.out.println("Role : " + ref.getRole());

        // 3. Using a Setter to update the X position (Entity method)
        p1.setX(15.0);
        System.out.println("New Player Position X: " + p1.getX());
        b.setSpeed(25.0);
        System.out.println("New Ball Speed: " + b.getSpeed());
        ref.setRole("Chief");
        System.out.println("New Referee Role: " + ref.getRole());
    }
}