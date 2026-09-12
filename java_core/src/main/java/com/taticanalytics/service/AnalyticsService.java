package com.taticanalytics.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.taticanalytics.model.FrameData;
import com.taticanalytics.model.Entity;
import com.taticanalytics.model.PlayerStats;
import com.taticanalytics.model.Ball;
import com.taticanalytics.model.Player;

public class AnalyticsService {
    public PlayerStats calculatePlayerStats(List<FrameData> frames, int entityId) {
        double totalDistance = 0.0;
        Entity previousEntity = null;
        int previousFrameId = -1;
        double previousTimestamp = 0.0;
        double maxSpeed = 0.0;
        double deltaTime = 0.0;
        double currentSpeed = 0.0;

        for (FrameData frame : frames) {

            for (Entity entity : frame.getEntities()) {

                if (entityId == entity.getId()) {

                    if ((previousEntity != null) && (frame.getFrameId() - previousFrameId) == 1) {
                        double dx = entity.getX() - previousEntity.getX();
                        double dy = entity.getY() - previousEntity.getY();
                        double stepDistance = Math.sqrt(dx * dx + dy * dy);
                        totalDistance += stepDistance;
                        deltaTime = frame.getTimestamp() - previousTimestamp;

                        if (deltaTime > 0.0) {
                            currentSpeed = stepDistance / deltaTime;

                            if (currentSpeed > maxSpeed) {
                                maxSpeed = currentSpeed;
                            }
                        }
                    }
                    previousEntity = entity;
                    previousFrameId = frame.getFrameId();
                    previousTimestamp = frame.getTimestamp();

                }
            }
        }

        return new PlayerStats(totalDistance, maxSpeed);
    }


    public Map<Integer, Double> calculatePossessionTimePerPlayer(List<FrameData> frames, double radiusMeters) {
        Map<Integer, Double> possessionMap = new HashMap<>();

        double previousTimestamp = 0.0;
        int previousFrameId = -1;
            
        for (FrameData frame : frames) {
                            Ball ball = null;

            for (Entity entity : frame.getEntities()) {

                if (entity instanceof Ball b) {
                    ball = b;
                    break;
                }
            }

            if (ball != null) {
                Player closestPlayer = null;
                double minDistance = Double.MAX_VALUE;

                for (Entity entity : frame.getEntities()) {

                    if (entity instanceof Player player) {
                        double dx = player.getX() - ball.getX();
                        double dy = player.getY() - ball.getY();
                        double distance = Math.sqrt(dx * dx + dy * dy);

                        if (distance < minDistance) {
                            minDistance = distance;
                            closestPlayer = player;
                        }
                    }
                }
                if ((previousFrameId != -1) && (frame.getFrameId() - previousFrameId) == 1) {
                    double deltaTime = frame.getTimestamp() - previousTimestamp;

                    if ((closestPlayer != null) && (minDistance <= radiusMeters && deltaTime > 0.0)) {
                        possessionMap.merge(closestPlayer.getId(), deltaTime, Double::sum);
                    }
                }
            }
        previousFrameId = frame.getFrameId();
        previousTimestamp = frame.getTimestamp();
        }
        return possessionMap;
    }
}