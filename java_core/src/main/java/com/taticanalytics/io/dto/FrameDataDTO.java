package com.taticanalytics.io.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record FrameDataDTO(@JsonProperty("frame_id") int frameId, @JsonProperty("timestamp") double timestamp, @JsonProperty("entities") List<EntityDTO> entities) {}