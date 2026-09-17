package com.taticanalytics.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;

import java.util.Map;
import java.util.List;


public class ExportService {
    private final ObjectMapper objectMapper;

    public ExportService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public <T> void exportToJson(T data, String filePath) throws IOException {
        File file = new File(filePath);

        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        
        objectMapper.writeValue(file, data);
    }

    public void exportPlayerStatsToCsv(List<Map<String, Object>> statsList, String filePath) throws IOException {
        File file = new File(filePath);
        
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("playerId,teamId,totalDistance,maxSpeed");

            for (Map<String, Object> stat : statsList) {
                writer.printf("%s,%s,%s,%s%n",
                stat.get("playerId"),
                stat.get("teamId"),
                stat.get("totalDistance"),
                stat.get("maxSpeed"));
            }
        }
    }

    public void exportHeatmapToCsv(double[][] grid, String filePath) throws IOException {
        File file = new File(filePath);

        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            for (double[] row : grid) {
                StringBuilder sb = new StringBuilder();

                for (int i = 0; i < row.length; i++) {
                    sb.append(row[i]);

                    if (i < row.length - 1) {
                        sb.append(",");
                    }
                }
                writer.println(sb.toString());
            }
        }
    }
}
