package com.taticanalytics.model;

// PURPOSE:
// This class represents the match Referee on the football pitch.
// Just like `Player` and `Ball`, it is part of the core domain model.

// OOP CONCEPT: Inheritance and Semantic Typing
// Similar to the `Ball` class, this class exists primarily to provide a distinct TYPE.
// While a `Player` needs a `teamId` (because players belong to teams), a Referee does not.
// By creating a specific `Referee` class that extends `Entity`, we allow the system 
// to track the referee's physical coordinates without confusing them with a player.
// 
// For example, if the `AnalyticsService` is calculating ball possession by finding 
// the closest person to the ball, we can easily ignore the Referee using the type:
// `if (entity instanceof Referee) { continue; }`
public class Referee extends Entity {

    // OOP CONCEPT: Constructor Delegation
    // When a new Referee object is created from the JSON tracking data:
    // Referee ref = new Referee(99, 52.5, 34.0);
    public Referee(int id, double x, double y) {
        
        // JAVA CONCEPT: The `super` call
        // Just like we saw in the `Ball` and `Player` classes, we must pass the 
        // ID and spatial coordinates "upwards" to the parent `Entity` class so 
        // they can be properly stored and managed.
        super(id, x, y);
    }
}