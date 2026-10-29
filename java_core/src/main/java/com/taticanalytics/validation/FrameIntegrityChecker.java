package com.taticanalytics.validation;

import java.util.HashSet;
import java.util.Set;

// LIBRARY: SLF4J (same logging API used by the loaders)
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.taticanalytics.model.Ball;
import com.taticanalytics.model.Entity;
import com.taticanalytics.model.Player;

// PURPOSE (TA-76):
// Checks the STRUCTURE of the entities of each frame, which is a different question from "where is the
// entity?" (that one is answered by `CoordinateSanitizer`). The data contract says that, inside one frame:
//   - a player's `team_id` is 1 or 2;
//   - there is at most ONE ball;
//   - every entity has a unique `id` (across players, ball and referees).
// An entity that breaks a rule is DISCARDED (with a WARN) and the frame is kept, the same tolerant policy
// used for coordinates. The first entity wins: if two entities share an id, the later one is the one dropped.
//
// WHY THESE RULES MATTER: the calculations find an entity by its id, so two entities with the same id would
// get mixed in the same player's distance; and possession per team credits `team_id` as if it were a team,
// so a `team_id` of 0 or 3 would create a "phantom team" in the result.
//
// DESIGN NOTE: like `CoordinateSanitizer`, this class holds state (the ids already seen in the current frame
// and the counters), so each loader creates a NEW one per file instead of sharing a Spring bean.
// It works on the domain `Entity`, which both loaders build, so the rules live in ONE place.
public class FrameIntegrityChecker {

    private static final Logger logger = LoggerFactory.getLogger(FrameIntegrityChecker.class);

    // Same idea as in `CoordinateSanitizer`: detail only the first problems, count the rest.
    private static final int MAX_DETAILED_WARNINGS = 10;

    // The two valid team ids of the data contract.
    private static final int FIRST_TEAM_ID = 1;
    private static final int SECOND_TEAM_ID = 2;

    // JAVA CONCEPT: Set
    // A `Set` holds each value at most once, and `add` tells us whether the value was NEW: it returns
    // `false` when the value was already there. That is exactly the question "have I seen this id in
    // this frame?". `HashSet` is the usual implementation, with very fast lookups.
    private final Set<Integer> idsInCurrentFrame = new HashSet<>();
    private boolean ballSeenInCurrentFrame = false;

    private int invalidTeamCount = 0;
    private int extraBallCount = 0;
    private int duplicateIdCount = 0;

    // PURPOSE:
    // Must be called when a NEW frame starts: the "ids seen" and "ball seen" memory only makes sense
    // inside one frame, so it is cleared here. Without this call, id 7 in frame 2 would be reported as a
    // duplicate of id 7 in frame 1.
    public void startFrame() {
        idsInCurrentFrame.clear();
        ballSeenInCurrentFrame = false;
    }

    // PURPOSE:
    // Returns `true` if the entity can be added to the frame, or `false` if it must be discarded.
    //
    // EXECUTION FLOW: all the checks run FIRST and the memory is only updated at the end, once the
    // entity is known to be accepted. Otherwise a rejected entity would still "use up" its id or the
    // frame's only ball.
    //
    // JAVA CONCEPT: pattern matching for `instanceof` (Java 16+)
    // `entity instanceof Player player` checks the type AND already gives a variable of that type,
    // so there is no separate cast like `(Player) entity`.
    public boolean accept(int frameId, Entity entity) {

        if (entity instanceof Player player && !isValidTeam(player.getTeamId())) {
            invalidTeamCount++;
            warn("Frame {}: player {} has team_id {} (expected {} or {}); entity discarded",
                frameId, entity.getId(), player.getTeamId(), FIRST_TEAM_ID, SECOND_TEAM_ID);
            return false;
        }

        if (entity instanceof Ball && ballSeenInCurrentFrame) {
            extraBallCount++;
            warn("Frame {}: ball {} is a second ball in the same frame; entity discarded",
                frameId, entity.getId());
            return false;
        }

        if (idsInCurrentFrame.contains(entity.getId())) {
            duplicateIdCount++;
            warn("Frame {}: id {} is already used by another entity in this frame; entity discarded",
                frameId, entity.getId());
            return false;
        }

        // Accepted: now it is safe to remember it.
        idsInCurrentFrame.add(entity.getId());
        if (entity instanceof Ball) {
            ballSeenInCurrentFrame = true;
        }
        return true;
    }

    private boolean isValidTeam(int teamId) {
        return teamId == FIRST_TEAM_ID || teamId == SECOND_TEAM_ID;
    }

    // Logs the first few problems in detail; after that they are only counted (see `logSummary`).
    private void warn(String message, Object... arguments) {
        if (getDiscardedCount() <= MAX_DETAILED_WARNINGS) {
            logger.warn(message, arguments);
        } else if (getDiscardedCount() == MAX_DETAILED_WARNINGS + 1) {
            logger.warn("More than {} entities were discarded by the frame checks; further ones are only counted (see the summary)",
                MAX_DETAILED_WARNINGS);
        }
    }

    // PURPOSE: one line per file with what was discarded and why.
    public void logSummary() {
        logger.info("Frame checks: {} entities discarded ({} invalid team_id, {} extra balls, {} duplicate ids)",
            getDiscardedCount(), invalidTeamCount, extraBallCount, duplicateIdCount);
    }

    public int getInvalidTeamCount() {
        return invalidTeamCount;
    }

    public int getExtraBallCount() {
        return extraBallCount;
    }

    public int getDuplicateIdCount() {
        return duplicateIdCount;
    }

    public int getDiscardedCount() {
        return invalidTeamCount + extraBallCount + duplicateIdCount;
    }
}