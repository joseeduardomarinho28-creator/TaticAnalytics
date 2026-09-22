package com.taticanalytics.controller;

// Domain Models
import com.taticanalytics.model.PlayerStats;
import com.taticanalytics.model.HeatmapGrid;
import com.taticanalytics.model.FrameData;

// Services & Utilities
import com.taticanalytics.service.AnalyticsService;
import com.taticanalytics.io.JsonFrameParser; // <-- Changed: importing the new parser
import com.taticanalytics.util.MatchConstants;

// LIBRARY: Spring Web Annotations
// These annotations map web concepts (URLs, HTTP Methods, Query Strings) directly to Java methods.
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

// SPRING ANNOTATION: `@RestController`
// Combines `@Controller` and `@ResponseBody`. It tells Spring: "This class receives HTTP requests,
// and whatever objects its methods return should be automatically converted to JSON and sent back as the HTTP response."
@RestController
// SPRING ANNOTATION: `@RequestMapping`
// Sets a base URL for every endpoint in this class. All routes here start with "http://localhost:8080/api/v1/analytics".
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    // OOP CONCEPT: Dependency Injection (Constructor Injection)
    // We declare our services as `private final`. We don't instantiate them with `new`.
    // Spring Boot automatically creates these services at startup and injects them here.
    private final AnalyticsService analyticsService;
    private final JsonFrameParser frameParser; // <-- Changed: using JsonFrameParser

    public AnalyticsController(AnalyticsService analyticsService, JsonFrameParser frameParser) {
        this.analyticsService = analyticsService;
        this.frameParser = frameParser; // <-- Changed: injecting the new dependency
    }

    // SPRING ANNOTATION: `@GetMapping`
    // Maps HTTP GET requests to this method.
    // The `{id}` in the path is a dynamic placeholder.
    // LEARNING NOTE: Checked Exceptions (`throws IOException`)
    // `frameParser.loadSampleData()` reads a file from disk, which can fail (e.g., file missing).
    // Java forces us to acknowledge this risk: either `catch` it here, or `throws` it up the
    // call stack, like we do below. Since we don't yet have custom error handling for this case,
    // we let it propagate; Spring will turn any uncaught exception into an HTTP 500 response.
    // This is a known gap (no dedicated error handling here yet) tracked in the README.
    @GetMapping("/player/{id}/stats")
    public PlayerStats getPlayerStats(
            // SPRING ANNOTATION: `@PathVariable`
            // Extracts the `{id}` from the URL (e.g., /player/10/stats -> id = 10) and assigns it to the `int id` variable.
            @PathVariable("id") int id) throws IOException {

        List<FrameData> frames = frameParser.loadSampleData(); // <-- Changed: calling the new parser
        return analyticsService.calculatePlayerStats(frames, id);
    }

    // 2. Possession Per Player
    @GetMapping("/possession/players")
    public Map<Integer, Double> getPossessionPerPlayer(
            // SPRING ANNOTATION: `@RequestParam`
            // Extracts values from the URL query string (e.g., /possession/players?radius=2.5).
            // `required = false` means the user doesn't HAVE to provide it.
            // LEARNING NOTE: Wrapper Class (`Double`)
            // We use `Double` instead of the primitive `double` here. If the user doesn't provide the radius,
            // a `Double` can safely be `null`. A primitive `double` cannot be null and would cause a crash.
            @RequestParam(name = "radius", required = false) Double radius) throws IOException {

        List<FrameData> frames = frameParser.loadSampleData(); // <-- Changed: calling the new parser

        // JAVA CONCEPT: Method Overloading execution
        if (radius != null) {
            // User provided a custom radius, use the full method.
            return analyticsService.calculatePossessionTimePerPlayer(frames, radius);
        }

        // User provided nothing, use the overloaded method that relies on default constants.
        return analyticsService.calculatePossessionTimePerPlayer(frames);
    }

    // 3. Possession Per Team
    @GetMapping("/possession/teams")
    public Map<Integer, Double> getPossessionPerTeam(
            @RequestParam(name = "radius", required = false) Double radius) throws IOException {

        List<FrameData> frames = frameParser.loadSampleData(); // <-- Changed: calling the new parser

        if (radius != null) {
            return analyticsService.calculatePossessionTimePerTeam(frames, radius);
        }

        return analyticsService.calculatePossessionTimePerTeam(frames);
    }

    // 4. Player Heatmap
    @GetMapping("/player/{id}/heatmap")
    public HeatmapGrid getPlayerHeatmap(
            @PathVariable("id") int id,
            @RequestParam(name = "rows", required = false) Integer rows,
            @RequestParam(name = "cols", required = false) Integer cols,
            @RequestParam(name = "fieldWidth", required = false) Double fieldWidth,
            @RequestParam(name = "fieldHeight", required = false) Double fieldHeight) throws IOException {

        List<FrameData> frames = frameParser.loadSampleData(); // <-- Changed: calling the new parser

        // LOGIC: Graceful Fallbacks using Constants
        // If the user provided ANY of the custom parameters, we must use the full method signature.
        if (rows != null || cols != null || fieldWidth != null || fieldHeight != null) {

            // SYNTAX: Ternary Operator
            // For each parameter, we check: "Did the user provide this specific one?"
            // If yes, use it. If no, fall back to our central `MatchConstants`.
            int r = (rows != null) ? rows : MatchConstants.DEFAULT_GRID_ROWS;
            int c = (cols != null) ? cols : MatchConstants.DEFAULT_GRID_COLS;
            double w = (fieldWidth != null) ? fieldWidth : MatchConstants.FIELD_LENGTH;
            double h = (fieldHeight != null) ? fieldHeight : MatchConstants.FIELD_WIDTH;

            return analyticsService.generatePlayerHeatmap(frames, id, r, c, w, h);
        }

        // If the user provided absolutely no query parameters, use the simpler overloaded method.
        return analyticsService.generatePlayerHeatmap(frames, id);
    }
}
