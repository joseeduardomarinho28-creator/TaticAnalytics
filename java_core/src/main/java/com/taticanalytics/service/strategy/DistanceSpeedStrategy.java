package com.taticanalytics.service.strategy;

import java.util.List;

import com.taticanalytics.model.Entity;
import com.taticanalytics.model.FrameData;
import com.taticanalytics.model.Player;
import com.taticanalytics.model.PlayerStats;

// PURPOSE (TA-77):
// The distance and maximum speed algorithm, moved out of `AnalyticsService.calculatePlayerStats` into its own
// class. The logic is EXACTLY the same as before: only its home changed. Because it is a Strategy, the service
// no longer needs to know how the distance is computed, it just calls `calculate`.
//
// JAVA CONCEPT: `implements` and generics
// `implements MetricCalculationStrategy<PlayerStats>` is a promise: "this class provides everything the
// interface demands". The interface has a placeholder `T` for the result type, and here it is replaced by
// `PlayerStats`, so `calculate` must return a `PlayerStats`.
public class DistanceSpeedStrategy implements MetricCalculationStrategy<PlayerStats> {

    // JAVA CONCEPT: `@Override`
    // Marks this method as the implementation of a method declared in the interface. It is optional, but the
    // compiler then checks the promise: if the name or the parameters do not match the interface, it fails
    // instead of silently creating a different method.
    @Override
    public PlayerStats calculate(List<FrameData> frames, MetricParams params) {

        // The old method received the id as a parameter (`int entityId`). Now everything arrives packed in
        // `params`, so we take the id out of it. The rest of the body can stay exactly as it was.
        int entityId = params.entityId();

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
                //
                // LOGIC CONCEPT: Match by TYPE and id, not by id alone (TA-107)
                // `Player`, `Referee` and `Ball` are all `Entity` objects, and each one has an `id`.
                // But ids are only unique among entities of the SAME type: the ball is always 0,
                // and a tracker can easily give a player an id that a referee also uses (e.g., 5).
                // If we compared only the ids, a request for "player 5" could also pick up the
                // referee, and `previousEntity` would jump between the two, adding fake "teleport"
                // distances (impossible speeds) without any error.
                // `entity instanceof Player player` checks the type AND gives us a `Player` variable.
                if (entity instanceof Player player && player.getId() == entityId) {

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
}