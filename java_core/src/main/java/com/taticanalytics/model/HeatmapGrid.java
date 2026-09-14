package com.taticanalytics.model;

public class HeatmapGrid {
    private int rows;
    private int cols;
    private double fieldWidth;
    private double fieldHeight;
    private double[][] grid;

    public HeatmapGrid(int rows, int cols, double fieldWidth, double fieldHeight) {
        this.grid = new double[rows][cols];
        this.rows = rows;
        this.cols = cols;
        this.fieldWidth = fieldWidth;
        this.fieldHeight = fieldHeight;
    }

    public void addTime (double x, double y, double deltaTime) {
        double cellWidth = fieldWidth / cols;
        double cellHeight = fieldHeight / rows;

        int colInd = (int) (x / cellWidth);
        int rowInd = (int) (y / cellHeight);

        if (rowInd >= 0 && rowInd < rows && colInd >= 0 && colInd < cols) {
            grid[rowInd][colInd] += deltaTime;
        }
    }

    public double[][] getGrid() {
        return grid;
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }
}
