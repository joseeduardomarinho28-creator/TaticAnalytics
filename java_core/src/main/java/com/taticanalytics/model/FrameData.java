package com.taticanalytics.model;

// LIBRARY: Java Standard Library (Collections Framework)
// These imports bring in built-in Java classes for handling groups of objects.
// `List` is an INTERFACE (a contract describing what a list can do, like add/remove/get).
// `ArrayList` is a concrete IMPLEMENTATION of that interface (how it actually works internally 
// using a resizable array).
// In Python, a simple `list = []` does this automatically. In Java, we must import them.
import java.util.List;
import java.util.ArrayList;

// PURPOSE:
// This class represents a single "snapshot" of the football match at a specific fraction 
// of a second. It groups together the time information (timestamp) and all the physical 
// objects (entities like players and the ball) present on the pitch at that exact moment.
//
// OOP CONCEPT: Composition (Has-A Relationship)
// While `Player` "IS-A" `Entity` (Inheritance), a `FrameData` "HAS-A" List of Entities.
// This is called Composition or Aggregation.
public class FrameData {
    
    // SYNTAX & ENCAPSULATION:
    // `private` fields protect the internal state of the frame.
    private int frameId;
    private double timestamp;
    
    // OOP CONCEPT: Generics and Interfaces
    // `List<Entity>` uses Java Generics (the angle brackets `< >`). 
    // This tells the Java compiler: "This list is strictly allowed to hold objects 
    // of type `Entity` (or its subclasses like `Player` and `Ball`)."
    // If you try to put a `String` in this list, the code will fail to compile.
    // 
    // LEARNING NOTE (Programming to an Interface):
    // Notice the type of the variable is `List`, not `ArrayList`. 
    // It is a Java best practice to declare variables using the Interface type (`List`) 
    // rather than the implementation type (`ArrayList`). This gives you the flexibility 
    // to swap out the underlying implementation later without changing the rest of your code.
    private List<Entity> entities;

    // OOP CONCEPT: Constructor and Object Initialization
    public FrameData(int frameId, double timestamp) {
        this.frameId = frameId;
        this.timestamp = timestamp;
        
        // JAVA CONCEPT: Instantiating the Collection
        // Notice that `entities` is NOT passed as a parameter in the constructor.
        // Instead, when a `FrameData` object is created, we automatically initialize 
        // `this.entities` with a brand new, empty `ArrayList`.
        // The `<>` is called the "diamond operator" and it automatically infers the 
        // type (`Entity`) from the variable declaration above.
        this.entities = new ArrayList<>();
    }

    // OOP CONCEPT: Mutator Method & Polymorphism
    // This method allows the outside world to add entities to the frame one by one.
    //
    // WHY THIS IS POWERFUL (Polymorphism):
    // The parameter expects an `Entity`. Because `Player` and `Ball` both extend `Entity`,
    // you can pass `player1`, `player2`, and `matchBall` into this exact same method, 
    // and they will all be safely stored in the list.
    public void addEntity(Entity entity) {
        // We delegate the action to the built-in `.add()` method of the ArrayList.
        this.entities.add(entity);
    }

    // GETTERS:
    // Standard encapsulation to allow reading the private fields.
    public int getFrameId() {
        return frameId;
    }

    public double getTimestamp() {
        return timestamp;
    }

    // LEARNING NOTE: Potential Encapsulation "Leak"
    // This getter returns the memory reference to the actual `ArrayList` used internally.
    // This means if another class calls `frame.getEntities().clear()`, it will 
    // erase the contents of the list inside this object! 
    // In advanced Java, to prevent this, you might return an unmodifiable copy:
    // `return java.util.Collections.unmodifiableList(entities);`
    // However, for an MVP/data-transfer object, returning the direct list is very common.
    public List<Entity> getEntities() {
        return entities;
    }
}