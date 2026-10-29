package com.taticanalytics.validation;

// PURPOSE (TA-76):
// The possible answers to the question "where is this (x, y) point in relation to the pitch?".
// `CoordinateValidator.classify` returns one of these values, and the tracking loaders decide what
// to do with each one (keep it, clamp it, or discard the entity).
//
// JAVA CONCEPT: Enum
// An `enum` is a type with a CLOSED list of values, declared right here. The compiler only accepts
// those values, so nobody can pass `7` or misspell `"insde"` by mistake, which would compile fine
// if we used an `int` or a `String`. A `boolean` is not enough either, because there are THREE
// possible answers, not two. Enums also work well in a `switch`, and the compiler can warn when a
// case is missing.
//
// DESIGN NOTE: the names describe WHERE the point is, not WHAT to do with it. Deciding the action is
// the loader's job, so if the policy changes later (e.g. discard instead of clamp), this enum
// does not need to change.
public enum CoordinateStatus {

    // x in [0, 105] and y in [0, 68]: inside the pitch, the value is used exactly as it came.
    INSIDE,

    // Outside the pitch, but no more than `MatchConstants.COORDINATE_MARGIN` (2 m) away:
    // treated as measurement noise, so the value is clamped to the nearest edge and kept.
    WITHIN_MARGIN,

    // More than 2 m outside the pitch (or not a valid number): the value cannot be trusted,
    // so the entity is discarded and a WARN is logged.
    OUT_OF_RANGE
}