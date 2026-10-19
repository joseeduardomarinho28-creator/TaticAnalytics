package com.taticanalytics.io;

// LIBRARY:
// `java.nio.file` is the modern Java API for working with files and folders.
// `Path` represents a location on disk, and `Files` has helpers to read/write it.
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// LIBRARY:
// JUnit 5 (an external dependency via Maven). `@TempDir` is a JUnit feature that creates a
// temporary folder before the test and deletes it afterwards, so tests never leave files behind.
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// LIBRARY:
// `AnnotationConfigApplicationContext` is Spring's container without the web server. Giving it a
// package name makes it SCAN that package looking for classes annotated with `@Service`/`@Component`.
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

// PURPOSE:
// Test suite for `ExportService` (TA-69). Before this task the class was dead code: it was never
// created by Spring and nothing called it. These tests prove that (1) Spring now manages it and
// (2) the CSV files it writes have the expected header and rows.
public class ExportServiceTest {

    // JUnit creates a fresh temporary directory and injects it here before each test.
    @TempDir
    Path tempDir;

    // TEST HELPER: builds one row of the list that `exportPlayerStatsToCsv` expects.
    // The keys must match the ones the service reads: playerId, teamId, totalDistance, maxSpeed.
    private Map<String, Object> row(int playerId, int teamId, double totalDistance, double maxSpeed) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("playerId", playerId);
        row.put("teamId", teamId);
        row.put("totalDistance", totalDistance);
        row.put("maxSpeed", maxSpeed);
        return row;
    }

    @Test
    public void shouldBeManagedBySpringAsABean() {

        // ACT: scan the `io` package. If `ExportService` had no `@Service`, it would not be found.
        // `try-with-resources` closes the Spring container automatically at the end of the block.
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext("com.taticanalytics.io")) {

            // ASSERT: `getBean` throws an exception if there is no bean of this type.
            assertNotNull(context.getBean(ExportService.class));
        }
    }

    @Test
    public void shouldWritePlayerStatsCsvWithHeaderAndRows() throws IOException {

        // ARRANGE: two players with known numbers.
        ExportService service = new ExportService();
        Path csvFile = tempDir.resolve("players.csv");
        List<Map<String, Object>> stats = new ArrayList<>();
        stats.add(row(1, 1, 12.5, 7.25));
        stats.add(row(2, 2, 0.0, 0.0));

        // ACT
        service.exportPlayerStatsToCsv(stats, csvFile.toString());

        // ASSERT: `Files.readAllLines` returns the file as a list of lines.
        // Decimal numbers must be written with a DOT (12.5), whatever the language of the computer,
        // otherwise a comma would add an extra column to the CSV.
        List<String> lines = Files.readAllLines(csvFile);
        assertEquals(3, lines.size());
        assertEquals("playerId,teamId,totalDistance,maxSpeed", lines.get(0));
        assertEquals("1,1,12.5,7.25", lines.get(1));
        assertEquals("2,2,0.0,0.0", lines.get(2));
    }

    @Test
    public void shouldWriteOnlyTheHeaderWhenThereAreNoPlayers() throws IOException {

        // ARRANGE: a match without players must still produce a valid (header-only) CSV.
        ExportService service = new ExportService();
        Path csvFile = tempDir.resolve("empty.csv");

        // ACT
        service.exportPlayerStatsToCsv(new ArrayList<>(), csvFile.toString());

        // ASSERT
        List<String> lines = Files.readAllLines(csvFile);
        assertEquals(1, lines.size());
        assertEquals("playerId,teamId,totalDistance,maxSpeed", lines.get(0));
    }

    @Test
    public void shouldCreateMissingParentFolders() throws IOException {

        // ARRANGE: neither "reports" nor "reports/2026" exist inside the temp folder.
        // This is what happens when the user passes "output/players.csv" and `output/` does not exist yet.
        ExportService service = new ExportService();
        Path csvFile = tempDir.resolve("reports").resolve("2026").resolve("players.csv");
        assertTrue(Files.notExists(csvFile.getParent()));

        // ACT
        service.exportPlayerStatsToCsv(List.of(row(7, 1, 3.0, 1.5)), csvFile.toString());

        // ASSERT: the folders were created and the file was written.
        assertTrue(Files.exists(csvFile));
        assertEquals("7,1,3.0,1.5", Files.readAllLines(csvFile).get(1));
    }

    @Test
    public void shouldWriteHeatmapCsvOneLinePerGridRow() throws IOException {

        // ARRANGE: a 2x3 grid (2 rows, 3 columns).
        ExportService service = new ExportService();
        Path csvFile = tempDir.resolve("heatmap.csv");
        double[][] grid = {
            {1.0, 2.5, 0.0},
            {0.0, 0.0, 4.0}
        };

        // ACT
        service.exportHeatmapToCsv(grid, csvFile.toString());

        // ASSERT: one line per row, values separated by commas and no trailing comma.
        List<String> lines = Files.readAllLines(csvFile);
        assertEquals(2, lines.size());
        assertEquals("1.0,2.5,0.0", lines.get(0));
        assertEquals("0.0,0.0,4.0", lines.get(1));
    }
}
