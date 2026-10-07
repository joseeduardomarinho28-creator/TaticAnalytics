package com.taticanalytics.service;

// LIBRARY:
// `java.nio.file` is the modern Java API for working with files and folders.
// `Path` represents a location on disk, and `Files` has helpers to read/write it.
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// LIBRARY:
// JUnit 5 (an external dependency via Maven). `@TempDir` is a JUnit feature that creates a
// temporary folder before the test and deletes it afterwards, so tests never leave files behind.
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.taticanalytics.exception.TrackingDataLoadException;

// PURPOSE:
// Test suite for the failure paths of `TrackingDataLoader.loadData`.
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
}
