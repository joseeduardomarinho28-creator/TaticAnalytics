package com.taticanalytics.io.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EntityDTO(@JsonProperty("type") String type, @JsonProperty("id") int id, @JsonProperty("x") double x, @JsonProperty("y") double y, @JsonProperty("teamId") Integer teamId) {}
