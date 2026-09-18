package com.taticanalytics.model;

// LIBRARY & DEPENDENCY:
// This imports a specific class from another package in our project.
// `MatchConstants` likely contains `public static final` variables (constants)
// that define fixed rules or dimensions for the game, so we don't have to 
// hardcode "magic numbers" (like 105.0 for field length) directly into our logic.
import com.taticanalytics.util.MatchConstants;

// PURPOSE:
// This class represents a spatial Heatmap. It virtually divides the football pitch 
// into a grid of smaller rectangles (cells). As players move, this class tracks 
// how much time (`deltaTime`) they spend in each specific cell.
public class HeatmapGrid {
    
    // SYNTAX: Fields (State)
    private int rows;
    private int cols;
    private double fieldWidth;
    private double fieldHeight;
    
    // JAVA CONCEPT: 2D Arrays
    // `double[][]` means a "two-dimensional array of doubles" (a matrix or grid).
    // In Java, a 2D array is actually an "array of arrays".
    // The first bracket `[]` usually represents the rows (Y axis), and the 
    // second bracket `[]` represents the columns (X axis).
    private double[][] grid;

    // OOP CONCEPT: Constructor Overloading & Delegation
    // This is the "Default Constructor" (it takes no arguments).
    public HeatmapGrid() {
        // SYNTAX: Constructor Delegation using `this(...)`
        // When `this()` is used like a method call as the very first line of a constructor,
        // it means: "Don't duplicate code here; instead, call the OTHER constructor 
        // in this same class that matches these arguments."
        // 
        // We are passing default values pulled from the `MatchConstants` class.
        // `MatchConstants.FIELD_LENGTH` is accessed directly using the class name,
        // which tells us `FIELD_LENGTH` is a `static` variable.
        this(
            MatchConstants.DEFAULT_GRID_ROWS,
            MatchConstants.DEFAULT_GRID_COLS,
            MatchConstants.FIELD_LENGTH, // Note: Length is usually the X axis (width in this context)
            MatchConstants.FIELD_WIDTH   // Note: Width is usually the Y axis (height in this context)
        );
    }

    // OOP CONCEPT: Parameterized Constructor
    // This constructor actually initializes the object. The default constructor above 
    // routes its data here.
    public HeatmapGrid(int rows, int cols, double fieldWidth, double fieldHeight) {
        // JAVA CONCEPT: Array Initialization
        // Arrays in Java have a fixed size. You must declare exactly how big they are 
        // when you create them using the `new` keyword.
        // `new double[rows][cols]` creates a grid with `rows` number of rows and `cols` columns.
        // IMPORTANT: In Java, when you create an array of numbers (like double), 
        // all cells are automatically filled with `0.0`. You don't need to manually fill them!
        this.grid = new double[rows][cols];
        this.rows = rows;
        this.cols = cols;
        this.fieldWidth = fieldWidth;
        this.fieldHeight = fieldHeight;
    }

    // PURPOSE:
    // This method takes a physical position on the field (x, y) and an amount of time 
    // (deltaTime), figures out which grid cell that position belongs to, and adds the time.
    public void addTime (double x, double y, double deltaTime) {
        
        // EXECUTION FLOW: Step 1 - Calculate Cell Dimensions
        // Divide the total field size by the number of columns/rows to find out 
        // how many meters wide and tall each individual grid cell is.
        double cellWidth = fieldWidth / cols;
        double cellHeight = fieldHeight / rows;

        // EXECUTION FLOW: Step 2 - Map Coordinates to Array Indices
        // JAVA CONCEPT: Type Casting `(int)`
        // `x / cellWidth` might result in a decimal, like 5.8. 
        // But array indices MUST be whole numbers (integers).
        // By putting `(int)` in front of the calculation, we force Java to chop off 
        // the decimal part (truncation). So 5.8 becomes 5. 
        // In Python, you would use integer division: `x // cellWidth`.
        int colInd = (int) (x / cellWidth);
        int rowInd = (int) (y / cellHeight);

        // EXECUTION FLOW: Step 3 - Boundary Checking (Safety)
        // JAVA CONCEPT: Logical AND (`&&`)
        // Arrays in Java strictly enforce their boundaries. If you try to access `grid[100][0]` 
        // on a grid that only has 10 rows, Java will crash your program with an 
        // `ArrayIndexOutOfBoundsException`.
        // This `if` statement ensures that the calculated row and column indices are 
        // greater than or equal to 0, AND strictly less than the maximum size.
        if (rowInd >= 0 && rowInd < rows && colInd >= 0 && colInd < cols) {
            
            // SYNTAX: Compound Assignment Operator (`+=`)
            // `a += b` is a shortcut for `a = a + b`.
            // We find the exact cell using `grid[rowInd][colInd]` and add the `deltaTime`.
            grid[rowInd][colInd] += deltaTime;
        }
    }

    // GETTERS
    
    // LEARNING NOTE: Reference Leak
    // Just like returning a `List`, returning a 2D array directly gives the caller 
    // access to the original memory block. Another class could call `getGrid()[0][0] = 999.0;`
    // and corrupt this heatmap's data. 
    public double[][] getGrid() {
        return grid;
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }
}