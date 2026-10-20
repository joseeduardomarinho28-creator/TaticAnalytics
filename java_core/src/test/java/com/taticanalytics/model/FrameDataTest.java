package com.taticanalytics.model;

// SYNTAX:
// `import static` lets us call `assertEquals(...)` and `assertThrows(...)` directly,
// without typing `Assertions.assertEquals(...)` every time.
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// LIBRARY:
// `@Test` comes from JUnit 5 (an external dependency via Maven). It marks a method as an automated test.
import org.junit.jupiter.api.Test;

// PURPOSE:
// Test suite for the encapsulation of `FrameData` (TA-73).
// Before this fix, `getEntities()` returned the real internal list, so any class could call
// `frame.getEntities().clear()` and silently empty the frame. These tests guarantee that
// the list is now protected, and that the official way to add entities (`addEntity`) still works.
public class FrameDataTest {

    @Test
    public void shouldNotAllowChangingEntitiesThroughTheGetter() {

        // ARRANGE: a frame with exactly one entity (the ball).
        FrameData frame = new FrameData(1, 0.04);
        frame.addEntity(new Ball(0, 50.0, 34.0));

        // ACT + ASSERT: `assertThrows` runs the lambda and checks that it throws the expected type.
        // If nothing is thrown (e.g. the list was NOT protected), the test FAILS.
        // We try to remove everything (`clear`) and to add an entity from the outside (`add`).
        assertThrows(UnsupportedOperationException.class, () -> frame.getEntities().clear());
        assertThrows(UnsupportedOperationException.class, () -> frame.getEntities().add(new Ball(1, 1.0, 1.0)));

        // ASSERT: both attempts failed, so the frame must be exactly as before (still 1 entity).
        assertEquals(1, frame.getEntities().size());
    }

    @Test
    public void shouldStillAllowAddingEntitiesThroughAddEntity() {

        // ARRANGE: an empty frame.
        FrameData frame = new FrameData(1, 0.04);

        // ACT: add two entities through the official way, the `addEntity` method.
        frame.addEntity(new Ball(0, 50.0, 34.0));
        frame.addEntity(new Ball(1, 56.0, 38.0));

        // ASSERT: the read-only view reflects the internal list, so both entities are visible.
        // This proves the protection blocks changes from the outside without breaking `addEntity`.
        assertEquals(2, frame.getEntities().size());
    }
}
