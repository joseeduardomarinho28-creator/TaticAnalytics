package com.taticanalytics.io;

// SYNTAX: 
// `import static` means we are importing a specific static method from a class,
// rather than the class itself. This allows us to write `assertEquals(...)` 
// instead of the longer `Assertions.assertEquals(...)`.
import static org.junit.jupiter.api.Assertions.assertEquals;

// LIBRARY: 
// `org.junit.jupiter.api.Test` comes from JUnit 5, an external testing framework 
// (added via Maven dependencies). It is not part of the standard Java library.
import org.junit.jupiter.api.Test;

import java.util.List;
import com.taticanalytics.model.FrameData;

// NOTE: This import is duplicated. The code still compiles and runs perfectly fine 
// because Java just ignores duplicate imports, but it's good practice to clean them up.
import com.taticanalytics.model.FrameData;

// PURPOSE:
// This is a Unit Test class. Its sole responsibility is to verify that the 
// `JsonFrameParser` class works correctly under controlled conditions.
// The "Test" suffix in the class name is a standard Maven/JUnit convention.
public class JsonFrameParserTest {
    
    // ANNOTATION:
    // `@Test` tells the JUnit framework: "Treat this method as an automated test."
    // When you run `mvn test`, the test runner looks for all methods marked with this.
    @Test
    // SYNTAX: `throws Exception`
    // JAVA CONCEPT: Checked Exceptions. Unlike Python, Java forces you to declare 
    // if a method might encounter a serious error (like failing to parse JSON).
    // Because this is a test method, we just throw the exception up to the JUnit 
    // runner. If an exception happens, the test automatically fails, which is exactly what we want.
    public void shouldParseJsonStringSuccessfully() throws Exception {
        
        // SYNTAX: Text Blocks (""")
        // Introduced in recent Java versions, this works exactly like Python's 
        // multiline strings. It allows you to write JSON directly in the code 
        // without needing to escape every quotation mark (e.g., "\"frame_id\"").
        // This makes creating mock data for tests incredibly easy.
        String jsonContent = """
            [
              {
                "frame_id": 1,
                "timestamp": 0.04,
                "entities": [
                  { "id": 10, "type": "player", "x": 12.5, "y": 25.0, "teamId": 1 },
                  { "id": 0, "type": "ball", "x": 13.0, "y": 25.5 },
                  { "id": 99, "type": "referee", "x": 20.0, "y": 30.0 }
                ]
              }
            ]
            """;

            // EXECUTION FLOW:
            // 1. We instantiate the object we want to test (the parser).
            JsonFrameParser parser = new JsonFrameParser();

            // 2. We call the method we are testing, passing our mock JSON string.
            // Data flows from `jsonContent` -> into `parseJsonString` -> comes out as a Java List.
            List<FrameData> frames = parser.parseJsonString(jsonContent);

            // OOP / TESTING CONCEPT: Assertions
            // `assertEquals(expected, actual)` checks if the code did what it was supposed to do.
            // If `frames.size()` is not 1, JUnit will halt the test and mark it as FAILED.
            assertEquals(1, frames.size());

            // SYNTAX: `frames.get(0)`
            // In Python, you access list elements using brackets: `frames[0]`.
            // In Java, because `List` is an interface and `frames` is an object, 
            // you must use the `.get(index)` method to retrieve an element.
            FrameData firstFrame = frames.get(0);
            
            // Here, we verify that the nested JSON array ("entities") was also parsed
            // correctly and converted into 3 Java objects within the FrameData.
            assertEquals(3, firstFrame.getEntities().size());
    }
}