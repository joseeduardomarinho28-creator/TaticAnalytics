package com.taticanalytics.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.taticanalytics.model.FrameData;
import com.taticanalytics.model.HeatmapGrid;
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

        Integer currentPossessorId = null;
        double previousTimestamp = 0.0;
        int previousFrameId = -1;
            
        for (FrameData frame : frames) {
            Ball ball = findBall(frame);

            if (ball != null) {
                if ((previousFrameId != -1) && (frame.getFrameId() - previousFrameId) == 1) {
                    double deltaTime = frame.getTimestamp() - previousTimestamp;

                    if (currentPossessorId != null && deltaTime > 0.0) {
                        possessionMap.merge(currentPossessorId, deltaTime, Double::sum);
                    }
                }
                ClosestPlayerResult closestResult = findClosestPlayer(frame, ball);
                
                if (closestResult.player() != null && closestResult.distance() <= radiusMeters) {
                    currentPossessorId = closestResult.player().getId();
                } else {
                    currentPossessorId = null;
                }

            } else {
                currentPossessorId = null;
            }

            previousFrameId = frame.getFrameId();
            previousTimestamp = frame.getTimestamp();
        }
        return possessionMap;
    }

    public Map<Integer, Double> calculatePossessionTimePerTeam(List<FrameData> frames, double radiusMeters) {
        Map<Integer, Double> teamPossessionMap = new HashMap<>();

        Integer currentPossessorTeam = null;
        double previousTimestamp = 0.0;
        int previousFrameId = -1;
            
        for (FrameData frame : frames) {
            Ball ball = findBall(frame);

            if (ball != null) {
                if ((previousFrameId != -1) && (frame.getFrameId() - previousFrameId) == 1) {
                    double deltaTime = frame.getTimestamp() - previousTimestamp;

                    if (currentPossessorTeam != null && deltaTime > 0.0) {
                        teamPossessionMap.merge(currentPossessorTeam, deltaTime, Double::sum);
                    }
                }

                ClosestPlayerResult closestResult = findClosestPlayer(frame, ball);

                if (closestResult.player() != null && closestResult.distance() <= radiusMeters) {
                    currentPossessorTeam = closestResult.player().getTeamId();
                } else {
                    currentPossessorTeam = null;
                }
            } else {
                currentPossessorTeam = null;
            }

            previousFrameId = frame.getFrameId();
            previousTimestamp = frame.getTimestamp();
        }
        return teamPossessionMap;
    }

    public HeatmapGrid generatePlayerHeatmap(List<FrameData> frames, int entityId, int rows, int cols, double fieldWidth, double fieldHeight) {
        HeatmapGrid heatmap = new HeatmapGrid(rows, cols, fieldWidth, fieldHeight);

        Entity previousEntity = null;
        int previousFrameId = -1;
        double previousTimestamp = 0.0;

        for (FrameData frame : frames) {

            for (Entity entity : frame.getEntities()) {

                if (entityId == entity.getId()) {

                     if ((previousEntity != null) && (frame.getFrameId() - previousFrameId == 1)) {
                        double deltaTime = frame.getTimestamp() - previousTimestamp;

                        if (deltaTime > 0.0) {
                            heatmap.addTime(previousEntity.getX(), previousEntity.getY(), deltaTime);
                        }
                     }
                    previousEntity = entity;
                    previousFrameId = frame.getFrameId();
                    previousTimestamp = frame.getTimestamp();
                }
            }
        }
        return heatmap;
    }

    private Ball findBall (FrameData frame) {
         for (Entity entity : frame.getEntities()) {

            if (entity instanceof Ball b) {
                return b;
            }
        }
        return null;
    }
    
    private ClosestPlayerResult findClosestPlayer(FrameData frame, Ball ball) {
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
        return new ClosestPlayerResult(closestPlayer, minDistance);
    }

    private record ClosestPlayerResult(Player player, double distance) {}
}
