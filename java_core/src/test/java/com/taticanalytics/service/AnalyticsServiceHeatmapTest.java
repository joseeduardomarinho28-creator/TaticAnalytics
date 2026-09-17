package com.taticanalytics.service;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.taticanalytics.model.FrameData;
import com.taticanalytics.model.HeatmapGrid;
import com.taticanalytics.model.Player;

import com.taticanalytics.util.MatchConstants;

public class AnalyticsServiceHeatmapTest {
    @Test
    public void shouldAccumulateTimeInCorrectHeatmapCell() {
        List<FrameData> frames = new ArrayList<>();

        FrameData frame1 = new FrameData(1, 0.0);
        frame1.addEntity(new Player(1, 15.0, 15.0, 10));
        frames.add(frame1);

        FrameData frame2 = new FrameData(2, 1.0);
        frame2.addEntity(new Player(1, 15.0, 15.0, 10));
        frames.add(frame2);

        FrameData frame3 = new FrameData(3, 2.0);
        frame3.addEntity(new Player(1, 15.0, 15.0, 10));
        frames.add(frame3);

        AnalyticsService service = new AnalyticsService();

        HeatmapGrid heatmap = service.generatePlayerHeatmap(frames, 1, 10, 10, 100.0, 100.0);

        double[][] grid = heatmap.getGrid();

        assertEquals(2.0, grid[1][1], 0.001);

    }

    @Test
    public void shouldNotAccumulateTimeOnFrameGap() {
        List<FrameData> frames = new ArrayList<>();

        FrameData frame1 = new FrameData(1, 0.0);
        frame1.addEntity(new Player(1, 15.0, 15.0, 10));
        frames.add(frame1);

        FrameData frame3 = new FrameData(3, 2.0);
        frame3.addEntity(new Player(1, 15.0, 15.0, 10));
        frames.add(frame3);

        AnalyticsService service = new AnalyticsService();

        HeatmapGrid heatmap = service.generatePlayerHeatmap(frames, 1, 10, 10, 100.0, 100.0);

        double[][] grid = heatmap.getGrid();

        assertEquals(0.0, grid[1][1], 0.001);

    }

    @Test
    public void shouldReturnEmptyGridForNonExistentPlayer() {
        List<FrameData> frames = new ArrayList<>();

        FrameData frame1 = new FrameData(1, 0.0);
        frame1.addEntity(new Player(1, 15.0, 15.0, 10));
        frames.add(frame1);

        FrameData frame2 = new FrameData(2, 1.0);
        frame2.addEntity(new Player(1, 15.0, 15.0, 10));
        frames.add(frame2);

        AnalyticsService service = new AnalyticsService();

        HeatmapGrid heatmap = service.generatePlayerHeatmap(frames, 999, 10, 10, 100.0, 100.0);

        double[][] grid = heatmap.getGrid();

        assertEquals(0.0, grid[1][1], 0.001);

    }

    @Test
    public void shouldGenerateHeatmapUsingDefaultFieldDimensions() {
        List<FrameData> frames = new ArrayList<>();

        FrameData frame1 = new FrameData(1, 0.0);
        frame1.addEntity(new Player(1, 10.0, 10.0, 10));
        frames.add(frame1);

        FrameData frame2 = new FrameData(2, 1.0);
        frame2.addEntity(new Player(1, 10.0, 10.0, 10));
        frames.add(frame2);

        AnalyticsService service = new AnalyticsService();

        // Testa a sobrecarga simplificada que utiliza MatchConstants
        HeatmapGrid heatmap = service.generatePlayerHeatmap(frames, 1);

        assertEquals(MatchConstants.DEFAULT_GRID_ROWS, heatmap.getRows());
        assertEquals(MatchConstants.DEFAULT_GRID_COLS, heatmap.getCols());
    }
}
