package com.taticanalytics.io.dto;

// LIBRARY: 
// `java.util.List` is the standard interface for ordered collections (like arrays, 
// but dynamically resizable). It's the Java equivalent of a Python List.
import java.util.List;

// LIBRARY:
// Jackson library annotation used to map exact JSON keys to Java fields.
import com.fasterxml.jackson.annotation.JsonProperty;

// PURPOSE:
// This is the parent DTO (Data Transfer Object) for a single video frame.
// It directly maps to the JSON structure:
// {
//   "frame_id": 1,
//   "timestamp": 0.04,
//   "entities": [ ... ]
// }

// JAVA CONCEPT / SYNTAX: `record`
// Just like `EntityDTO`, this is an immutable data carrier. Java automatically 
// generates the constructor, getters (e.g., `frameId()`, `entities()`), and 
// `toString()` methods behind the scenes.
public record FrameDataDTO(
    
    // ANNOTATION: 
    // Maps the snake_case JSON key "frame_id" to the camelCase Java variable "frameId".
    @JsonProperty("frame_id") int frameId, 
    
    // Maps the JSON key "timestamp". (Since the names match exactly, the 
    // @JsonProperty annotation is technically optional here, but keeping it 
    // is a good practice for consistency and clarity).
    @JsonProperty("timestamp") double timestamp, 
    
    // DATA FLOW / NESTING:
    // This is the most powerful part of Jackson. When it sees the "entities" JSON array,
    // it looks at this `List<EntityDTO>` declaration. Jackson will automatically loop 
    // through the JSON array, instantiate an `EntityDTO` (the class we looked at previously) 
    // for each item, and pack them all into this Java List.
    @JsonProperty("entities") List<EntityDTO> entities
    
) {}