package com.taticanalytics.io.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

// PURPOSE (TA-106):
// Represents the ROOT of a tracking JSON file, exactly as defined by the v1 data contract
// agreed with the CV team (see TA-50 / TA-61): an object, not an array —
//   { "match_info": {...}, "frames": [ {...}, {...}, ... ] }
//
// Before this DTO existed, `JsonFrameParser` tried to deserialize the raw JSON straight into a
// `List<FrameDataDTO>`, which only works if the root of the file is itself a JSON array. It
// isn't — the array lives under the "frames" key. Any file that actually followed the contract
// (including tracking_sample.json) made Jackson throw a MismatchedInputException, which
// GlobalExceptionHandler turned into an HTTP 500 for all four analytics endpoints.
public record TrackingFileDTO(

        // `match_info` is metadata about the source video (resolution, frame rate, file name).
        // It has no effect on any calculation today, so we keep it as a raw JsonNode instead of
        // modeling every field. That also makes it optional: if it's missing from the JSON,
        // `matchInfo` is simply null and nothing else breaks.
        @JsonProperty("match_info") JsonNode matchInfo,

        // The actual frame-by-frame tracking data. Required: see JsonFrameParser.parseJsonString
        // for the explicit, readable error thrown when this is missing or null.
        @JsonProperty("frames") List<FrameDataDTO> frames
) {
}
