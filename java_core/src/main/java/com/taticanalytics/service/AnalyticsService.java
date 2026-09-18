package com.taticanalytics.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Domain model imports
import com.taticanalytics.model.FrameData;
import com.taticanalytics.model.HeatmapGrid;
import com.taticanalytics.model.Entity;
import com.taticanalytics.model.PlayerStats;
import com.taticanalytics.model.Ball;
import com.taticanalytics.model.Player;
import com.taticanalytics.util.MatchConstants;

// LIBRARY & FRAMEWORK: Spring Boot
import org.springframework.stereotype.Service;

// PURPOSE:
// This is the core "Business Logic" class of our application.
// While the classes in the `model` package just hold data, this `service` class 
// actually DOES the math (kinematics, heatmaps, and possession calculations).
//
// SPRING BOOT CONCEPT: @Service annotation
// By placing @Service here, we tell the Spring Framework: 
// "Hey Spring, please create exactly one instance (a Singleton) of this class 
// when the app starts, and automatically hand it to anyone who needs it."
// This is called Dependency Injection (Inversion of Control).
@Service
public class AnalyticsService {

    // =====================================================================
    // 1. KINEMATIC CALCULATIONS (Distance & Speed)
    // =====================================================================
    
    // Calculates total distance and max speed for a specific entity across all frames.
    public PlayerStats calculatePlayerStats(List<FrameData> frames, int entityId) {
        double totalDistance = 0.0;
        double maxSpeed = 0.0;
        
        // State variables to remember the "past" while we iterate through the frames.
        Entity previousEntity = null;
        int previousFrameId = -1;
        double previousTimestamp = 0.0;
        double deltaTime = 0.0;
        double currentSpeed = 0.0;

        // Iterate sequentially through time
        for (FrameData frame : frames) {
            for (Entity entity : frame.getEntities()) {
                
                // If this is the player we are looking for...
                if (entityId == entity.getId()) {

                    // LOGIC CONCEPT: Frame Continuity Check
                    // Why `frame.getFrameId() - previousFrameId == 1`?
                    // Sometimes players are occluded (hidden behind someone else) and the 
                    // camera misses them for a few frames. If we jump from frame 10 to frame 50, 
                    // drawing a straight line between those points would result in fake, massive 
                    // speed spikes (e.g., "teleportation"). We ONLY calculate math between 
                    // adjacent, consecutive frames.
                    if ((previousEntity != null) && (frame.getFrameId() - previousFrameId) == 1) {
                        
                        // MATH: Euclidean Distance Formula (Pythagorean theorem)
                        // c = √(a² + b²)
                        double dx = entity.getX() - previousEntity.getX();
                        double dy = entity.getY() - previousEntity.getY();
                        double stepDistance = Math.sqrt(dx * dx + dy * dy);
                        
                        totalDistance += stepDistance;
                        deltaTime = frame.getTimestamp() - previousTimestamp;

                        // MATH: Speed calculation (v = d/t)
                        if (deltaTime > 0.0) {
                            currentSpeed = stepDistance / deltaTime;

                            // Update maximum speed if current is higher
                            if (currentSpeed > maxSpeed) {
                                maxSpeed = currentSpeed;
                            }
                        }
                    }
                    
                    // Update state for the next loop iteration
                    previousEntity = entity;
                    previousFrameId = frame.getFrameId();
                    previousTimestamp = frame.getTimestamp();
                }
            }
        }
        return new PlayerStats(totalDistance, maxSpeed);
    }

    // =====================================================================
    // 2. BALL POSSESSION (Player and Team level)
    // =====================================================================

    public Map<Integer, Double> calculatePossessionTimePerPlayer(List<FrameData> frames, double radiusMeters) {
        // JAVA CONCEPT: Map & HashMap
        // A Map is a dictionary of Key-Value pairs. Here, the Key is the Player ID (Integer) 
        // and the Value is their total possession time in seconds (Double).
        Map<Integer, Double> possessionMap = new HashMap<>();

        Integer currentPossessorId = null;
        double previousTimestamp = 0.0;
        int previousFrameId = -1;
            
        for (FrameData frame : frames) {
            Ball ball = findBall(frame);

            if (ball != null) {
                
                // LOGIC CONCEPT: Avoiding "Future Bias"
                // This is a subtle but critical piece of logic.
                // We calculate `deltaTime` since the last frame. But who gets credit for that time?
                // The player who had the ball in the PAST (`currentPossessorId`). 
                // We MUST award the time BEFORE we update the state to the new possessor. 
                // If we check who has the ball now, and award them the past 1 second of time, 
                // we are predicting the future, which ruins the analytics.
                if ((previousFrameId != -1) && (frame.getFrameId() - previousFrameId) == 1) {
                    double deltaTime = frame.getTimestamp() - previousTimestamp;

                    if (currentPossessorId != null && deltaTime > 0.0) {
                        // JAVA CONCEPT: Map.merge()
                        // A clean way to say: "Add deltaTime to currentPossessorId. 
                        // If currentPossessorId doesn't exist yet, start it at deltaTime."
                        possessionMap.merge(currentPossessorId, deltaTime, Double::sum);
                    }
                }
                
                // Now that the past time is accounted for, who has the ball RIGHT NOW?
                ClosestPlayerResult closestResult = findClosestPlayer(frame, ball);
                
                // Check if the closest player is within the acceptable control radius
                if (closestResult.player() != null && closestResult.distance() <= radiusMeters) {
                    currentPossessorId = closestResult.player().getId();
                } else {
                    // Ball is loose or contested
                    currentPossessorId = null;
                }

            } else {
                // Ball is out of bounds or occluded
                currentPossessorId = null;
            }

            previousFrameId = frame.getFrameId();
            previousTimestamp = frame.getTimestamp();
        }
        return possessionMap;
    }

    // OOP CONCEPT: Method Overloading
    // We have two methods with the exact same name, but different parameters.
    // This provides a convenient default. If the user doesn't specify a `radiusMeters`, 
    // we call the main method using the default constant.
    public Map<Integer, Double> calculatePossessionTimePerPlayer(List<FrameData> frames) {
        return calculatePossessionTimePerPlayer(frames, MatchConstants.BALL_CONTROL_RADIUS);
    }

    // Calculates Team possession using the exact same logic as Player possession above.
    // The only difference is we track `TeamId` instead of `PlayerId`.
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

    public Map<Integer, Double> calculatePossessionTimePerTeam(List<FrameData> frames) {
        return calculatePossessionTimePerTeam(frames, MatchConstants.BALL_CONTROL_RADIUS);
    }

    // =====================================================================
    // 3. HEATMAP GENERATION
    // =====================================================================

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
                            // Note: We add time to the cell the player was in DURING this time interval.
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

    public HeatmapGrid generatePlayerHeatmap(List<FrameData> frames, int entityId) {
        return generatePlayerHeatmap(frames, entityId, MatchConstants.DEFAULT_GRID_ROWS, MatchConstants.DEFAULT_GRID_COLS, MatchConstants.FIELD_LENGTH, MatchConstants.FIELD_WIDTH);
    }

    // =====================================================================
    // 4. PRIVATE UTILITY METHODS (Encapsulated Logic)
    // =====================================================================

    // JAVA CONCEPT: Pattern Matching for instanceof (Java 16+)
    // `instanceof` checks if an object belongs to a specific class. 
    // Historically, you had to check it, then cast it on the next line.
    // Now, `entity instanceof Ball b` does both! If `entity` is a `Ball`, it creates a 
    // new variable `b` automatically cast to `Ball` so you can use it immediately.
    private Ball findBall (FrameData frame) {
         for (Entity entity : frame.getEntities()) {
            if (entity instanceof Ball b) {
                return b;
            }
        }
        return null; // Return null if the ball is occluded/missing in this frame
    }
    
    // Finds which player is physically closest to the ball.
    private ClosestPlayerResult findClosestPlayer(FrameData frame, Ball ball) {
        Player closestPlayer = null;
        // Start the minimum distance at the highest possible number in Java.
        double minDistance = Double.MAX_VALUE;

        for (Entity entity : frame.getEntities()) {
            if (entity instanceof Player player) {
                // Calculate distance between player and ball
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

    // JAVA CONCEPT: Records (Java 14+)
    // A `record` is a special, ultra-compact class designed exclusively to hold immutable data. 
    // It automatically generates the constructor, getters, `equals()`, and `toString()` methods!
    // We use it here because `findClosestPlayer` needs to return TWO pieces of information 
    // (the Player AND the distance), and Java methods can normally only return one thing.
    private record ClosestPlayerResult(Player player, double distance) {}
}