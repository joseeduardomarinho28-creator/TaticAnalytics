package com.taticanalytics;

import com.taticanalytics.service.TrackingDataLoader;
import java.util.List;
import com.taticanalytics.model.FrameData;
import com.taticanalytics.model.Entity;

public class Main {
    public static void main(String[] args) {
        TrackingDataLoader loader = new TrackingDataLoader();

        List<FrameData> frames = loader.loadData("src/main/resources/tracking_sample.json");

        System.out.println("Frames : " + frames.size());

        loader.loadData("src/main/resources/tracking_sample.json");
    }
}