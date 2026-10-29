package com.taticanalytics.io.dto;

// OOP CONCEPT: DTO (Data Transfer Object) Pattern
// The `dto` package indicates that classes here are used purely to transfer data 
// from one boundary of the application to another (e.g., reading from a JSON file 
// before passing it deeper into the system). They do not contain complex business logic.

// LIBRARY:
// `com.fasterxml.jackson` is a very popular external dependency (added via Maven) 
// used for parsing JSON into Java objects (deserialization) and vice-versa (serialization).
import com.fasterxml.jackson.annotation.JsonProperty;

// PURPOSE:
// This file defines the exact structure expected when reading a single entity 
// (a player, ball, or referee) from the raw JSON file.

// SYNTAX / JAVA CONCEPT: `record` (Introduced in Java 14)
// A `record` is a special kind of class designed purely to carry immutable (unchangeable) data.
// It is the Java equivalent of Python's `@dataclass(frozen=True)` or `collections.namedtuple`.
// By declaring this as a `record` instead of a `class`, Java automatically generates:
// 1. A constructor requiring all these parameters.
// 2. Private, final fields for everything inside the parentheses.
// 3. Read-only getter methods (e.g., `entity.type()`, `entity.teamId()`).
// 4. Boilerplate methods like `toString()`, `equals()`, and `hashCode()`.

// ANNOTATION:
// `@JsonProperty("...")` tells the Jackson library exactly which JSON key maps 
// to which Java variable. For example, if the JSON has `"team_id": 1`, but the 
// Java variable is named `teamId`, this annotation bridges that naming gap.

// LEARNING NOTE: Primitive vs Wrapper classes (`int` vs `Integer`)
// `teamId` is an `Integer` (capital I), an object that can be null, unlike the primitive `int` (lowercase).
// - `int` is a primitive type. It CANNOT be null. It defaults to 0.
// - `Integer` is a full Java Object (a wrapper around `int`). It CAN be null.
// Looking at our domain, a Ball or a Referee doesn't belong to a team, so their 
// "teamId" will be explicitly `null` or missing in the JSON. If we used the primitive `int`, 
// Java would crash or automatically default it to 0 (which could be a real team ID).
// Using `Integer` safely allows the value to be missing/null.
//
// TA-76: the same idea now applies to `id`, `x` and `y`, which are REQUIRED by the data contract.
// With the primitives `int` / `double`, a JSON entity WITHOUT `"x"` was silently read as `x = 0.0`, so a
// player with no position showed up in the corner of the pitch and nobody noticed. With the wrappers
// `Integer` / `Double`, a missing field becomes `null`, and the parser can reject it with a clear message
// instead of inventing a value. Records are read-only, so `x()` still works like before.
public record EntityDTO(@JsonProperty("type") String type, @JsonProperty("id") Integer id, @JsonProperty("x") Double x, @JsonProperty("y") Double y, @JsonProperty("team_id") Integer teamId) {}