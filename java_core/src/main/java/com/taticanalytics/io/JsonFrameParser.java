package com.taticanalytics.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;

import com.taticanalytics.model.FrameData;
import com.taticanalytics.model.Entity;
import com.taticanalytics.model.Ball;
import com.taticanalytics.model.Player;
import com.taticanalytics.model.Referee;

import com.taticanalytics.io.dto.EntityDTO;
import com.taticanalytics.io.dto.FrameDataDTO;

import org.springframework.stereotype.Component;

@Component
public class JsonFrameParser {
    private final ObjectMapper objectMapper;

    public JsonFrameParser() {
        this.objectMapper = new ObjectMapper();
    }

    public List<FrameData> parseJsonString(String jsonContent) throws IOException {
        List<FrameDataDTO> dtos = objectMapper.readValue(jsonContent, new TypeReference<List<FrameDataDTO>>() {});
        return convertToDomain(dtos);
    }

    private List<FrameData> convertToDomain(List<FrameDataDTO> dtos) {
        List<FrameData> frames = new ArrayList<>();

        for (FrameDataDTO dto : dtos) {
            FrameData frame = new FrameData(dto.frameId(), dto.timestamp());

            if (dto.entities() != null) {

                for (EntityDTO entityDTO : dto.entities()) {
                    Entity entity = mapEntity(entityDTO);

                    if(entity != null) {
                        frame.addEntity(entity);
                    }
                }
            }
            frames.add(frame);
        }
        return frames;
    }

    private Entity mapEntity(EntityDTO dto) {
        if ("player".equalsIgnoreCase(dto.type())) {

            int team = (dto.teamId() != null) ? dto.teamId() : -1;
            return new Player(dto.id(), dto.x(), dto.y(), team);

        } else if ("ball".equalsIgnoreCase(dto.type())) {
            return new Ball(dto.id(), dto.x(), dto.y());

        } else if ("referee".equalsIgnoreCase(dto.type())) {
            return new Referee(dto.id(), dto.x(), dto.y());
        }

        return null;
    }
}
