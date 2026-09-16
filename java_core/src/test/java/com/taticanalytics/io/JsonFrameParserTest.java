package com.taticanalytics.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.List;
import com.taticanalytics.model.FrameData;

import com.taticanalytics.model.FrameData;

public class JsonFrameParserTest {
    @Test
    public void shouldParseJsonStringSuccessfully() throws Exception {
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

            JsonFrameParser parser = new JsonFrameParser();

            List<FrameData> frames = parser.parseJsonString(jsonContent);

            assertEquals(1, frames.size());

            FrameData firstFrame = frames.get(0);
            assertEquals(3, firstFrame.getEntities().size());
    }
}
