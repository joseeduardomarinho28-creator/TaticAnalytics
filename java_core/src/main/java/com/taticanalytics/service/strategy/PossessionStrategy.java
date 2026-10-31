package com.taticanalytics.service.strategy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.taticanalytics.model.Ball;
import com.taticanalytics.model.Entity;
import com.taticanalytics.model.FrameData;
import com.taticanalytics.model.Player;

// PURPOSE (TA-77):
// The ball possession algorithm, moved out of `AnalyticsService`. Before, there were TWO copies of it:
// `calculatePossessionTimePerPlayer` and `calculatePossessionTimePerTeam`. They were identical except for ONE
// detail: the KEY of the result map (the player id in one, the team id in the other). Here there is a single
// copy, and that one detail is handed to the class from outside.
//
// JAVA CONCEPT: `Function<Player, Integer>` (a function passed as a value)
// `Function<A, B>` is a Java type that represents "something that receives an A and returns a B".
// Here it is "receives a `Player` and returns an `Integer`": the key under which the possession time is
// credited. `Player::getId` and `Player::getTeamId` are METHOD REFERENCES: a short way to say
// "use the method `getId` (or `getTeamId`) of the player I receive". Calling `keyExtractor.apply(player)`
// runs that method. So one algorithm serves both cases, and only the function changes.
//
// This is the Strategy idea again, in miniature: the part that varies (how to get the key) is passed in.
public class PossessionStrategy implements MetricCalculationStrategy<Map<Integer, Double>> {

    // `final`: set once in the constructor and never changed afterwards.
    private final Function<Player, Integer> keyExtractor;

    public PossessionStrategy(Function<Player, Integer> keyExtractor) {
        this.keyExtractor = keyExtractor;
    }

    // JAVA CONCEPT: static factory methods
    // Two named ways of building the same class. The name says what is being built, which is clearer than
    // `new PossessionStrategy(Player::getId)` repeated all over the project.

    // Possession credited to each PLAYER: the key is the player id.
    public static PossessionStrategy perPlayer() {
        return new PossessionStrategy(Player::getId);
    }

    // Possession credited to each TEAM: the key is the team id.
    public static PossessionStrategy perTeam() {
        return new PossessionStrategy(Player::getTeamId);
    }

    @Override
    public Map<Integer, Double> calculate(List<FrameData> frames, MetricParams params) {

        // The old methods received the radius as a parameter. Now it arrives packed in `params`.
        double radiusMeters = params.radiusMeters();

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
                    currentPossessorId = keyExtractor.apply(closestResult.player());
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

    // =====================================================================
    // PRIVATE UTILITY METHODS (moved here unchanged from `AnalyticsService`)
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