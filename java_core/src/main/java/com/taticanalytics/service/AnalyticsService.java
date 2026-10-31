package com.taticanalytics.service;

import java.util.List;
import java.util.Map;

// Domain model imports
import com.taticanalytics.model.FrameData;
import com.taticanalytics.model.HeatmapGrid;
import com.taticanalytics.model.PlayerStats;
import com.taticanalytics.service.strategy.DistanceSpeedStrategy;
import com.taticanalytics.service.strategy.HeatmapStrategy;
import com.taticanalytics.service.strategy.MetricCalculationStrategy;
import com.taticanalytics.service.strategy.MetricParams;
import com.taticanalytics.service.strategy.PossessionStrategy;
import com.taticanalytics.util.MatchConstants;

// LIBRARY & FRAMEWORK: Spring Boot
import org.springframework.stereotype.Service;

// PURPOSE:
// This is the "front door" of the analytics. Controllers, the `Main` class and the tests call it to get
// distance, speed, possession and heatmaps.
//
// DESIGN PATTERN (TA-77): Strategy
// Before, this class contained ALL the algorithms (about 250 lines), including two almost identical
// copies of the possession logic. Now each algorithm lives in its own class inside the `strategy` package,
// and this class only DELEGATES: it packs the inputs into a `MetricParams` and asks the right strategy to
// `calculate`. The public methods below keep exactly the same names and parameters as before, so nobody
// that uses `AnalyticsService` needs to change.
//
//   AnalyticsService ──uses──▶ MetricCalculationStrategy<T>   (the interface: "calculate(frames, params)")
//                                  ▲            ▲            ▲
//                  DistanceSpeedStrategy   PossessionStrategy   HeatmapStrategy
//                    (PlayerStats)         (Map<Integer,Double>)   (HeatmapGrid)
//
// SPRING BOOT CONCEPT: @Service annotation
// By placing @Service here, we tell the Spring Framework:
// "Hey Spring, please create exactly one instance (a Singleton) of this class
// when the app starts, and automatically hand it to anyone who needs it."
// This is called Dependency Injection (Inversion of Control).
@Service
public class AnalyticsService {

    // The strategies this service delegates to. `final`: chosen once, in the constructor, never changed.
    // The fields are typed with the INTERFACE (not with `DistanceSpeedStrategy`, etc.) on purpose: this class
    // only needs to know "something that calculates this metric", not which concrete class it is.
    private final MetricCalculationStrategy<PlayerStats> distanceSpeedStrategy;
    private final MetricCalculationStrategy<Map<Integer, Double>> playerPossessionStrategy;
    private final MetricCalculationStrategy<Map<Integer, Double>> teamPossessionStrategy;
    private final MetricCalculationStrategy<HeatmapGrid> heatmapStrategy;

    // JAVA CONCEPT: Constructor chaining with `this(...)`
    // The no-arg constructor builds the default strategies and passes them to the constructor below.
    // We keep it public because `new AnalyticsService()` is used by `Main` and by all the tests, and because
    // Spring uses it when it creates the @Service singleton (with two constructors and no @Autowired,
    // Spring picks the no-arg one).
    public AnalyticsService() {
        this(
            new DistanceSpeedStrategy(),
            PossessionStrategy.perPlayer(),
            PossessionStrategy.perTeam(),
            new HeatmapStrategy()
        );
    }

    // The "choose your strategies" constructor. It makes the pattern useful: a test (or a future feature)
    // can hand in a different algorithm without touching this class.
    public AnalyticsService(MetricCalculationStrategy<PlayerStats> distanceSpeedStrategy,
                            MetricCalculationStrategy<Map<Integer, Double>> playerPossessionStrategy,
                            MetricCalculationStrategy<Map<Integer, Double>> teamPossessionStrategy,
                            MetricCalculationStrategy<HeatmapGrid> heatmapStrategy) {
        this.distanceSpeedStrategy = distanceSpeedStrategy;
        this.playerPossessionStrategy = playerPossessionStrategy;
        this.teamPossessionStrategy = teamPossessionStrategy;
        this.heatmapStrategy = heatmapStrategy;
    }

    // =====================================================================
    // 1. KINEMATIC CALCULATIONS (Distance & Speed)
    // =====================================================================

    // Calculates total distance and max speed for a specific entity across all frames.
    public PlayerStats calculatePlayerStats(List<FrameData> frames, int entityId) {
        return distanceSpeedStrategy.calculate(frames, MetricParams.forPlayer(entityId));
    }

    // =====================================================================
    // 2. BALL POSSESSION (Player and Team level)
    // =====================================================================

    public Map<Integer, Double> calculatePossessionTimePerPlayer(List<FrameData> frames, double radiusMeters) {
        return playerPossessionStrategy.calculate(frames, MetricParams.forPossession(radiusMeters));
    }

    // OOP CONCEPT: Method Overloading
    // We have two methods with the exact same name, but different parameters.
    // This provides a convenient default. If the user doesn't specify a `radiusMeters`,
    // we call the main method using the default constant.
    public Map<Integer, Double> calculatePossessionTimePerPlayer(List<FrameData> frames) {
        return calculatePossessionTimePerPlayer(frames, MatchConstants.BALL_CONTROL_RADIUS);
    }

    // Team possession uses the SAME algorithm as player possession (`PossessionStrategy`).
    // The only difference is the strategy object we ask: the one built with `perTeam()`.
    public Map<Integer, Double> calculatePossessionTimePerTeam(List<FrameData> frames, double radiusMeters) {
        return teamPossessionStrategy.calculate(frames, MetricParams.forPossession(radiusMeters));
    }

    public Map<Integer, Double> calculatePossessionTimePerTeam(List<FrameData> frames) {
        return calculatePossessionTimePerTeam(frames, MatchConstants.BALL_CONTROL_RADIUS);
    }

    // =====================================================================
    // 3. HEATMAP GENERATION
    // =====================================================================

    public HeatmapGrid generatePlayerHeatmap(List<FrameData> frames, int entityId, int rows, int cols, double fieldWidth, double fieldHeight) {
        return heatmapStrategy.calculate(frames, MetricParams.forHeatmap(entityId, rows, cols, fieldWidth, fieldHeight));
    }

    public HeatmapGrid generatePlayerHeatmap(List<FrameData> frames, int entityId) {
        return generatePlayerHeatmap(frames, entityId, MatchConstants.DEFAULT_GRID_ROWS, MatchConstants.DEFAULT_GRID_COLS, MatchConstants.FIELD_LENGTH, MatchConstants.FIELD_WIDTH);
    }
}
