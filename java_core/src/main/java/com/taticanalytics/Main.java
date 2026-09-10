package com.taticanalytics;

import com.taticanalytics.service.TrackingDataLoader;

public class Main {
    public static void main(String[] args) {
        TrackingDataLoader loader = new TrackingDataLoader();

        loader.loadData("src/main/resources/tracking_sample.json");
    }
}