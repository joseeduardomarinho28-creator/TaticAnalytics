package com.taticanalytics.service;

import java.util.List;
import com.taticanalytics.model.FrameData;
import com.taticanalytics.model.Entity;

public class AnalyticsService {
    public double calculateTotalDistance(List<FrameData> frames, int entityId) {
        double totalDistance = 0.0;

        Entity previousEntity = null;

        for (FrameData frame : frames) {

            for (Entity entity : frame.getEntities()) {

                if (entityId == entity.getId()) {

                    if (previousEntity != null) {
                        double dx = entity.getX() - previousEntity.getX();
                        double dy = entity.getY() - previousEntity.getY();
                        double stepDistance = Math.sqrt(dx * dx + dy * dy);
                        totalDistance += stepDistance;
                    }
                    previousEntity = entity;
                }
            }
        }

        return totalDistance;
    }
}