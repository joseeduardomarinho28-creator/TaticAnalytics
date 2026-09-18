package com.taticanalytics.model;

// PURPOSE:
// This package declaration tells Java that this class belongs to the 'model' group
// of our application. In software architecture, 'model' classes usually represent 
// the core business data or physical concepts of the domain (like a football field).

// OOP CONCEPT: Abstraction and Inheritance
// The `abstract` keyword means you CANNOT create a direct instance of this class.
// You cannot do: Entity e = new Entity(...);
// 
// Why? Because in a football game, there is no generic "Entity" running on the field. 
// There are Players, Referees, and Balls. This class exists purely to be a "parent" 
// (superclass) that shares common characteristics (id, x, y coordinates) with its 
// "children" (subclasses).
public abstract class Entity {
    
    // SYNTAX & OOP CONCEPT: Access Modifiers & State
    // `protected` means these variables are hidden from the outside world (like `private`),
    // BUT they are directly accessible to any subclass that `extends Entity` (like Player or Ball).
    // 
    // `int` (integer) is used for the ID because IDs are whole numbers.
    // `double` is a primitive type used for numbers with decimal points (fractions), 
    // which is necessary for precise spatial coordinates (x, y) on the field.
    protected int id;
    protected double x;
    protected double y;

    // OOP CONCEPT: Constructor
    // This is the constructor. Even though `Entity` is abstract and cannot be instantiated
    // directly, its subclasses (like Player) will still need to call this constructor 
    // (using the `super()` keyword) to initialize these common fields when they are created.
    public Entity(int id, double x, double y) {
        // SYNTAX: The `this` keyword
        // `id` on the right refers to the parameter passed into the constructor.
        // `this.id` on the left refers to the actual field belonging to the object.
        // We use `this` to resolve the naming collision (shadowing) since they have the same name.
        this.id = id;
        this.x = x;
        this.y = y;
    }
    
    // OOP CONCEPT: Encapsulation (Getters)
    // These public methods allow the outside world to read the hidden (protected) data safely.
    public int getId() {
        return this.id;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    // OOP CONCEPT: Encapsulation & Mutability (Setters)
    // These methods allow the outside world to modify the x and y coordinates.
    // Because the tracking system updates the positions frame by frame, the coordinates
    // must be mutable (changeable).
    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }
    
    // LEARNING NOTE: 
    // Notice that there is NO `setId(int id)` method. 
    // This is a deliberate design decision. Once an entity is created with an ID, 
    // that ID should never change during the lifespan of the object. By omitting the setter,
    // the `id` field effectively becomes read-only (immutable) to the outside world.
}