package com.taticanalytics.service.strategy;

import java.util.List;

import com.taticanalytics.model.FrameData;

// PURPOSE (TA-77):
// The common "contract" of every metric calculation. Distance/speed, possession and heatmap are different
// algorithms, but they all do the same kind of job: take the frames of a match plus some parameters and
// produce a result. This interface says exactly that, and each algorithm becomes its own class.
//
// DESIGN PATTERN: Strategy
// The Strategy pattern puts each interchangeable algorithm behind the same interface, so the code that USES
// the algorithm (here, `AnalyticsService`) does not need to know HOW it works, only that it can call
// `calculate`. To add a new metric, we write a new class; the service does not need to change.
//
// JAVA CONCEPT: Generics (`<T>`)
// The three algorithms return different TYPES: `PlayerStats`, `Map<Integer, Double>` and `HeatmapGrid`.
// `<T>` is a placeholder for "some type, decided by whoever implements the interface". For example,
// `MetricCalculationStrategy<HeatmapGrid>` is a strategy whose `calculate` returns a `HeatmapGrid`, with
// no casting needed by the caller. (You already use this idea in `List<FrameData>`.)
public interface MetricCalculationStrategy<T> {

    T calculate(List<FrameData> frames, MetricParams params);
}