package com.taticanalytics.service.strategy;

import java.util.List;

import com.taticanalytics.model.Entity;
import com.taticanalytics.model.FrameData;
import com.taticanalytics.model.HeatmapGrid;
import com.taticanalytics.model.Player;

// PURPOSE (TA-77):
// The heatmap algorithm, moved out of `AnalyticsService.generatePlayerHeatmap` into its own class.
// Just like `DistanceSpeedStrategy`, the logic is EXACTLY the same as before: only its home changed.
//
// JAVA CONCEPT: the same interface, a different result type
// `MetricCalculationStrategy<HeatmapGrid>` uses the same interface as the other strategies, but the
// placeholder `T` is now `HeatmapGrid`. This is the whole point of the generic `<T>`: one contract
// (`calculate(frames, params)`), three different kinds of result (`PlayerStats`, a `Map`, a `HeatmapGrid`).
public class HeatmapStrategy implements MetricCalculationStrategy<HeatmapGrid> {

    @Override
    public HeatmapGrid calculate(List<FrameData> frames, MetricParams params) {

        // The old method received these five values as separate parameters. Now they arrive packed in
        // `params`, so we take them out here. The rest of the body can stay exactly as it was.
        int entityId = params.entityId();
        int rows = params.rows();
        int cols = params.cols();
        double fieldWidth = params.fieldWidth();
        double fieldHeight = params.fieldHeight();

        HeatmapGrid heatmap = new HeatmapGrid(rows, cols, fieldWidth, fieldHeight);

        Entity previousEntity = null;
        int previousFrameId = -1;
        double previousTimestamp = 0.0;

        for (FrameData frame : frames) {
            for (Entity entity : frame.getEntities()) {
                // Same rule as in `calculatePlayerStats`: only a `Player` can match (TA-107).
                if (entity instanceof Player player && player.getId() == entityId) {
                    
                     if ((previousEntity != null) && (frame.getFrameId() - previousFrameId == 1)) {
                        double deltaTime = frame.getTimestamp() - previousTimestamp;

                        if (deltaTime > 0.0) {
                            // Note: We add time to the cell the player was in DURING this time interval.
                            heatmap.addTime(previousEntity.getX(), previousEntity.getY(), deltaTime);
                        }
                     }
                    previousEntity = entity;
                    previousFrameId = frame.getFrameId();
                    previousTimestamp = frame.getTimestamp();
                }
            }
        }
        return heatmap;
    }
}
