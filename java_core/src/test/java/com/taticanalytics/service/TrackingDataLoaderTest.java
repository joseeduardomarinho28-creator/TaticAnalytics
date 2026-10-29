package com.taticanalytics.service;

// LIBRARY:
// `java.nio.file` is the modern Java API for working with files and folders.
// `Path` represents a location on disk, and `Files` has helpers to read/write it.
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// LIBRARY:
// JUnit 5 (an external dependency via Maven). `@TempDir` is a JUnit feature that creates a
// temporary folder before the test and deletes it afterwards, so tests never leave files behind.
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.taticanalytics.exception.TrackingDataLoadException;
import com.taticanalytics.model.Entity;
import com.taticanalytics.model.FrameData;

// PURPOSE:
// Test suite for `TrackingDataLoader`: the failure paths of `loadData` (TA-66) and the classpath
// loading of the bundled sample (TA-70).
// Before TA-66, a missing or corrupted file made the loader print a stack trace and return an
// EMPTY list, which looked like a legitimate match with zero frames. These tests guarantee that
// the loader now fails loudly with a `TrackingDataLoadException` instead.
public class TrackingDataLoaderTest {

    // JUnit creates a fresh temporary directory and injects it here before each test.
    @TempDir
    Path tempDir;

    @Test
    public void shouldThrowWhenFileDoesNotExist() {

        // ARRANGE: a path inside the temp folder where no file was ever created.
        TrackingDataLoader loader = new TrackingDataLoader();
        String missingPath = tempDir.resolve("does_not_exist.json").toString();

        // ACT + ASSERT: `assertThrows` runs the lambda and checks that it throws the expected type.
        // If nothing is thrown (e.g. an empty list is returned instead), the test FAILS.
        // It also returns the thrown exception, so we can inspect it.
        TrackingDataLoadException exception = assertThrows(
            TrackingDataLoadException.class,
            () -> loader.loadData(missingPath)
        );

        // The message must tell the user WHICH file failed, and the original IOException must be
        // preserved as the cause (exception chaining), so no diagnostic information is lost.
        assertTrue(exception.getMessage().contains(missingPath));
        assertInstanceOf(IOException.class, exception.getCause());
    }

    @Test
    public void shouldThrowWhenJsonIsMalformed() throws IOException {

        // ARRANGE: write a file whose JSON is cut off in the middle (the array is never closed).
        // SYNTAX: Text Blocks (""") let us write multi-line JSON without escaping quotes.
        Path malformedFile = tempDir.resolve("malformed.json");
        Files.writeString(malformedFile, """
            {
              "frames": [
                { "frame_id": 1, "timestamp": 0.04, "entities": [
            """);
        TrackingDataLoader loader = new TrackingDataLoader();

        // ACT + ASSERT: Jackson's parsing error is a subclass of IOException, so it is caught by the
        // same `catch` block and rethrown as our exception, again with the file path in the message.
        TrackingDataLoadException exception = assertThrows(
            TrackingDataLoadException.class,
            () -> loader.loadData(malformedFile.toString())
        );

        assertTrue(exception.getMessage().contains(malformedFile.toString()));
        assertInstanceOf(IOException.class, exception.getCause());
    }

    // TA-70: the bundled sample must be found through the classpath, not through a path relative to
    // the directory where the tests happen to run. Maven copies `src/main/resources` to the test
    // classpath, so if this test passes, the lookup by resource name works.
    @Test
    public void shouldLoadBundledSampleFromClasspath() {

        TrackingDataLoader loader = new TrackingDataLoader();

        List<FrameData> frames = loader.loadSampleData();

        assertTrue(frames.size() > 0, "Expected tracking_sample.json to contain at least one frame");
    }

    // TA-70: a wrong resource name must fail with a CLEAR message, not with a NullPointerException.
    // `getResourceAsStream` returns null (it does not throw) when the resource does not exist.
    @Test
    public void shouldThrowClearExceptionWhenClasspathResourceIsMissing() {

        TrackingDataLoader loader = new TrackingDataLoader();

        TrackingDataLoadException exception = assertThrows(
            TrackingDataLoadException.class,
            () -> loader.loadFromClasspath("does_not_exist.json")
        );

        // The message names the missing resource. There is no underlying IOException to chain
        // here (nothing was opened), so the cause is null.
        assertTrue(exception.getMessage().contains("does_not_exist.json"));
        assertNull(exception.getCause());
    }

    // TA-76: coordinate policy applied while loading a file from disk.
    // - player 7 is slightly outside the pitch (x = 105.8, y = -1.2): CLAMPED to (105.0, 0.0);
    // - the ball is 235 m outside the pitch (x = 340): DISCARDED, but the frame is kept;
    // - player 8 is inside the pitch: unchanged.
    @Test
    public void shouldClampNearbyPositionsAndDiscardFarAwayOnesWhenLoadingAFile() throws IOException {

        // ARRANGE
        Path file = tempDir.resolve("coordinates.json");
        Files.writeString(file, """
            {
              "frames": [
                { "frame_id": 1, "timestamp": 0.04, "entities": [
                  { "type": "player", "id": 7, "x": 105.8, "y": -1.2, "team_id": 1 },
                  { "type": "ball", "id": 0, "x": 340.0, "y": 30.0 },
                  { "type": "player", "id": 8, "x": 50.0, "y": 30.0, "team_id": 2 }
                ] }
              ]
            }
            """);
        TrackingDataLoader loader = new TrackingDataLoader();

        // ACT
        List<FrameData> frames = loader.loadData(file.toString());

        // ASSERT: the frame is still there, with 2 of the 3 entities (the ball was discarded).
        assertEquals(1, frames.size());
        List<Entity> entities = frames.get(0).getEntities();
        assertEquals(2, entities.size());

        assertEquals(7, entities.get(0).getId());
        assertEquals(105.0, entities.get(0).getX(), 0.0001);
        assertEquals(0.0, entities.get(0).getY(), 0.0001);

        assertEquals(8, entities.get(1).getId());
        assertEquals(50.0, entities.get(1).getX(), 0.0001);
    }

    // TA-76: a position in PIXEL scale (the original problem of `tracking_sample.json`, see the README) is
    // far outside a 105 m x 68 m pitch, so the entity must be discarded instead of silently breaking the heatmap.
    @Test
    public void shouldDiscardEntitiesWithPixelScaleCoordinates() throws IOException {

        // ARRANGE: one player at the center of a 1920x1080 video frame, in pixels (960, 540).
        Path file = tempDir.resolve("pixels.json");
        Files.writeString(file, """
            {
              "frames": [
                { "frame_id": 1, "timestamp": 0.04, "entities": [
                  { "type": "player", "id": 7, "x": 960.0, "y": 540.0, "team_id": 1 }
                ] }
              ]
            }
            """);
        TrackingDataLoader loader = new TrackingDataLoader();

        // ACT
        List<FrameData> frames = loader.loadData(file.toString());

        // ASSERT: the frame is still there, but without the player.
        assertEquals(1, frames.size());
        assertEquals(0, frames.get(0).getEntities().size());
    }

    // TA-76: a REQUIRED field that is missing must fail loudly. Before, the missing key made `get("x")`
    // return null and the next call crashed with an unexplained NullPointerException.
    @Test
    public void shouldRejectTheFileWhenAnEntityIsMissingARequiredField() throws IOException {

        // ARRANGE: a player WITHOUT "x".
        Path file = tempDir.resolve("missing_x.json");
        Files.writeString(file, """
            {
              "frames": [
                { "frame_id": 3, "timestamp": 0.12, "entities": [
                  { "type": "player", "id": 7, "y": 30.0, "team_id": 1 }
                ] }
              ]
            }
            """);
        TrackingDataLoader loader = new TrackingDataLoader();

        // ACT + ASSERT
        TrackingDataLoadException exception = assertThrows(
            TrackingDataLoadException.class,
            () -> loader.loadData(file.toString())
        );

        // The message says WHICH field of WHICH frame is missing.
        assertTrue(exception.getMessage().contains("\"x\""));
        assertTrue(exception.getMessage().contains("frame 3"));
    }

    @Test
    public void shouldRejectTheFileWhenAPlayerHasNoTeamId() throws IOException {

        Path file = tempDir.resolve("missing_team.json");
        Files.writeString(file, """
            {
              "frames": [
                { "frame_id": 1, "timestamp": 0.04, "entities": [
                  { "type": "player", "id": 7, "x": 10.0, "y": 30.0 }
                ] }
              ]
            }
            """);
        TrackingDataLoader loader = new TrackingDataLoader();

        TrackingDataLoadException exception = assertThrows(
            TrackingDataLoadException.class,
            () -> loader.loadData(file.toString())
        );

        assertTrue(exception.getMessage().contains("\"team_id\""));
    }

    // TA-76: the structure rules of a frame, applied while loading a file.
    // The frame has 5 entities and only 2 survive:
    // - player 1 (team 1) and the ball 0 are valid;
    // - player 2 has team_id 3, which does not exist: discarded;
    // - ball 5 is a SECOND ball in the frame: discarded;
    // - referee 1 repeats the id of player 1: discarded (the first one wins).
    @Test
    public void shouldDiscardInvalidTeamExtraBallAndDuplicateIdWhenLoadingAFile() throws IOException {

        Path file = tempDir.resolve("structure.json");
        Files.writeString(file, """
            {
              "frames": [
                { "frame_id": 1, "timestamp": 0.04, "entities": [
                  { "type": "player", "id": 1, "x": 10.0, "y": 10.0, "team_id": 1 },
                  { "type": "player", "id": 2, "x": 20.0, "y": 20.0, "team_id": 3 },
                  { "type": "ball", "id": 0, "x": 15.0, "y": 15.0 },
                  { "type": "ball", "id": 5, "x": 60.0, "y": 30.0 },
                  { "type": "referee", "id": 1, "x": 50.0, "y": 30.0 }
                ] }
              ]
            }
            """);
        TrackingDataLoader loader = new TrackingDataLoader();

        List<FrameData> frames = loader.loadData(file.toString());

        List<Entity> entities = frames.get(0).getEntities();
        assertEquals(2, entities.size());
        assertEquals(1, entities.get(0).getId());
        assertEquals(0, entities.get(1).getId());
    }
}
