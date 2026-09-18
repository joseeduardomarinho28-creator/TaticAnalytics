package com.taticanalytics.model;

// PURPOSE:
// This class represents the football itself on the pitch.
// Like `Player`, it is part of the core domain model.

// OOP CONCEPT: Inheritance and Semantic Typing (Marker-like Class)
// You might look at this class and wonder: "Why create a whole class 
// if it doesn't add any new fields or methods to Entity?"
//
// In Object-Oriented Programming, creating a specific class just for the 
// type name is highly valuable. Even though `Ball` acts exactly like an `Entity`, 
// it provides a unique TYPE to the Java compiler. 
//
// Later on, when you have a mixed List of Entities (players, referees, balls),
// having this distinct `Ball` type allows your `AnalyticsService` to easily 
// locate the ball using the `instanceof` keyword (e.g., `if (entity instanceof Ball)`).
public class Ball extends Entity {

    // OOP CONCEPT: Constructor Delegation
    // When we instantiate the ball in memory using the `new` keyword: 
    // Ball matchBall = new Ball(0, 52.5, 34.0);
    // Java executes this constructor.
    public Ball(int id, double x, double y) {
        // JAVA CONCEPT: The `super` call
        // Because `Ball` extends `Entity`, it MUST ensure the parent class is 
        // properly initialized. 
        // `super(id, x, y)` takes the parameters provided to the `Ball` constructor 
        // and passes them "upwards" to the `Entity` constructor. The parent class 
        // then stores them in its `protected` fields (id, x, y).
        super (id, x, y);
    }
}