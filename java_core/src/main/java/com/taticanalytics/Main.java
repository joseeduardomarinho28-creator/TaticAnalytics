package com.taticanalytics;

// LEARNING NOTE:
// Imports tell Java where to find classes that are not in the current package.
// We separate them logically here: 
// 1. Services (business logic/data fetching)
// 2. Java Standard Library (built-in data structures)
// 3. Domain Models (the data objects representing our business entities)

// Service imports
import com.taticanalytics.service.TrackingDataLoader;
import com.taticanalytics.service.AnalyticsService;

// Java standard collections
// LEARNING NOTE:
// `List` is an interface representing an ordered collection (like Python's `list`).
// `Map` is an interface representing key-value pairs (like Python's `dict`).
import java.util.List;
import java.util.Map;

// Domain model imports
import com.taticanalytics.model.FrameData;
import com.taticanalytics.model.HeatmapGrid;
import com.taticanalytics.model.Entity;
import com.taticanalytics.model.Player;
import com.taticanalytics.model.PlayerStats;
import com.taticanalytics.model.Referee;
import com.taticanalytics.model.Ball;

// PURPOSE:
// This is the standalone CLI (Command Line Interface) entry point for our console MVP.
// While `Application.java` boots up the Spring Boot web server, `Main.java` is used 
// for quick testing, batch processing, or console verification of tracking pipelines.
// It acts as an "Orchestrator": it doesn't do the complex math itself; instead, it 
// delegates tasks to the appropriate service classes.
public class Main {
    
    // SYNTAX:
    // `public`: Anyone can call this method (the JVM needs to call it to start the program).
    // `static`: This method belongs to the class itself, not to a specific object. 
    //           The JVM can run it without needing to create a `new Main()` object first.
    // `void`: This method does not return any value when it finishes.
    // `String[] args`: An array of Strings representing command-line arguments.
    //
    // STANDARD JAVA ENTRY POINT (Console execution)
    public static void main(String[] args) {
        
        // EXECUTION FLOW:
        // 1. Instantiate the objects that will do the work (Services).
        // 2. Load the data from the JSON file.
        // 3. Process and print raw data.
        // 4. Calculate and print player stats, possession, and heatmaps.

        // OOP CONCEPT: Object Creation and Delegation
        // We use the `new` keyword to call the constructors of our service classes.
        // This allocates memory for them and gives us a reference (`loader` and `analyticsService`)
        // to call their methods later.
        TrackingDataLoader loader = new TrackingDataLoader();
        AnalyticsService analyticsService = new AnalyticsService();

        // 1. DATA INGESTION
        // DATA FLOW: 
        // JSON file -> TrackingDataLoader -> parses JSON -> returns a List of FrameData objects.
        //
        // SYNTAX: `List<FrameData>` uses Generics (the `< >`). 
        // It guarantees that this list will ONLY ever contain `FrameData` objects,
        // providing type safety at compile time.
        List<FrameData> frames = loader.loadData("src/main/resources/tracking_sample.json");

        System.out.println("Loaded Frames: " + frames.size());
        System.out.println("--------------------------------------------------");

        // 2. INSPECTING RAW / PARSED DATA
        // SYNTAX: This is an "enhanced for-loop" (similar to Python's `for frame in frames:`).
        // It iterates over every item in the `frames` collection.
        for (FrameData frame : frames) {
            System.out.println("Frame ID: " + frame.getFrameId() + " | Timestamp: " + frame.getTimestamp() + "s");

            // JAVA CONCEPT: Polymorphism & Pattern Matching in Console Output
            // `frame.getEntities()` returns a List of `Entity` objects.
            // Because `Player`, `Ball`, and `Referee` all inherit from `Entity` (Polymorphism),
            // they can all be stored in this same list.
            for (Entity entity : frame.getEntities()) {
                
                // SYNTAX: Pattern Matching for `instanceof` (Introduced in newer Java versions).
                // It asks: "Is this `entity` actually a `Player`?"
                // If YES, it automatically casts it to a Player and binds it to the variable `player`.
                // This allows us to safely call `player.getTeamId()`, which doesn't exist on a generic `Entity`.
                if (entity instanceof Player player) {
                    // SYNTAX: `System.out.printf` allows formatted printing.
                    // %d = integer, %.2f = float/double with 2 decimal places, %n = cross-platform newline.
                    System.out.printf("  [PLAYER] ID: %d | Team: %d | Position: (%.2f, %.2f)%n", 
                        player.getId(), player.getTeamId(), player.getX(), player.getY());
                } else if (entity instanceof Ball ball) {
                    System.out.printf("  [BALL] ID: %d | Position: (%.2f, %.2f)%n", 
                        ball.getId(), ball.getX(), ball.getY());
                } else if (entity instanceof Referee referee) {
                    System.out.printf("  [REFEREE] ID: %d | Position: (%.2f, %.2f)%n", 
                        referee.getId(), referee.getX(), referee.getY());
                }
            }
        }

        // 3. KINEMATIC STATS (Player 1)
        System.out.println("--------------------------------------------------");
        // We pass the entire list of frames and the ID of the player we want to analyze.
        PlayerStats statsPlayer1 = analyticsService.calculatePlayerStats(frames, 1);
        System.out.printf("Total distance of Player 1: %.2f meters%n", statsPlayer1.getTotalDistance());
        System.out.printf("Max speed of Player 1: %.2f m/s (%.2f km/h)%n", statsPlayer1.getMaxSpeed(), statsPlayer1.getMaxSpeedKmh());

        // 4. PLAYER POSSESSION TIME (Default Radius overload)
        System.out.println("--------------------------------------------------");
        
        // SYNTAX: `Map<Integer, Double>` 
        // A Map maps Keys to Values. Here, the Key is an `Integer` (Player ID), 
        // and the Value is a `Double` (Accumulated possession time in seconds).
        Map<Integer, Double> possessionMap = analyticsService.calculatePossessionTimePerPlayer(frames);

        System.out.println("Ball Possession Time per Player (Default Radius):");
        if (possessionMap.isEmpty()) {
            System.out.println("No player kept possession within the specified radius.");
        } else {
            // SYNTAX: Lambda Expression `(playerId, time) -> ...`
            // Instead of writing a traditional loop, we use the `forEach` method of the Map.
            // For every entry in the map, it passes the key (`playerId`) and value (`time`) 
            // into this unnamed, inline function (the lambda) to be printed.
            possessionMap.forEach((playerId, time) -> 
                System.out.printf("  Player ID %d: %.2f seconds%n", playerId, time)
            );
        }

        // 5. TEAM POSSESSION TIME & PERCENTAGES
        System.out.println("--------------------------------------------------");
        Map<Integer, Double> teamPossessionMap = analyticsService.calculatePossessionTimePerTeam(frames);

        System.out.println("Ball Possession Time per Team (Default Radius):");
        if (teamPossessionMap.isEmpty()) {
            System.out.println("No team kept possession within the specified radius.");
        } else {
            // JAVA CONCEPT: Streams API (mapToDouble + sum)
            // HOW IT WORKS:
            // 1. `teamPossessionMap.values()` gets just the collection of times (the Doubles).
            // 2. `.stream()` turns that collection into a sequence of elements we can process.
            // 3. `.mapToDouble(Double::doubleValue)` takes the Object `Double` and unboxes it 
            //    into a primitive `double` so we can do math on it. (`Double::doubleValue` is a method reference).
            // 4. `.sum()` adds them all up.
            double totalTeamTime = teamPossessionMap.values().stream().mapToDouble(Double::doubleValue).sum();
            
            teamPossessionMap.forEach((teamId, time) -> {
                // Ternary operator (`condition ? true_value : false_value`) guards against division by zero.
                double percentage = totalTeamTime > 0 ? (time / totalTeamTime) * 100.0 : 0.0;
                System.out.printf("  Team ID %d: %.2f seconds (%.1f%%)%n", teamId, time, percentage);
            });
        }

        // 6. SPATIAL HEATMAP PRINT (Player 1)
        System.out.println("--------------------------------------------------");
        System.out.println("Heatmap (Player 1):");
        HeatmapGrid heatmap = analyticsService.generatePlayerHeatmap(frames, 1);
        
        // SYNTAX: `double[][]` is a 2-Dimensional Array (an array of arrays).
        // It represents a matrix of primitive decimal numbers.
        double[][] grid = heatmap.getGrid();

        // Printing 2D matrix row by row to console
        // EXECUTION FLOW:
        // The outer loop extracts one horizontal row (which is an array `double[]`) at a time.
        for (double[] line : grid) {
            // The inner loop goes through every individual value in that specific row.
            for (double value : line) {
                // We use `printf` without a newline `%n` so the values print side-by-side.
                System.out.printf("%.1fs ", value);
            }
            // After finishing a row, we print an empty newline to move down for the next row.
            System.out.println();
        }
    }
}