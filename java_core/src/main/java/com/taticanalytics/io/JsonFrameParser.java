package com.taticanalytics.io;

// LIBRARY:
// Jackson core tools for converting JSON strings into Java objects.
import com.fasterxml.jackson.databind.ObjectMapper;
// LEARNING NOTE: TypeReference is specifically used to solve a Java limitation called "Type Erasure".
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.io.InputStream;

// Domain model imports (The "Smart" objects with behavior)
import com.taticanalytics.model.FrameData;
import com.taticanalytics.model.Entity;
import com.taticanalytics.model.Ball;
import com.taticanalytics.model.Player;
import com.taticanalytics.model.Referee;

// DTO imports (The "Dumb" objects that just carry data)
import com.taticanalytics.io.dto.EntityDTO;
import com.taticanalytics.io.dto.FrameDataDTO;

// LIBRARY: Spring Framework
// `@Component` tells Spring: "Please create and manage a single instance of this class (a Singleton). 
// Whenever another class needs a JsonFrameParser, inject this instance automatically."
import org.springframework.stereotype.Component;

// PURPOSE:
// This class acts as the bridge between the raw JSON file and the core Application Domain.
// It performs two distinct steps:
// 1. Deserialization: JSON String -> DTOs (using Jackson)
// 2. Mapping: DTOs -> Domain Models (using custom Java logic)
@Component
public class JsonFrameParser {
    
    // The Jackson engine that does the heavy lifting of reading JSON.
    private final ObjectMapper objectMapper;

    public JsonFrameParser() {
        this.objectMapper = new ObjectMapper();
    }

    public List<FrameData> parseJsonString(String jsonContent) throws IOException {
        
        // SYNTAX / JAVA CONCEPT: Type Erasure & TypeReference
        // In Java, at runtime, a `List<FrameDataDTO>` forgets what it holds and just becomes a `List`.
        // If we just told Jackson to return a `List.class`, it wouldn't know what to put inside it, 
        // and would default to returning a List of standard Maps/Dictionaries.
        // `new TypeReference<List<FrameDataDTO>>() {}` is a clever workaround that forces Java to 
        // remember the exact nested type so Jackson can build the correct DTOs.
        List<FrameDataDTO> dtos = objectMapper.readValue(jsonContent, new TypeReference<List<FrameDataDTO>>() {});
        
        // Once Jackson gives us the "dumb" DTOs, we immediately convert them into our "smart" Domain models.
        return convertToDomain(dtos);
    }

    // IO CONCEPT: Classpath Resource Loading
    // Convenience public method to load default/sample tracking data bundled in src/main/resources.
    public List<FrameData> loadSampleData() {
        // SYNTAX: Try-with-resources
        // Ensures the InputStream is automatically closed after reading, preventing resource leaks.
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("tracking_sample.json")) {
            if (is == null) {
                throw new RuntimeException("Sample tracking data file 'tracking_sample.json' not found in classpath!");
            }
            
            List<FrameDataDTO> dtos = objectMapper.readValue(
                is, 
                new TypeReference<List<FrameDataDTO>>() {}
            );
            
            return convertToDomain(dtos);
        } catch (IOException e) {
            // EXCEPTION TRANSLATION: Wrapping low-level checked IOException into unchecked RuntimeException 
            // to keep the service/controller signatures clean and consistent with domain expectations.
            throw new RuntimeException("Failed to load sample tracking data from file", e);
        }
    }

    // OOP CONCEPT: Private Helper Method
    // This method is hidden from the outside world. Other classes only care that they give this 
    // parser a String and get back a List of Domain models.
    private List<FrameData> convertToDomain(List<FrameDataDTO> dtos) {
        List<FrameData> frames = new ArrayList<>();

        // Loop through each frame DTO
        for (FrameDataDTO dto : dtos) {
            
            // SYNTAX: Record Accessors
            // Notice it's `dto.frameId()` and `dto.timestamp()`, NOT `dto.getFrameId()`. 
            // This is because FrameDataDTO is a Java `record`.
            FrameData frame = new FrameData(dto.frameId(), dto.timestamp());

            // Defensive programming: Make sure the entities list actually exists in the JSON
            if (dto.entities() != null) {

                // Loop through each entity DTO inside this frame
                for (EntityDTO entityDTO : dto.entities()) {
                    
                    // Call the factory method to figure out if it's a Player, Ball, or Referee
                    Entity entity = mapEntity(entityDTO);

                    // Only add it if we successfully mapped it (e.g., ignore unknown types)
                    if(entity != null) {
                        frame.addEntity(entity);
                    }
                }
            }
            frames.add(frame);
        }
        return frames;
    }

    // OOP CONCEPT: Simple Factory Pattern
    // This method takes raw data and decides WHICH specific subclass (Player, Ball, Referee) to instantiate.
    private Entity mapEntity(EntityDTO dto) {
        
        // SYNTAX: Safe String Comparison
        // `"player".equalsIgnoreCase(dto.type())` is safer than `dto.type().equalsIgnoreCase("player")`.
        // If `dto.type()` is null, the first one just returns false. The second one throws a NullPointerException and crashes.
        if ("player".equalsIgnoreCase(dto.type())) {

            // SYNTAX: Ternary Operator (`condition ? if_true : if_false`)
            // Remember that in our EntityDTO, `teamId` is an `Integer` (wrapper class), so it can be null.
            // If the JSON didn't have a team_id, we default it to -1 (or any invalid team ID).
            int team = (dto.teamId() != null) ? dto.teamId() : -1;
            
            // Create and return the Domain Model
            return new Player(dto.id(), dto.x(), dto.y(), team);

        } else if ("ball".equalsIgnoreCase(dto.type())) {
            return new Ball(dto.id(), dto.x(), dto.y());

        } else if ("referee".equalsIgnoreCase(dto.type())) {
            return new Referee(dto.id(), dto.x(), dto.y());
        }

        // If the JSON had an entity type like "coach", we return null because we don't care about it.
        return null;
    }
}