package com.taticanalytics.exception;

// PURPOSE:
// A domain-specific exception that signals "the tracking file could not be loaded".
// Before this class existed, `TrackingDataLoader.loadData` swallowed the `IOException` and
// returned an empty list, so callers could not tell "a match with zero frames" apart from
// "the file was missing or corrupted". Throwing this exception makes the failure impossible to ignore.
//
// JAVA CONCEPT: Checked vs. Unchecked Exceptions
// `IOException` is CHECKED: every method in the call chain must either catch it or declare `throws`.
// By extending `RuntimeException` this one is UNCHECKED, so it can travel up the call stack
// without forcing a `throws` clause on every method in between. The caller that actually knows
// how to react (e.g. `Main`, which prints a friendly message) is the one that catches it.
public class TrackingDataLoadException extends RuntimeException {

    // JAVA CONCEPT: Exception chaining
    // We keep the original exception as the `cause`, so no information is lost:
    // the message says WHAT failed (including the file path), and `getCause()` says WHY.
    public TrackingDataLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
