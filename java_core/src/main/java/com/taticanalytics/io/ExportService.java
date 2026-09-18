package com.taticanalytics.io;

// LEARNING NOTE:
// `io` stands for Input/Output. This package is dedicated to classes that handle 
// reading from or writing to the outside world (like files, databases, or networks).

// LIBRARY:
// `com.fasterxml.jackson` is an external dependency (from Maven).
// `ObjectMapper` is the core class of Jackson, responsible for converting Java objects to JSON and vice-versa.
// `SerializationFeature` provides configuration options for how Jackson writes the JSON.
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

// LIBRARY:
// `java.io` is part of the Java Standard Library.
// It provides the classical, stream-based classes for file manipulation.
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;

// Java standard collections
import java.util.Map;
import java.util.List;

// PURPOSE:
// This class is responsible for exporting calculated data to physical files (JSON and CSV).
// OOP CONCEPT: Single Responsibility Principle (SRP).
// By keeping this logic here, our AnalyticsService doesn't need to know *how* to save files. 
// It just calculates, and this ExportService just saves.
public class ExportService {
    
    // SYNTAX: `private final`
    // `private`: Only this class can access this variable.
    // `final`: Once this variable is assigned a value (in the constructor), it can NEVER be reassigned. 
    // This is a great practice for service dependencies.
    private final ObjectMapper objectMapper;

    // OOP CONCEPT: Constructor
    // When `new ExportService()` is called, this block runs.
    public ExportService() {
        // EXECUTION FLOW:
        // We initialize the Jackson ObjectMapper.
        this.objectMapper = new ObjectMapper();
        
        // We configure it so the output JSON is formatted nicely with line breaks and indentation
        // (often called "pretty printing"), rather than being one giant unreadable line of text.
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    // SYNTAX: Generics `<T>`
    // The `<T>` before the return type `void` declares a Generic Type parameter.
    // `T data` means this method can accept ANY type of Java object as its first argument.
    // Whether you pass a `PlayerStats`, a `List<FrameData>`, or a `HeatmapGrid`, Java will adapt `T` to match it.
    //
    // JAVA CONCEPT: Checked Exceptions (`throws IOException`)
    // Writing to a disk might fail (disk full, permission denied). Java forces us to acknowledge this.
    // `throws IOException` tells the compiler: "I am not handling the error here. If it fails, 
    // whoever called this method must deal with the crash."
    public <T> void exportToJson(T data, String filePath) throws IOException {
        
        // JAVA CONCEPT: The `File` object.
        // A `File` object in Java does NOT immediately create a file on your hard drive. 
        // It is merely an abstract representation of a file path in memory.
        File file = new File(filePath);

        // HOW IT WORKS: Safe Directory Creation
        // `file.getParentFile()` gets the folder containing the file.
        // If the folder doesn't exist, attempting to write a file inside it will crash.
        // `.mkdirs()` safely creates the folder (and any missing parent folders) on the hard drive.
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        
        // DATA FLOW: 
        // The Jackson library inspects the `data` object, translates its fields into JSON text, 
        // and writes that text directly into the physical file on the disk.
        objectMapper.writeValue(file, data);
    }

    // LEARNING NOTE: Complex Generics
    // `List<Map<String, Object>>` means a List where every item is a Map (Dictionary).
    // In that Map, the keys are Strings (e.g., "playerId"), and the values can be ANY Object (e.g., an Integer, a Double).
    public void exportPlayerStatsToCsv(List<Map<String, Object>> statsList, String filePath) throws IOException {
        File file = new File(filePath);
        
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }

        // SYNTAX / JAVA CONCEPT: `try-with-resources`
        // This is exactly like Python's `with open(filepath) as writer:`.
        // By putting the resource instantiation inside `try (...)`, Java guarantees that 
        // `writer.close()` will be called automatically when the block finishes, 
        // even if an error crashes the program inside the block.
        //
        // `FileWriter` opens the physical connection to the file.
        // `PrintWriter` wraps it to provide convenient methods like `println` and `printf`.
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            
            // Write the CSV header line.
            writer.println("playerId,teamId,totalDistance,maxSpeed");

            // Loop through each dictionary in our list.
            for (Map<String, Object> stat : statsList) {
                // `printf` formats the string. `%s` means "insert a string/value here". `%n` means newline.
                writer.printf("%s,%s,%s,%s%n",
                stat.get("playerId"),
                stat.get("teamId"),
                stat.get("totalDistance"),
                stat.get("maxSpeed"));
            }
        } // writer is automatically closed right here.
    }

    public void exportHeatmapToCsv(double[][] grid, String filePath) throws IOException {
        File file = new File(filePath);

        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            // Loop through each row of our 2D array.
            for (double[] row : grid) {
                
                // JAVA CONCEPT: StringBuilder
                // In Java, Strings are immutable (they cannot be changed). 
                // If you do `str = str + ","`, Java destroys the old string and creates a brand new one in memory.
                // Doing this in a loop causes severe memory and performance issues.
                // `StringBuilder` is a mutable buffer. It allows you to append text efficiently 
                // without creating thousands of temporary string objects.
                StringBuilder sb = new StringBuilder();

                // Loop through each cell in the current row.
                for (int i = 0; i < row.length; i++) {
                    sb.append(row[i]); // Add the number

                    // If it's not the last element in the row, append a comma.
                    // This prevents having a trailing comma at the end of the line (e.g., "1.0,2.0,3.0,").
                    if (i < row.length - 1) {
                        sb.append(",");
                    }
                }
                // Convert the optimized StringBuilder buffer back into a standard String and write it to the file.
                writer.println(sb.toString());
            }
        }
    }
}