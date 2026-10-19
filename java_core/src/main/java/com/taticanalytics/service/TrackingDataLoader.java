package com.taticanalytics.service;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.io.File;

// LIBRARY: Jackson (JSON Parser)
// ObjectMapper is the core class of the Jackson library. It's the engine that 
// translates raw JSON text into Java objects.
import com.fasterxml.jackson.databind.ObjectMapper;
// JsonNode represents a single piece of the JSON document (an object, an array, or a value).
import com.fasterxml.jackson.databind.JsonNode;

import com.taticanalytics.exception.TrackingDataLoadException;
import com.taticanalytics.model.FrameData;
import com.taticanalytics.model.Player;
import com.taticanalytics.model.Ball;
import com.taticanalytics.model.Referee;

// LIBRARY: SLF4J (Simple Logging Facade for Java)
// SLF4J is a standard logging API that comes with the Spring Boot starter (backed by Logback).
// Unlike System.out.println, a logger has LEVELS (debug, info, warn, error) that can be turned
// on or off through configuration, without changing the code.
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// SPRING BOOT CONCEPT: @Component
// Like @Service, @Component tells Spring: "Create an instance of this class and manage it."
// @Service is usually for business logic, while @Component is a generic stereotype 
// often used for utility or I/O classes like this one.
import org.springframework.stereotype.Component;

@Component
public class TrackingDataLoader {

    // LOGGING CONCEPT: one logger per class
    // `LoggerFactory.getLogger(TrackingDataLoader.class)` tags every log line with this class name,
    // so we can tell where a message came from. It is `static final` because it is shared by all instances.
    private static final Logger logger = LoggerFactory.getLogger(TrackingDataLoader.class);

    // JAVA CONCEPT: final dependency
    // The mapper is instantiated once and reused. ObjectMapper is thread-safe after configuration,
    // so keeping a single, final instance is a Java best practice for performance.
    private final ObjectMapper mapper;

    public TrackingDataLoader() {
        this.mapper = new ObjectMapper();
    }

    public List<FrameData> loadData(String filePath) {
        List<FrameData> frames = new ArrayList<>();
        
        // JAVA CONCEPT: Try-Catch Block (Checked Exceptions)
        // Reading files from a hard drive is risky. The file might be missing, locked, 
        // or corrupted. Java FORCES you to acknowledge this risk using a try-catch block 
        // to handle potential IOExceptions (Input/Output Exceptions).
        try {
            // creates an object in memory that points to the file located at the path passed in filePath
            File file = new File(filePath);
            
            // JACKSON CONCEPT: The Tree Model
            // mapper.readTree(file): It's the ObjectMapper method that opens the file, 
            // reads the raw text, and builds this tree structure in memory.
            JsonNode rootNode = mapper.readTree(file);

            // Navigating the tree: We ask the root object for the key named "frames"
            JsonNode framesNode = rootNode.get("frames");

            // Checks if the node exists and is a list (JSON Array `[ ]`)
            if (framesNode != null && framesNode.isArray()) {
                
                // JAVA CONCEPT: Enhanced For-Loop over an Iterable
                // JsonNode safely allows us to iterate over its elements if it's an array.
                for (JsonNode frameNode : framesNode) {
                    
                    // DATA EXTRACTION & CASTING
                    // .get() gets the node, .asInt() safely converts the JSON number to a Java int.
                    int frameId = frameNode.get("frame_id").asInt();
                    double timestamp = frameNode.get("timestamp").asDouble();

                    FrameData currentFrame = new FrameData(frameId, timestamp);

                    // LOGGING CONCEPT: debug level
                    // One line per frame is far too noisy for a real match (thousands of frames),
                    // so it is logged at DEBUG, which is hidden by default. The `{}` placeholders
                    // are only filled in if DEBUG is actually enabled.
                    logger.debug("Processing Frame ID {} | Time: {}s", frameId, timestamp);

                    JsonNode entitiesNode = frameNode.get("entities");

                    if (entitiesNode != null && entitiesNode.isArray()) {
                        for (JsonNode entityNode : entitiesNode) {
                            
                            // Extracting common Entity fields
                            String type = entityNode.get("type").asText();
                            int id = entityNode.get("id").asInt();
                            double x = entityNode.get("x").asDouble();
                            double y = entityNode.get("y").asDouble();
                        
                            // JAVA CONCEPT: Switch Statement (String matching)
                            // A cleaner alternative to a long chain of if-else blocks.
                            // Here, the "Factory" pattern is informally used: we decide WHICH 
                            // specific subclass to instantiate based on the "type" string in the JSON.
                            switch (type) {
                                case "player":
                                    int teamId = entityNode.get("team_id").asInt();
                                    Player player = new Player(id, x, y, teamId);
                                    currentFrame.addEntity(player);
                                    break;
                                case "ball":
                                    Ball ball = new Ball(id, x, y);
                                    currentFrame.addEntity(ball);
                                    break;
                                case "referee":
                                    Referee referee = new Referee(id, x, y);
                                    currentFrame.addEntity(referee);
                                    break;
                                default:
                                    logger.warn("Ignoring unknown entity type: {}", type);
                                    break;
                            }
                        }
                    }
                    frames.add(currentFrame);
                }
            }

        } catch (IOException e) {
            // ERROR HANDLING: never swallow an exception.
            // Returning an empty list here would make "the file failed to load" look exactly like
            // "the match had no frames", and callers would print a report full of legitimate-looking zeros.
            // Instead we (1) log the error together with the exception, so the stack trace goes to the log,
            // and (2) rethrow it as our own unchecked exception, with the file path in the message.
            // Malformed JSON also ends up here: Jackson's JsonProcessingException is a subclass of IOException.
            logger.error("Failed to load tracking data from '{}'", filePath, e);
            throw new TrackingDataLoadException("Failed to load tracking data from: " + filePath, e);
        }
        return frames;
    }

    // Convenience method using method overloading
    public List<FrameData> loadSampleData() {
        return loadData("src/main/resources/tracking_sample.json");
    }
}