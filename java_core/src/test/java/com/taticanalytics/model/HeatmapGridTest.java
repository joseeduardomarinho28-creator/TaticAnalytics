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

    // TA-76: a position exactly on the far edge of the pitch is VALID (the contract says x in [0, 105],
    // y in [0, 68]). Before, 105.0 / 7.0 = 15 was an index past the last column (14) and the time
    // was silently thrown away.
    @Test
    public void shouldPutAPositionOnTheFarEdgeOfThePitchInTheLastCell() {

        // ARRANGE + ACT: the default heatmap has 10 rows x 15 columns, so the last cell is [9][14].
        HeatmapGrid heatmap = new HeatmapGrid();
        heatmap.addTime(105.0, 68.0, 1.0);

        // ASSERT
        assertEquals(1.0, heatmap.getGrid()[9][14], 0.0001);
    }

    // TA-76: positions outside the pitch, or not a number, must not be counted in any cell.
    // GOTCHA: `(int) (-0.5 / 7.0)` is 0, because casting to `int` truncates toward zero. Before the fix,
    // a position slightly to the left of the pitch was wrongly counted in column 0.
    @Test
    public void shouldIgnorePositionsOutsideThePitchOrNotANumber() {

        HeatmapGrid heatmap = new HeatmapGrid();
        heatmap.addTime(-0.5, 10.0, 1.0);
        heatmap.addTime(105.1, 10.0, 1.0);
        heatmap.addTime(10.0, 68.1, 1.0);
        heatmap.addTime(Double.NaN, 10.0, 1.0);

        // ASSERT: the sum of ALL cells is still zero, so none of the four calls was counted.
        double sum = 0.0;
        for (double[] row : heatmap.getGrid()) {
            for (double cell : row) {
                sum += cell;
            }
        }
        assertEquals(0.0, sum, 0.0001);
    }
}
