package com.taticanalytics.validation;

// SYNTAX:
// `import static` lets us call `assertEquals(...)` directly, without `Assertions.assertEquals(...)`.
import static org.junit.jupiter.api.Assertions.assertEquals;

// LIBRARY:
// `@Test` comes from JUnit 5 (an external dependency via Maven). It marks a method as an automated test.
import org.junit.jupiter.api.Test;

// PURPOSE:
// Test suite for `CoordinateValidator` (TA-76).
// It checks the BOUNDARIES of each zone (the edge of the pitch, exactly 2 m outside, just past 2 m),
// because that is where an off-by-one mistake (`<` instead of `<=`) shows up.
// The pitch is 105 m x 68 m and the tolerance margin is 2 m, so the accepted range is
// x in [-2, 107] and y in [-2, 70].
public class CoordinateValidatorTest {

    // This test is complete: use it as the model for the others.
    @Test
    public void shouldClassifyAPointInTheMiddleOfThePitchAsInside() {

        // ACT: classify a point in the middle of the pitch.
        CoordinateStatus status = CoordinateValidator.classify(50.0, 30.0);

        // ASSERT: assertEquals(EXPECTED, ACTUAL). The expected value always comes first.
        assertEquals(CoordinateStatus.INSIDE, status);
    }

    // The pitch edge counts as inside (the check uses `<=`, not `<`).
    @Test
    public void shouldClassifyTheExactPitchCornerAsInside() {
        CoordinateStatus status = CoordinateValidator.classify(105.0, 68.0);
        assertEquals(CoordinateStatus.INSIDE, status);
    }

    // Exactly 2 m outside is still within the margin.
    @Test
    public void shouldClassifyExactlyTwoMetersOutsideAsWithinMargin() {
        CoordinateStatus status = CoordinateValidator.classify(107.0, 34.0);
        assertEquals(CoordinateStatus.WITHIN_MARGIN, status);
    }

    // Just past the margin (2 m + 1 cm) the value is not trusted anymore.
    @Test
    public void shouldClassifyMoreThanTwoMetersOutsideAsOutOfRange() {
        CoordinateStatus status = CoordinateValidator.classify(107.01, 34.0);
        assertEquals(CoordinateStatus.OUT_OF_RANGE, status);
    }

    // NaN must NOT slip through the comparisons (see the GOTCHA in `classify`).
    @Test
    public void shouldClassifyNaNAsOutOfRange() {
        CoordinateStatus status = CoordinateValidator.classify(Double.NaN, 5.0);
        assertEquals(CoordinateStatus.OUT_OF_RANGE, status);
    }

    // SYNTAX: for `double`, the third argument of `assertEquals` is a tolerance (delta), because
    // decimal numbers should not be compared with exact equality.
    @Test
    public void shouldClampAValueAboveTheMaximumToTheMaximum() {
        double result = CoordinateValidator.clamp(105.4, 105.0);
        assertEquals(105.0, result, 0.0001);
    }

    @Test
    public void shouldClampANegativeValueToZero() {
        double result = CoordinateValidator.clamp(-1.3, 105.0);
        assertEquals(0.0, result, 0.0001);
    }
}