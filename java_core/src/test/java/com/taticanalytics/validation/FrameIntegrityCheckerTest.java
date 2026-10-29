package com.taticanalytics.validation;

// SYNTAX:
// `import static` lets us call the assertion methods directly, without `Assertions.` in front.
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// LIBRARY:
// `@Test` comes from JUnit 5 (an external dependency via Maven). It marks a method as an automated test.
import org.junit.jupiter.api.Test;

import com.taticanalytics.model.Ball;
import com.taticanalytics.model.Player;
import com.taticanalytics.model.Referee;

// PURPOSE:
// Test suite for `FrameIntegrityChecker` (TA-76): the structure rules of a frame (valid team_id,
// at most one ball, unique ids) and the fact that its memory is reset on every new frame.
public class FrameIntegrityCheckerTest {

    @Test
    public void shouldAcceptAValidFrame() {
        FrameIntegrityChecker checker = new FrameIntegrityChecker();
        checker.startFrame();

        assertTrue(checker.accept(1, new Player(1, 10.0, 10.0, 1)));
        assertTrue(checker.accept(1, new Player(2, 20.0, 20.0, 2)));
        assertTrue(checker.accept(1, new Ball(0, 15.0, 15.0)));
        assertTrue(checker.accept(1, new Referee(1000, 50.0, 30.0)));
        assertEquals(0, checker.getDiscardedCount());
    }

    // The contract only has teams 1 and 2. Zero is what a missing value used to become; -1 and 3 are just wrong.
    @Test
    public void shouldDiscardAPlayerWhoseTeamIsNotOneOrTwo() {
        FrameIntegrityChecker checker = new FrameIntegrityChecker();
        checker.startFrame();

        assertFalse(checker.accept(1, new Player(1, 10.0, 10.0, 0)));
        assertFalse(checker.accept(1, new Player(2, 10.0, 10.0, 3)));
        assertFalse(checker.accept(1, new Player(3, 10.0, 10.0, -1)));
        assertEquals(3, checker.getInvalidTeamCount());
    }

    @Test
    public void shouldDiscardASecondBallInTheSameFrame() {
        FrameIntegrityChecker checker = new FrameIntegrityChecker();
        checker.startFrame();

        assertTrue(checker.accept(1, new Ball(0, 15.0, 15.0)));
        assertFalse(checker.accept(1, new Ball(5, 60.0, 30.0)));
        assertEquals(1, checker.getExtraBallCount());
    }

    @Test
    public void shouldDiscardAnEntityWhoseIdIsAlreadyUsedByAnotherTypeInTheSameFrame() {
        FrameIntegrityChecker checker = new FrameIntegrityChecker();
        checker.startFrame();

        assertTrue(checker.accept(1, new Player(7, 10.0, 10.0, 1)));
        // A referee with the same id as the player: the FIRST entity wins, the later one is dropped.
        assertFalse(checker.accept(1, new Referee(7, 50.0, 30.0)));
        assertEquals(1, checker.getDuplicateIdCount());
    }

    // The memory is per frame: the same ids and the ball show up again in the next frame, and that is normal.
    @Test
    public void shouldForgetIdsAndTheBallWhenANewFrameStarts() {
        FrameIntegrityChecker checker = new FrameIntegrityChecker();

        checker.startFrame();
        assertTrue(checker.accept(1, new Player(7, 10.0, 10.0, 1)));
        assertTrue(checker.accept(1, new Ball(0, 15.0, 15.0)));

        checker.startFrame();
        assertTrue(checker.accept(2, new Player(7, 10.5, 10.0, 1)));
        assertTrue(checker.accept(2, new Ball(0, 15.5, 15.0)));
        assertEquals(0, checker.getDiscardedCount());
    }

    // A rejected entity must NOT use up its id or the frame's only ball.
    @Test
    public void shouldNotRememberAnEntityThatWasRejected() {
        FrameIntegrityChecker checker = new FrameIntegrityChecker();
        checker.startFrame();

        // The player is rejected for its team, so id 7 is still free for the next entity.
        assertFalse(checker.accept(1, new Player(7, 10.0, 10.0, 9)));
        assertTrue(checker.accept(1, new Referee(7, 50.0, 30.0)));
    }
}