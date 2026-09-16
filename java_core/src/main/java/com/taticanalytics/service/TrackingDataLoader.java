package com.taticanalytics.service;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import com.fasterxml.jackson.databind.JsonNode;
import com.taticanalytics.model.FrameData;
import com.taticanalytics.model.Player;
import com.taticanalytics.model.Ball;
import com.taticanalytics.model.Referee;

import org.springframework.stereotype.Component;

@Component
public class TrackingDataLoader {

    private final ObjectMapper mapper;

    public TrackingDataLoader() {
        this.mapper = new ObjectMapper();
    }

    public List<FrameData> loadData(String filePath) {
        List<FrameData> frames = new ArrayList<>();
        try {
            // creates an object in memory that points to the file located at the path passed in filePath
            File file = new File(filePath);
            // mapper.readTree(file): It's the ObjectMapper method that opens the file, reads the raw text, and builds this tree structure in memory.
            JsonNode rootNode = mapper.readTree(file);

            JsonNode framesNode = rootNode.get("frames");

            // Checks if the node exists and is a list
            if (framesNode != null && framesNode.isArray()) {
                for (JsonNode frameNode : framesNode) {
                    int frameId = frameNode.get("frame_id").asInt();
                    double timestamp = frameNode.get("timestamp").asDouble();

                    FrameData currentFrame = new FrameData(frameId, timestamp);

                    System.out.println("Processing Frame ID " + frameId + " | Time: " + timestamp + "s");

                    JsonNode entitiesNode = frameNode.get("entities");

                    if (entitiesNode != null && entitiesNode.isArray()) {
                        for (JsonNode entityNode : entitiesNode) {
                            String type = entityNode.get("type").asText();
                            int id = entityNode.get("id").asInt();
                            double x = entityNode.get("x").asDouble();
                            double y = entityNode.get("y").asDouble();
                        
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
                                    System.out.println(" -> Unknow: " + type);
                                    break;  

                            }
                        }
                    }
                    frames.add(currentFrame);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return frames;
    }

    public List<FrameData> loadSampleData() {
    return loadData("src/main/resources/tracking_sample.json");
    }
}