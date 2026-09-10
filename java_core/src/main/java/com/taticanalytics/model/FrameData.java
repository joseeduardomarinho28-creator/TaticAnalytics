package com.taticanalytics.model;

import java.util.List;
import java.util.ArrayList;

public class FrameData {
    private int frameId;
    private double timestamp;
    private List<Entity> entities;

    public FrameData(int frameId, double timestamp) {
        this.frameId = frameId;
        this.timestamp = timestamp;
        this.entities = new ArrayList<>();
    }

    public void addEntity(Entity entity) {
        this.entities.add(entity);
    }

    public int getFrameId() {
        return frameId;
    }

    public double getTimestamp() {
        return timestamp;
    }

    public List<Entity> getEntities() {
        return entities;
    }
}