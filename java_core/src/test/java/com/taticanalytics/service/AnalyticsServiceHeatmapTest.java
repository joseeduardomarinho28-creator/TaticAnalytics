package com.taticanalytics.service;

// LEARNING NOTE:
// Java utilizes packages to organize code. Imports tell the compiler where to find 
// classes that are not in this exact same package. 

// LIBRARY: 
// `java.util.ArrayList` and `java.util.List` belong to the Java Standard Library.
// They provide the interface and implementation for resizable arrays (like Python lists).
import java.util.ArrayList;
import java.util.List;

// LIBRARY:
// `org.junit.jupiter.api` is the JUnit 5 framework, an external dependency (via Maven) 
// used for writing and running automated tests in Java.
import org.junit.jupiter.api.Test;
// SYNTAX:
// `import static` lets us use the method `assertEquals()` directly in our code 
// without having to type `Assertions.assertEquals()` every time.
import static org.junit.jupiter.api.Assertions.assertEquals;

// Domain model imports
// These are the classes we built in our own project representing the business logic.
import com.taticanalytics.model.FrameData;
import com.taticanalytics.model.HeatmapGrid;
import com.taticanalytics.model.Player;

// Utility import
import com.taticanalytics.util.MatchConstants;

// PURPOSE:
// This class is a Test Suite specifically for validating the Heatmap generation 
// logic inside the `AnalyticsService`. It does not contain production application 
// logic; it only contains scenarios to ensure the production logic works perfectly.
public class AnalyticsServiceHeatmapTest {
    
    // ANNOTATION:
    // `@Test` is a marker for the JUnit test runner. It says: "Execute this method 
    // as an isolated test case." Without this, the method would just be ignored.
    @Test
    public void shouldAccumulateTimeInCorrectHeatmapCell() {
        
        // OOP CONCEPT / TESTING: Arrange, Act, Assert (AAA) Pattern
        // ---------------------------------------------------------
        // STEP 1: ARRANGE - Set up the mock data.
        
        // SYNTAX: Generics `<FrameData>`
        // Unlike Python, Java is strongly typed. We declare that `frames` is a `List`
        // (an Interface) that can ONLY hold `FrameData` objects. 
        // We instantiate it as an `ArrayList` (a concrete Implementation).
        List<FrameData> frames = new ArrayList<>();

        // EXECUTION FLOW: Object Creation
        // We call the `FrameData` constructor. We pass 1 (frameId) and 0.0 (timestamp).
        FrameData frame1 = new FrameData(1, 0.0);
        
        // SYNTAX: Inline Object Creation
        // `new Player(1, 15.0, 15.0, 10)` creates a Player object in memory and 
        // immediately passes it to `addEntity`. We don't save the Player to a 
        // variable because we don't need to reference it again in this method.
        frame1.addEntity(new Player(1, 15.0, 15.0, 10));
        frames.add(frame1);

        FrameData frame2 = new FrameData(2, 1.0);
        frame2.addEntity(new Player(1, 15.0, 15.0, 10));
        frames.add(frame2);

        // DATA FLOW: 
        // The player stays at coordinate (15.0, 15.0) from timestamp 0.0 to 2.0.
        // Therefore, the total time accumulated at this spot should be exactly 2.0 seconds.
        FrameData frame3 = new FrameData(3, 2.0);
        frame3.addEntity(new Player(1, 15.0, 15.0, 10));
        frames.add(frame3);

        AnalyticsService service = new AnalyticsService();

        // STEP 2: ACT - Run the method we are testing.
        HeatmapGrid heatmap = service.generatePlayerHeatmap(frames, 1, 10, 10, 100.0, 100.0);

        // SYNTAX: `double[][]`
        // This declares a 2-Dimensional Array (an array of arrays) of primitive `double` values.
        // It represents our grid/matrix.
        double[][] grid = heatmap.getGrid();

        // STEP 3: ASSERT - Verify the result.
        // LEARNING NOTE: Why `grid[1][1]`?
        // Since the field is 100x100 and divided into 10x10 cells, each cell is 10x10 units.
        // Coordinates X=15.0, Y=15.0 fall into row index 1 (15/10) and col index 1 (15/10).
        // 
        // We expect the accumulated time (2.0s) to be stored in `grid[1][1]`.
        // The third parameter `0.001` is the delta: JUnit allows a 0.001 margin of error 
        // because floating-point math (decimals) in computers is sometimes imprecise.
        assertEquals(2.0, grid[1][1], 0.001);

    }

    @Test
    public void shouldNotAccumulateTimeOnFrameGap() {
        // PURPOSE: Testing business rules (Edge Cases).
        // If the tracking system loses the player and skips a frame, we shouldn't 
        // draw a straight line or accumulate time blindly.
        List<FrameData> frames = new ArrayList<>();

        FrameData frame1 = new FrameData(1, 0.0);
        frame1.addEntity(new Player(1, 15.0, 15.0, 10));
        frames.add(frame1);

        // Notice we skip Frame 2 entirely.
        FrameData frame3 = new FrameData(3, 2.0);
        frame3.addEntity(new Player(1, 15.0, 15.0, 10));
        frames.add(frame3);

        AnalyticsService service = new AnalyticsService();

        HeatmapGrid heatmap = service.generatePlayerHeatmap(frames, 1, 10, 10, 100.0, 100.0);
        double[][] grid = heatmap.getGrid();

        // Because there was a gap between frame 1 and 3, our service logic should 
        // ignore this interval and accumulate 0.0 seconds.
        assertEquals(0.0, grid[1][1], 0.001);

    }

    @Test
    public void shouldReturnEmptyGridForNonExistentPlayer() {
        // PURPOSE: Testing Resilience.
        // What happens if we ask for a heatmap of a player not in the data?
        List<FrameData> frames = new ArrayList<>();

        FrameData frame1 = new FrameData(1, 0.0);
        frame1.addEntity(new Player(1, 15.0, 15.0, 10));
        frames.add(frame1);

        FrameData frame2 = new FrameData(2, 1.0);
        frame2.addEntity(new Player(1, 15.0, 15.0, 10));
        frames.add(frame2);

        AnalyticsService service = new AnalyticsService();

        // ACT: We request player ID 999.
        HeatmapGrid heatmap = service.generatePlayerHeatmap(frames, 999, 10, 10, 100.0, 100.0);
        double[][] grid = heatmap.getGrid();

        // ASSERT: The application should not crash. It should gracefully return an 
        // empty grid where all values are 0.0.
        assertEquals(0.0, grid[1][1], 0.001);

    }

    @Test
    public void shouldGenerateHeatmapUsingDefaultFieldDimensions() {
        List<FrameData> frames = new ArrayList<>();

        FrameData frame1 = new FrameData(1, 0.0);
        frame1.addEntity(new Player(1, 10.0, 10.0, 10));
        frames.add(frame1);

        FrameData frame2 = new FrameData(2, 1.0);
        frame2.addEntity(new Player(1, 10.0, 10.0, 10));
        frames.add(frame2);

        AnalyticsService service = new AnalyticsService();

        // Testa a sobrecarga simplificada que utiliza MatchConstants
        // LEARNING NOTE: The comment above (in Portuguese) correctly identifies the OOP concept here:
        // "Method Overloading". 
        // We call `generatePlayerHeatmap(frames, 1)` which takes only 2 parameters.
        // Under the hood, this method should automatically use the default constants 
        // (like field width, height, rows, and cols) instead of forcing us to pass them.
        HeatmapGrid heatmap = service.generatePlayerHeatmap(frames, 1);

        // ASSERT: We verify that the returned `heatmap` object actually used the constants 
        // defined in our `MatchConstants` file for its dimensions.
        assertEquals(MatchConstants.DEFAULT_GRID_ROWS, heatmap.getRows());
        assertEquals(MatchConstants.DEFAULT_GRID_COLS, heatmap.getCols());
    }
}