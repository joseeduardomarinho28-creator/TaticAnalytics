package com.taticanalytics.model;

// PURPOSE:
// This class represents a specific object in our domain: a football Player.
// It is part of the 'model' package, meaning its main job is to hold data 
// (the state of a player) rather than perform complex business logic.

// OOP CONCEPT: Inheritance
// The keyword `extends` creates an "is-a" relationship. 
// Java interprets this as: "A Player IS AN Entity."
// Because it extends `Entity`, this `Player` class automatically inherits all the 
// fields (id, x, y) and methods (getId, getX, setX, etc.) from the `Entity` class, 
// even though they are not explicitly written in this file!
public class Player extends Entity {
    
    // SYNTAX & OOP CONCEPT: Encapsulation (Private fields)
    // `private` means this specific variable can ONLY be seen and modified from 
    // inside this exact `Player` class. 
    // This is stricter than the `protected` modifier we saw in `Entity`. 
    // It is a best practice to make fields private and control access via methods.
    private int teamId;

    // OOP CONCEPT: Constructor & Object Creation
    // This is the constructor. When we want to create a new player in memory, 
    // we use the `new` keyword, like this: 
    // Player p1 = new Player(7, 45.0, 30.5, 1);
    public Player(int id, double x, double y, int teamId) {
        // JAVA CONCEPT: The `super` keyword
        // `super()` is a mandatory call to the constructor of the parent class (Entity).
        // Since `Entity` is responsible for `id`, `x`, and `y`, we must pass those 
        // values "up the chain" so the parent can initialize its own fields.
        // Rule: If used, `super()` MUST be the very first line inside the constructor.
        super(id, x, y);
        
        // After the parent is initialized, we initialize the field specific to this class.
        // Again, `this.teamId` refers to the class field, while `teamId` refers to the parameter.
        this.teamId = teamId;
    }

    // OOP CONCEPT: Getter method
    // Since `teamId` is private, the outside world needs this public method to read it.
    public int getTeamId() {
        return this.teamId;
    }

    // OOP CONCEPT: Setter method
    // This allows the outside world to change the player's team after the object is created.
    // 
    // LEARNING NOTE: A more idiomatic Java naming convention would be `setTeamId` 
    // to perfectly match the `teamId` field, but `setTeam` is perfectly valid 
    // and works exactly the same way.
    public void setTeam(int teamId) {
        this.teamId = teamId;
    }
    
    // EXECUTION FLOW (Mental Model):
    // If you call `player.getX()`, Java will look inside this `Player` class. 
    // It won't find `getX()` here. Because of `extends Entity`, Java will automatically 
    // look up at the parent `Entity` class, find `getX()` there, and execute it!
}