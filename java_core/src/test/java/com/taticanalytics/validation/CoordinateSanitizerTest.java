package com.taticanalytics.validation;

// SYNTAX:
// `import static` lets us call the assertion methods directly, without `Assertions.` in front.
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

// LIBRARY:
// `@Test` comes from JUnit 5 (an external dependency via Maven). It marks a method as an automated test.
import org.junit.jupiter.api.Test;

// PURPOSE:
// Test suite for `CoordinateSanitizer` (TA-76): what it does with each kind of position
// (keep, clamp, discard) and how it keeps score for the summary and the 5% alert.
public class CoordinateSanitizerTest {

    @Test
    public void shouldKeepAPositionInsideThePitchUnchanged() {
        CoordinateSanitizer sanitizer = new CoordinateSanitizer();

        Optional<CoordinateSanitizer.Position> result = sanitizer.sanitize(7, 50.0, 30.0);

        assertTrue(result.isPresent());
        assertEquals(50.0, result.get().x(), 0.0001);
        assertEquals(30.0, result.get().y(), 0.0001);
        assertEquals(0, sanitizer.getClampedCount());
        assertEquals(0, sanitizer.getDiscardedCount());
    }

    @Test
    public void shouldClampAPositionSlightlyOutsideThePitch() {
        CoordinateSanitizer sanitizer = new CoordinateSanitizer();

        // x is 0.8 m past the far edge and y is 1.2 m before the near edge.
        Optional<CoordinateSanitizer.Position> result = sanitizer.sanitize(7, 105.8, -1.2);

        assertTrue(result.isPresent());
        assertEquals(105.0, result.get().x(), 0.0001);
        assertEquals(0.0, result.get().y(), 0.0001);
        assertEquals(1, sanitizer.getClampedCount());
    }

    @Test
    public void shouldDiscardAPositionTooFarFromThePitchAndCountIt() {
        CoordinateSanitizer sanitizer = new CoordinateSanitizer();

        Optional<CoordinateSanitizer.Position> result = sanitizer.sanitize(0, 340.0, 30.0);

        // An EMPTY Optional is how the sanitizer says "discard this entity".
        assertTrue(result.isEmpty());
        assertEquals(1, sanitizer.getDiscardedCount());
    }

    @Test
    public void shouldCountEveryReadingAndComputeTheShareOfCorrectedOnes() {
        CoordinateSanitizer sanitizer = new CoordinateSanitizer();

        // 10 readings: 7 inside, 2 clamped, 1 discarded -> 3 of 10 needed correction.
        for (int i = 0; i < 7; i++) {
            sanitizer.sanitize(i, 50.0, 30.0);
        }
        sanitizer.sanitize(10, 105.5, 30.0);
        sanitizer.sanitize(11, 50.0, -0.5);
        sanitizer.sanitize(12, 500.0, 30.0);

        assertEquals(10, sanitizer.getTotal());
        assertEquals(2, sanitizer.getClampedCount());
        assertEquals(1, sanitizer.getDiscardedCount());
        assertEquals(0.3, sanitizer.getCorrectedRatio(), 0.0001);
    }

    // GOTCHA: with no readings the ratio would be 0 / 0 = NaN. It must be 0.0 instead.
    @Test
    public void shouldReturnZeroRatioWhenNothingWasChecked() {
        CoordinateSanitizer sanitizer = new CoordinateSanitizer();

        assertEquals(0.0, sanitizer.getCorrectedRatio(), 0.0001);
    }
}