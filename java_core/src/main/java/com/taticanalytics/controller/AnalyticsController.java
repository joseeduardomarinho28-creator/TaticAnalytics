package com.taticanalytics.controller;

import com.taticanalytics.model.PlayerStats;
import com.taticanalytics.model.HeatmapGrid;
import com.taticanalytics.service.AnalyticsService;
import com.taticanalytics.service.TrackingDataLoader;
import com.taticanalytics.model.FrameData;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final TrackingDataLoader dataLoader;

    public AnalyticsController(AnalyticsService analyticsService, TrackingDataLoader dataLoader) {
        this.analyticsService = analyticsService;
        this.dataLoader = dataLoader;
    }

    // 1. Player Stats
    @GetMapping("/player/{id}/stats")
    public PlayerStats getPlayerStats(@PathVariable("id") int id) {
        List<FrameData> frames = dataLoader.loadSampleData();
        return analyticsService.calculatePlayerStats(frames, id);
    }

    // 2. Possession Per Player
    @GetMapping("/possession/players")
    public Map<Integer, Double> getPossessionPerPlayer(@RequestParam(name = "radius", defaultValue = "1.5") double radius) {
        List<FrameData> frames = dataLoader.loadSampleData();
        return analyticsService.calculatePossessionTimePerPlayer(frames, radius);
    }

    // 3. Heatmap (Heatmap)
    @GetMapping("/player/{id}/heatmap")
    public HeatmapGrid getPlayerHeatmap(
            @PathVariable("id") int id,
            @RequestParam(name = "rows", defaultValue = "10") int rows,
            @RequestParam(name = "cols", defaultValue = "10") int cols,
            @RequestParam(name = "fieldWidth", defaultValue = "100.0") double fieldWidth,
            @RequestParam(name = "fieldHeight", defaultValue = "100.0") double fieldHeight) {
        List<FrameData> frames = dataLoader.loadSampleData();
        return analyticsService.generatePlayerHeatmap(frames, id, rows, cols, fieldWidth, fieldHeight);
    }
}