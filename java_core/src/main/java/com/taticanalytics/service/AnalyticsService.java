package com.taticanalytics.service;

import java.util.List;
import com.taticanalytics.model.FrameData;
import com.taticanalytics.model.Entity;
import com.taticanalytics.model.PlayerStats;

public class AnalyticsService {
    public PlayerStats calculatePlayerStats(List<FrameData> frames, int entityId) {
        double totalDistance = 0.0;
        Entity previousEntity = null;
        int previousFrameId = -1;
        double previousTimestamp = 0.0;
        double maxSpeed = 0.0;
        double deltaTime = 0.0;
        double currentSpeed = 0.0;

        for (FrameData frame : frames) {

            for (Entity entity : frame.getEntities()) {

                if (entityId == entity.getId()) {

                    if ((previousEntity != null) && (frame.getFrameId() - previousFrameId) == 1) {
                        double dx = entity.getX() - previousEntity.getX();
                        double dy = entity.getY() - previousEntity.getY();
                        double stepDistance = Math.sqrt(dx * dx + dy * dy);
                        totalDistance += stepDistance;
                        deltaTime = frame.getTimestamp() - previousTimestamp;

                        if (deltaTime > 0.0) {
                            currentSpeed = stepDistance / deltaTime;

                            if (currentSpeed > maxSpeed) {
                                maxSpeed = currentSpeed;
                            }
                        }
                    }
                    previousEntity = entity;
                    previousFrameId = frame.getFrameId();
                    previousTimestamp = frame.getTimestamp();

                }
            }
        }

        return new PlayerStats(totalDistance, maxSpeed);
    }
}