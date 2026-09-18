package com.taticanalytics.service;

// LIBRARY:
// `org.junit.jupiter.api` is the package for JUnit 5, the standard testing framework in Java.
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

// Domain model imports
import com.taticanalytics.model.Entity;
import com.taticanalytics.model.Player;
import com.taticanalytics.model.PlayerStats;
import com.taticanalytics.model.Ball;
import com.taticanalytics.model.FrameData;

// LEARNING NOTE:
// Notice that `MatchConstants` is imported here, but it is actually never used in the code below.
// In Java, unused imports don't break the code, but they are considered "code smell". 
// Most IDEs will highlight this in gray or yellow.
import com.taticanalytics.util.MatchConstants;

// Java standard collections
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// PURPOSE:
// This is a Unit Test class. Its responsibility is to verify the internal logic of the 
// `AnalyticsService` class. Instead of reading from a real JSON file, we manually build 
// "mock" objects in memory to simulate very specific scenarios (like a player moving, 
// a frame gap, or a player not existing).
public class AnalyticsServiceTest {
    
    @Test
    public void shouldCalculateCorrectDistanceForPlayer() {
        
        // OOP CONCEPT / TESTING: "Arrange, Act, Assert" Pattern (AAA)
        // This is the standard way to write tests.
        // Step 1: ARRANGE (Set up the initial state and mock data)
        List<FrameData> frames = new ArrayList<>();

        // SYNTAX: Constructor Instantiation
        // `new FrameData(1, 0.0)` calls the constructor of FrameData passing ID=1, timestamp=0.0.
        FrameData frame1 = new FrameData(1, 0.0);
        
        // SYNTAX: We create a Player directly inside the `addEntity` method.
        // This is an anonymous, inline object. We don't save it to a variable first because 
        // we only need it inside `frame1`.
        frame1.addEntity(new Player(1, 0.0, 0.0, 10));
        frames.add(frame1);

        FrameData frame2 = new FrameData(2, 0.1);
        // The player moves from (0.0, 0.0) to (3.0, 4.0).
        // By Pythagorean theorem (3^2 + 4^2 = C^2), the distance should be exactly 5.0.
        frame2.addEntity(new Player(1, 3.0, 4.0, 10));
        frames.add(frame2);

        AnalyticsService service = new AnalyticsService();

        // Step 2: ACT (Execute the specific method we want to test)
        PlayerStats stats = service.calculatePlayerStats(frames, 1);

        // Step 3: ASSERT (Verify the result)
        // LEARNING NOTE: The third parameter (0.001) is the "delta" or "epsilon".
        // Floating-point math (doubles) in computing is inherently imprecise (e.g., 5.0 might 
        // be stored as 5.000000000000001). We tell JUnit: "If the actual value is within 
        // 0.001 of 5.0, consider it a pass."
        assertEquals(5.0, stats.getTotalDistance(), 0.001);
    }

    @Test
    public void shouldReturnZeroWhenPlayerNotFound() {
        // PURPOSE: Testing edge cases (Resilience).
        // What happens if we ask for the stats of a player who isn't on the field?
        List<FrameData> frames = new ArrayList<>();

        FrameData frame1 = new FrameData(1, 0.0);
        frame1.addEntity(new Player(1, 0.0, 0.0, 10));
        frames.add(frame1);

        FrameData frame2 = new FrameData(2, 0.1);
        frame2.addEntity(new Player(1, 3.0, 4.0, 10));
        frames.add(frame2);

        AnalyticsService service = new AnalyticsService();

        // ACT: We request stats for Player 99, but only Player 1 exists.
        PlayerStats stats = service.calculatePlayerStats(frames, 99);

        // ASSERT: The code shouldn't crash. It should gracefully return 0.0.
        assertEquals(0.0, stats.getTotalDistance(), 0.001);
    }

    @Test
    public void shouldIgnoreDistanceCalculationWhenFrameGapIsGreaterThanOne() {
        // PURPOSE: Testing business rules.
        // Our service has a rule: "Only calculate distance if frames are consecutive".
        List<FrameData> frames = new ArrayList<>();

        FrameData frame1 = new FrameData(1, 0.0);
        frame1.addEntity(new Player(1, 0.0, 0.0, 10));
        frames.add(frame1);

        // Notice we skip Frame 2 and jump straight to Frame 3.
        FrameData frame3 = new FrameData(3, 0.1);
        frame3.addEntity(new Player(1, 3.0, 4.0, 10));
        frames.add(frame3);

        AnalyticsService service = new AnalyticsService();
        PlayerStats stats = service.calculatePlayerStats(frames, 1);

        // ASSERT: Because of the gap, the service should ignore this movement.
        assertEquals(0.0, stats.getTotalDistance(), 0.001);
    }

    @Test
    public void shouldCalculatePlayerStatsWithMaxSpeed() {
        List<FrameData> frames = new ArrayList<>();

        FrameData frame1 = new FrameData(1, 0.0);
        frame1.addEntity(new Player(1, 0.0, 0.0, 10));
        frames.add(frame1);

        // Move 1: (0,0) to (3,4) = 5 meters. Time delta = 1.0s. Speed = 5 m/s.
        FrameData frame2 = new FrameData(2, 1.0);
        frame2.addEntity(new Player(1, 3.0, 4.0, 10));
        frames.add(frame2);

        // Move 2: (3,4) to (11,10) = 10 meters. Time delta = 1.0s. Speed = 10 m/s.
        FrameData frame3 = new FrameData(3, 2.0);
        frame3.addEntity(new Player(1, 11.0, 10.0, 10));
        frames.add(frame3);

        AnalyticsService service = new AnalyticsService();
        PlayerStats stats = service.calculatePlayerStats(frames, 1);

        // TESTING NOTE: A single test can contain multiple assertions to verify different 
        // fields of the resulting object (`PlayerStats`).
        assertEquals(15.0, stats.getTotalDistance(), 0.001);
        assertEquals(10.0, stats.getMaxSpeed(), 0.001);
        // 10 m/s * 3.6 = 36 km/h
        assertEquals(36.0, stats.getMaxSpeedKmh(), 0.001);
    }

    @Test
    public void shouldCalculatePossessionTimePerPlayer() {
        List<FrameData> frames = new ArrayList<>();

        // Frame 1: Ball is at X=10. Player 1 is at X=10.5. Player 2 is at X=14.0.
        // Player 1 is closer (0.5m distance vs 4.0m).
        FrameData frame1 = new FrameData(1, 0.0);
        frame1.addEntity(new Ball(1, 10.0, 10.0));
        frame1.addEntity(new Player(1, 10.5, 10.0, 10));
        frame1.addEntity(new Player(2, 14.0, 10.0, 10));
        frames.add(frame1);

        FrameData frame2 = new FrameData(2, 1.0);
        frame2.addEntity(new Ball(1, 10.0, 10.0));
        frame2.addEntity(new Player(1, 13.0, 10.0, 10));
        frame2.addEntity(new Player(2, 10.5, 10.0, 10));
        frames.add(frame2);

        AnalyticsService service = new AnalyticsService();

        // Pass a strict possession radius of 1.5 meters.
        Map<Integer, Double> possessionMap = service.calculatePossessionTimePerPlayer(frames, 1.5);

        // Player 1 had the ball for the 1.0s interval between frame 1 and 2.
        assertEquals(1.0, possessionMap.get(1), 0.001);

        // LEARNING NOTE: `getOrDefault(key, default_value)`
        // This is exactly like Python's `dictionary.get(key, default)`.
        // If Player 2 never touched the ball, they won't even exist in the map as a key.
        // Calling `.get(2)` would return `null`. `getOrDefault` safely returns 0.0 instead, 
        // preventing a NullPointerException when JUnit tries to compare it.
        assertEquals(0.0, possessionMap.getOrDefault(2, 0.0), 0.001);
    }

    @Test
    public void shouldCalculatePossessionTimePerTeam() {
        List<FrameData> frames = new ArrayList<>();

        // Notice that we group by Team ID (10 and 20) instead of Player ID here.
        FrameData frame1 = new FrameData(1, 0.0);
        frame1.addEntity(new Ball(1, 10.0, 10.0));
        frame1.addEntity(new Player(1, 10.5, 10.0, 10));
        frame1.addEntity(new Player(2, 14.0, 10.0, 20));
        frames.add(frame1);

        FrameData frame2 = new FrameData(2, 2.0);
        frame2.addEntity(new Ball(1, 10.0, 10.0));
        frame2.addEntity(new Player(1, 13.0, 10.0, 10));
        frame2.addEntity(new Player(2, 10.5, 10.0, 20));
        frames.add(frame2);

        FrameData frame3 = new FrameData(3, 5.0);
        frame3.addEntity(new Ball(1, 10.0, 10.0));
        frame3.addEntity(new Player(1, 13.0, 10.0, 10));
        frame3.addEntity(new Player(2, 10.5, 10.0, 20));
        frames.add(frame3);

        AnalyticsService service = new AnalyticsService();
        Map<Integer, Double> teamPossession = service.calculatePossessionTimePerTeam(frames, 1.5);

        // Team 10 had it from 0.0 to 2.0 = 2 seconds
        assertEquals(2.0, teamPossession.get(10), 0.001);
        // Team 20 had it from 2.0 to 5.0 = 3 seconds
        assertEquals(3.0, teamPossession.get(20), 0.001);
    }

    @Test
    public void shouldCalculatePossessionTimePerPlayerUsingDefaultRadius() {
        List<FrameData> frames = new ArrayList<>();

        FrameData frame1 = new FrameData(1, 0.0);
        frame1.addEntity(new Ball(1, 10.0, 10.0));
        frame1.addEntity(new Player(1, 10.5, 10.0, 10));
        frame1.addEntity(new Player(2, 14.0, 10.0, 10));
        frames.add(frame1);

        FrameData frame2 = new FrameData(2, 1.0);
        frame2.addEntity(new Ball(1, 10.0, 10.0));
        frame2.addEntity(new Player(1, 13.0, 10.0, 10));
        frame2.addEntity(new Player(2, 10.5, 10.0, 10));
        frames.add(frame2);

        AnalyticsService service = new AnalyticsService();

        // OOP CONCEPT: Method Overloading
        // Notice that here we only pass `frames` to `calculatePossessionTimePerPlayer`.
        // In the earlier test, we passed `frames, 1.5`. 
        // Java allows multiple methods with the exact same name, as long as they require 
        // different parameters. This is called Method Overloading.
        Map<Integer, Double> possessionMap = service.calculatePossessionTimePerPlayer(frames);

        assertEquals(1.0, possessionMap.get(1), 0.001);
        assertEquals(0.0, possessionMap.getOrDefault(2, 0.0), 0.001);
    }

    @Test
    public void shouldCalculatePossessionTimePerTeamUsingDefaultRadius() {
        List<FrameData> frames = new ArrayList<>();

        FrameData frame1 = new FrameData(1, 0.0);
        frame1.addEntity(new Ball(1, 10.0, 10.0));
        frame1.addEntity(new Player(1, 10.5, 10.0, 10));
        frame1.addEntity(new Player(2, 14.0, 10.0, 20));
        frames.add(frame1);

        FrameData frame2 = new FrameData(2, 2.0);
        frame2.addEntity(new Ball(1, 10.0, 10.0));
        frame2.addEntity(new Player(1, 13.0, 10.0, 10));
        frame2.addEntity(new Player(2, 10.5, 10.0, 20));
        frames.add(frame2);

        FrameData frame3 = new FrameData(3, 5.0);
        frame3.addEntity(new Ball(1, 10.0, 10.0));
        frame3.addEntity(new Player(1, 13.0, 10.0, 10));
        frame3.addEntity(new Player(2, 10.5, 10.0, 20));
        frames.add(frame3);

        AnalyticsService service = new AnalyticsService();

        // Again testing the Overloaded method (without passing the radius)
        Map<Integer, Double> teamPossession = service.calculatePossessionTimePerTeam(frames);

        assertEquals(2.0, teamPossession.get(10), 0.001);
        assertEquals(3.0, teamPossession.get(20), 0.001);
    }
}