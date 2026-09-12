package com.taticanalytics.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.taticanalytics.model.Entity;
import com.taticanalytics.model.Player;
import com.taticanalytics.model.PlayerStats;
import com.taticanalytics.model.Ball;

import com.taticanalytics.model.FrameData;
import java.util.ArrayList;
import java.util.List;

public class AnalyticsServiceTest {
    @Test
    public void shouldCalculateCorrectDistanceForPlayer() {
        List<FrameData> frames = new ArrayList<>();

        FrameData frame1 = new FrameData(1, 0.0);
        frame1.addEntity(new Player(1, 0.0, 0.0, 10));
        frames.add(frame1);

        FrameData frame2 = new FrameData(2, 0.1);
        frame2.addEntity(new Player(1, 3.0, 4.0, 10));
        frames.add(frame2);

        AnalyticsService service = new AnalyticsService();

        PlayerStats stats = service.calculatePlayerStats(frames, 1);

        assertEquals(5.0, stats.getTotalDistance(), 0.001);

    }

    @Test
    public void shouldReturnZeroWhenPlayerNotFound() {
        List<FrameData> frames = new ArrayList<>();

        FrameData frame1 = new FrameData(1, 0.0);
        frame1.addEntity(new Player(1, 0.0, 0.0, 10));
        frames.add(frame1);

        FrameData frame2 = new FrameData(2, 0.1);
        frame2.addEntity(new Player(1, 3.0, 4.0, 10));
        frames.add(frame2);

        AnalyticsService service = new AnalyticsService();

        PlayerStats stats = service.calculatePlayerStats(frames, 99);

        assertEquals(0.0, stats.getTotalDistance(), 0.001);

    }

    @Test
    public void shouldIgnoreDistanceCalculationWhenFrameGapIsGreaterThanOne() {
        List<FrameData> frames = new ArrayList<>();

        FrameData frame1 = new FrameData(1, 0.0);
        frame1.addEntity(new Player(1, 0.0, 0.0, 10));
        frames.add(frame1);

        FrameData frame3 = new FrameData(3, 0.1);
        frame3.addEntity(new Player(1, 3.0, 4.0, 10));
        frames.add(frame3);

        AnalyticsService service = new AnalyticsService();

        PlayerStats stats = service.calculatePlayerStats(frames, 1);

        assertEquals(0.0, stats.getTotalDistance(), 0.001);

    }

    @Test
    public void shouldCalculatePlayerStatsWithMaxSpeed() {
        List<FrameData> frames = new ArrayList<>();

        FrameData frame1 = new FrameData(1, 0.0);
        frame1.addEntity(new Player(1, 0.0, 0.0, 10));
        frames.add(frame1);

        FrameData frame2 = new FrameData(2, 1.0);
        frame2.addEntity(new Player(1, 3.0, 4.0, 10));
        frames.add(frame2);

        FrameData frame3 = new FrameData(3, 2.0);
        frame3.addEntity(new Player(1, 11.0, 10.0, 10));
        frames.add(frame3);

        AnalyticsService service = new AnalyticsService();

        PlayerStats stats = service.calculatePlayerStats(frames, 1);

        assertEquals(15.0, stats.getTotalDistance(), 0.001);

        assertEquals(10.0, stats.getMaxSpeed(), 0.001);

        assertEquals(36.0, stats.getMaxSpeedKmh(), 0.001);

    }
}


