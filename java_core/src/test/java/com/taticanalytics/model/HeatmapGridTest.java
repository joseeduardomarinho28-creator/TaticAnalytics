package com.taticanalytics.model;

// SYNTAX:
// `import static` lets us call `assertEquals(...)` directly, without `Assertions.assertEquals(...)`.
import static org.junit.jupiter.api.Assertions.assertEquals;

// LIBRARY:
// `@Test` comes from JUnit 5 (an external dependency via Maven). It marks a method as an automated test.
import org.junit.jupiter.api.Test;

// PURPOSE:
// Test suite for the encapsulation of `HeatmapGrid` (TA-73).
// Before this fix, `getGrid()` returned the real internal matrix, so any class could write
// `heatmap.getGrid()[0][0] = 999` and corrupt the result. This test guarantees that
// `getGrid()` now returns a DEEP COPY, so changing the returned matrix never changes the heatmap.
public class HeatmapGridTest {

    @Test
    public void shouldNotChangeTheHeatmapWhenTheReturnedGridIsModified() {

        // ARRANGE: a default heatmap (10 rows x 15 columns, over a 105m x 68m field).
        // Each cell is 7.0m wide (105 / 15) and 6.8m tall (68 / 10), so the position (10, 10)
        // falls in column 1 (10 / 7.0 = 1.43) and row 1 (10 / 6.8 = 1.47). We add 1.0 second there.
        HeatmapGrid heatmap = new HeatmapGrid();
        heatmap.addTime(10.0, 10.0, 1.0);

        // ACT: take the matrix, check the value, and then "corrupt" it on purpose.
        // SYNTAX: the third argument of `assertEquals` for `double` is a tolerance (delta),
        // because decimal numbers should not be compared with exact equality.
        double[][] copy = heatmap.getGrid();
        assertEquals(1.0, copy[1][1], 0.0001);
        copy[1][1] = 999.0;

        // ASSERT: ask for the matrix again. The heatmap must still hold 1.0 in that cell.
        // If `getGrid()` returned the real matrix, or only a SHALLOW copy (`grid.clone()`, which
        // shares the rows), the 999.0 would have leaked in and this test would FAIL.
        double[][] again = heatmap.getGrid();
        assertEquals(1.0, again[1][1], 0.0001);
    }
}
