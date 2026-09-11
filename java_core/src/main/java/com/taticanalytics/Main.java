package com.taticanalytics;

import com.taticanalytics.service.TrackingDataLoader;
import java.util.List;
import com.taticanalytics.model.FrameData;
import com.taticanalytics.model.Entity;
import com.taticanalytics.model.Player;
import com.taticanalytics.model.Referee;
import com.taticanalytics.model.Ball;
import com.taticanalytics.service.AnalyticsService;

public class Main {
    public static void main(String[] args) {
        TrackingDataLoader loader = new TrackingDataLoader();
        AnalyticsService analyticsService = new AnalyticsService();

        List<FrameData> frames = loader.loadData("src/main/resources/tracking_sample.json");

        System.out.println("Frames : " + frames.size());

        System.out.println("--------------------------------------------------");

        for (FrameData frame : frames) {
            System.out.println("Frame ID: " + frame.getFrameId() + " | Time: " + frame.getTimestamp() + "s");

            for (Entity entity : frame.getEntities()) {

                 if (entity instanceof Player player) {
                System.out.println("[PLAYER] ID: " + player.getId() + " | Team: " + player.getTeamId() + " | Position: (" + player.getX() + ", " + player.getY() + ")");
            }

            else if (entity instanceof Ball ball) {
                System.out.println("[BALL] ID: " + ball.getId() + " | Position: (" + ball.getX() + ", " + ball.getY() + ")");
            }

            else if (entity instanceof Referee referee) {
                System.out.println("[REFEREE] ID: " + referee.getId() + " | Position: (" + referee.getX() + ", " + referee.getY() + ")");
            }
            }
        }

        System.out.println("--------------------------------------------------");
        double distancePlayer1 = analyticsService.calculateTotalDistance(frames, 1);
        System.out.printf("total distance of the player 1: %.2f meters%n", distancePlayer1);

    }
}