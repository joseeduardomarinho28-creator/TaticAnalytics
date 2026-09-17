package com.taticanalytics.controller;

import com.taticanalytics.model.PlayerStats;
import com.taticanalytics.model.HeatmapGrid;
import com.taticanalytics.model.FrameData;
import com.taticanalytics.service.AnalyticsService;
import com.taticanalytics.service.TrackingDataLoader;
import com.taticanalytics.util.MatchConstants;

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
    public Map<Integer, Double> getPossessionPerPlayer(
            @RequestParam(name = "radius", required = false) Double radius) {
        
        List<FrameData> frames = dataLoader.loadSampleData();
        
        if (radius != null) {
            return analyticsService.calculatePossessionTimePerPlayer(frames, radius);
        }
        
        return analyticsService.calculatePossessionTimePerPlayer(frames);
    }

    // 3. Possession Per Team
    @GetMapping("/possession/teams")
    public Map<Integer, Double> getPossessionPerTeam(
            @RequestParam(name = "radius", required = false) Double radius) {
        
        List<FrameData> frames = dataLoader.loadSampleData();
        
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
            @RequestParam(name = "fieldHeight", required = false) Double fieldHeight) {
        
        List<FrameData> frames = dataLoader.loadSampleData();

        if (rows != null || cols != null || fieldWidth != null || fieldHeight != null) {
            int r = (rows != null) ? rows : MatchConstants.DEFAULT_GRID_ROWS;
            int c = (cols != null) ? cols : MatchConstants.DEFAULT_GRID_COLS;
            double w = (fieldWidth != null) ? fieldWidth : MatchConstants.FIELD_LENGTH;
            double h = (fieldHeight != null) ? fieldHeight : MatchConstants.FIELD_WIDTH;

            return analyticsService.generatePlayerHeatmap(frames, id, r, c, w, h);
        }

        return analyticsService.generatePlayerHeatmap(frames, id);
    }
}