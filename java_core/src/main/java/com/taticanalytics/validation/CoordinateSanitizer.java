package com.taticanalytics.validation;

import java.util.Optional;

// LIBRARY: SLF4J (same logging API used by the loaders)
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.taticanalytics.util.MatchConstants;

// PURPOSE (TA-76):
// Applies the coordinate policy to every entity reading of ONE tracking file and keeps score of
// what it did, so the loader can log a single summary at the end:
//   - INSIDE         -> the position is kept as it came;
//   - WITHIN_MARGIN  -> the position is clamped to the pitch and kept (measurement noise);
//   - OUT_OF_RANGE   -> the entity is discarded (the value cannot be trusted).
// `CoordinateValidator` only CLASSIFIES a point. This class decides what to DO about it and counts.
//
// DESIGN NOTE: why this is NOT a Spring bean (`@Component`)
// It holds state (the counters), and each tracking file needs its own fresh counters. A bean would be
// a single shared instance, so the counts of one load would leak into the next and two loads running
// at the same time would corrupt each other's numbers. Each loader creates a `new CoordinateSanitizer()`
// per file instead. The stateless rule itself still lives in the static `CoordinateValidator`.
public class CoordinateSanitizer {

    private static final Logger logger = LoggerFactory.getLogger(CoordinateSanitizer.class);

    // LOGGING CONCEPT: avoid flooding the log.
    // A real match has millions of readings, so a systematic problem could produce hundreds of
    // thousands of identical WARN lines and bury everything else. We detail only the first discards;
    // after that they are still COUNTED and show up in the summary.
    private static final int MAX_DETAILED_WARNINGS = 10;

    // JAVA CONCEPT: Records (Java 16+)
    // A `record` is a compact immutable class: the compiler generates the constructor, the accessors
    // `x()` and `y()`, `equals`, `hashCode` and `toString`. Perfect for a small "pair of values".
    // (`EntityDTO` and `FrameDataDTO` are records too.)
    public record Position(double x, double y) {}

    private int total = 0;
    private int clamped = 0;
    private int discarded = 0;

    // PURPOSE:
    // Checks one reading. Returns the position to use, or an EMPTY Optional when the entity must be
    // discarded.
    //
    // JAVA CONCEPT: Optional
    // `Optional<Position>` is a box that holds a value OR is empty. It forces the caller to deal with the
    // "nothing to return" case, instead of returning `null` and risking a NullPointerException later.
    //
    // JAVA CONCEPT: switch expression over an enum
    // `switch (status) { case X -> ...; }` is a switch that RETURNS a value. Because `status` is an
    // enum, the compiler checks that EVERY value is handled: if someone adds a new constant to
    // `CoordinateStatus` and forgets it here, this code stops compiling.
    public Optional<Position> sanitize(int entityId, double x, double y) {
        total++;

        CoordinateStatus status = CoordinateValidator.classify(x, y);

        return switch (status) {
            case INSIDE -> Optional.of(new Position(x, y));

            case WITHIN_MARGIN -> {
                clamped++;
                double fixedX = CoordinateValidator.clamp(x, MatchConstants.FIELD_LENGTH);
                double fixedY = CoordinateValidator.clamp(y, MatchConstants.FIELD_WIDTH);
                // DEBUG, not WARN: clamping is expected noise, and one line per reading is too much.
                logger.debug("Entity {}: ({}, {}) is slightly outside the pitch, clamped to ({}, {})",
                    entityId, x, y, fixedX, fixedY);
                yield Optional.of(new Position(fixedX, fixedY));
            }

            case OUT_OF_RANGE -> {
                discarded++;
                if (discarded <= MAX_DETAILED_WARNINGS) {
                    logger.warn("Entity {}: ({}, {}) is more than {} m outside the pitch (or not a number); entity discarded",
                        entityId, x, y, MatchConstants.COORDINATE_MARGIN);
                } else if (discarded == MAX_DETAILED_WARNINGS + 1) {
                    logger.warn("More than {} entities were discarded; further discards are only counted (see the summary)",
                        MAX_DETAILED_WARNINGS);
                }
                yield Optional.empty();
            }
        };
    }

    // PURPOSE:
    // Share of the readings that needed some correction (clamped or discarded), from 0.0 to 1.0.
    // GOTCHA: with zero readings the division would be 0 / 0 = NaN, so that case returns 0.0 explicitly.
    // SYNTAX: the `(double)` cast in the return below matters. Without it Java would do INTEGER division
    // (e.g. 3 / 100 = 0) and the ratio would always be 0. Casting one side to `double` makes it a real division.
    public double getCorrectedRatio() {
        if (total == 0) {
            return 0.0;
        }
        return (double) (clamped + discarded) / total;
    }

    // PURPOSE:
    // Logs ONE line with the totals for the file and, when the share of corrected readings is above
    // `MatchConstants.COORDINATE_ALERT_RATIO` (5%), an extra WARN telling someone to check the data source.
    public void logSummary() {
        logger.info("Coordinate validation: {} entity readings checked, {} clamped, {} discarded",
            total, clamped, discarded);

        if (getCorrectedRatio() > MatchConstants.COORDINATE_ALERT_RATIO) {
            logger.warn("ALERT: {}% of the entity readings needed correction (limit: {}%). "
                    + "This looks systematic: check the calibration of the CV pipeline that produced the file.",
                String.format("%.1f", getCorrectedRatio() * 100),
                String.format("%.0f", MatchConstants.COORDINATE_ALERT_RATIO * 100));
        }
    }

    public int getTotal() {
        return total;
    }

    public int getClampedCount() {
        return clamped;
    }

    public int getDiscardedCount() {
        return discarded;
    }
}