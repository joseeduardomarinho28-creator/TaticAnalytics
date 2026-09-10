package com.taticanalytics.service;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;

import com.fasterxml.jackson.databind.JsonNode;

public class TrackingDataLoader {

    private final ObjectMapper mapper;

    public TrackingDataLoader() {
        this.mapper = new ObjectMapper();
    }

    public void loadData(String filePath) {
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
                                    System.out.println(" -> Player ID " + id + " in X: " + x + " / Y: " + y);
                                    break;
                                case "ball":
                                    System.out.println(" -> Ball ID " + id + " in X: " + x + " / Y: " + y);
                                    break;
                                case "referee":
                                    System.out.println(" -> Referee ID " + id + " in X: " + x + " / Y: " + y);
                                    break;
                                default:
                                    System.out.println(" -> Unknow: " + type);
                                    break;  

                            }
                        }
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}