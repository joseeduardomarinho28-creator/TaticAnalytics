package com.taticanalytics.io;

// LIBRARY:
// Jackson core tools for converting JSON strings into Java objects.
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.ArrayList;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

// Domain model imports (The "Smart" objects with behavior)
import com.taticanalytics.model.FrameData;
import com.taticanalytics.model.Entity;
import com.taticanalytics.model.Ball;
import com.taticanalytics.model.Player;
import com.taticanalytics.model.Referee;
import com.taticanalytics.validation.CoordinateSanitizer;
import com.taticanalytics.validation.FrameIntegrityChecker;

// DTO imports (The "Dumb" objects that just carry data)
import com.taticanalytics.io.dto.EntityDTO;
import com.taticanalytics.io.dto.FrameDataDTO;
import com.taticanalytics.io.dto.TrackingFileDTO;

// LIBRARY: Spring Framework
// `@Component` tells Spring: "Please create and manage a single instance of this class (a Singleton).
// Whenever another class needs a JsonFrameParser, inject this instance automatically."
import org.springframework.stereotype.Component;

// LIBRARY: Spring Framework (TA-70)
// `ClassPathResource` is Spring's way of pointing at a file that lives on the classpath
// (for example inside `src/main/resources`, or inside the packaged .jar).
import org.springframework.core.io.ClassPathResource;

// PURPOSE:
// This class acts as the bridge between the raw JSON file and the core Application Domain.
// It performs two distinct steps:
// 1. Deserialization: JSON String -> DTOs (using Jackson)
// 2. Mapping: DTOs -> Domain Models (using custom Java logic)
@Component
public class JsonFrameParser {

    // CLASSPATH CONCEPT (TA-70): the name of the bundled sample, RELATIVE TO `src/main/resources`.
    // Maven copies that folder to the root of the classpath, so there is no "src/main/resources/" prefix.
    private static final String SAMPLE_RESOURCE = "tracking_sample.json";

    // The Jackson engine that does the heavy lifting of reading JSON.

    private final ObjectMapper objectMapper;

    public JsonFrameParser() {
        this.objectMapper = new ObjectMapper();
    }

    // PURPOSE:
    // Convenience method used by the controller layer. It reads the sample tracking
    // file straight from the classpath and hands it off to `parseJsonString`, so
    // callers don't need to deal with file paths themselves.
    // TA-70: it used to read the relative path "src/main/resources/tracking_sample.json", which only
    // worked when the process was started from the module root. Now it goes through the classpath,
    // so it also works when the app is packaged as a .jar and run from any directory.
    public List<FrameData> loadSampleData() throws IOException {
        return loadFromClasspath(SAMPLE_RESOURCE);
    }

    // PURPOSE (TA-70):
    // Reads a resource that is bundled inside the application and parses it.
    //
    // SPRING CONCEPT: `ClassPathResource`
    // This class is a Spring bean, so the idiomatic way to reach a bundled file is Spring's
    // `ClassPathResource` (the plain-Java equivalent is `getClassLoader().getResourceAsStream(...)`,
    // used by `TrackingDataLoader`, which also runs outside Spring).
    //
    // We check `exists()` first so that a wrong name fails with a message that says exactly what is
    // missing. It is a `FileNotFoundException` (a subclass of `IOException`), so callers and the
    // `GlobalExceptionHandler` keep dealing with the same exception type as before.
    //
    // LEARNING NOTE: reading with `readAllBytes()` + `StandardCharsets.UTF_8` is the stream version of
    // `Files.readString(...)`, which also decoded the file as UTF-8. The try-with-resources block
    // closes the stream automatically, even if reading fails.
    public List<FrameData> loadFromClasspath(String resourceName) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourceName);

        if (!resource.exists()) {
            throw new FileNotFoundException("Tracking resource not found on the classpath: " + resourceName);
        }

        try (InputStream inputStream = resource.getInputStream()) {
            String jsonContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return parseJsonString(jsonContent);
        }
    }

    // FIX (TA-106): The tracking file's root is a JSON OBJECT — { "match_info": {...}, "frames": [...] } —
    // per the v1 data contract (TA-50 / TA-61), not a bare array. We now deserialize into
    // `TrackingFileDTO` (which mirrors that object shape) and pull `frames` out of it, instead of
    // trying to read the whole content straight into a `List<FrameDataDTO>`. That old approach only
    // worked for a root-level array, which the contract explicitly does NOT define — it's what made
    // every real tracking file (including tracking_sample.json) blow up with a Jackson
    // MismatchedInputException, turned into an HTTP 500 by GlobalExceptionHandler.
    //
    // NOTE: We intentionally do NOT also accept a root-level array as a fallback. The whole point of
    // having a contract is that there is exactly one accepted shape (see TA-80 for unifying this
    // with TrackingDataLoader later).
    public List<FrameData> parseJsonString(String jsonContent) throws IOException {

        TrackingFileDTO trackingFile = objectMapper.readValue(jsonContent, TrackingFileDTO.class);

        List<FrameDataDTO> dtos = trackingFile.frames();

        // Defensive check: fail loudly with a clear message instead of letting a
        // NullPointerException surface later (e.g. inside convertToDomain's for-loop) as an opaque,
        // hard-to-debug 500. A tracking file with no "frames" key (or "frames": null) has nothing to
        // analyze, which is a data problem worth calling out explicitly.
        if (dtos == null) {
            throw new IOException(
                "Tracking file is missing the required \"frames\" array. " +
                "Expected root shape: { \"match_info\": {...}, \"frames\": [ ... ] }."
            );
        }

        // Once Jackson gives us the "dumb" DTOs, we immediately convert them into our "smart" Domain models.
        return convertToDomain(dtos);
    }

    // OOP CONCEPT: Private Helper Method
    // This method is hidden from the outside world. Other classes only care that they give this
    // parser a String and get back a List of Domain models.
    private List<FrameData> convertToDomain(List<FrameDataDTO> dtos) throws IOException {
        List<FrameData> frames = new ArrayList<>();

        // TA-76: one sanitizer PER CALL. It applies the coordinate policy (keep, clamp or discard) to every
        // entity and counts what it did. It is a local variable, and not a field, because this class is a
        // Spring singleton and the counters must start from zero on every parse.
        CoordinateSanitizer sanitizer = new CoordinateSanitizer();

        // TA-76: same idea for the structure of each frame (valid team_id, at most one ball, unique ids).
        FrameIntegrityChecker checker = new FrameIntegrityChecker();

        // Loop through each frame DTO
        for (FrameDataDTO dto : dtos) {

            // SYNTAX: Record Accessors
            // Notice it's `dto.frameId()` and `dto.timestamp()`, NOT `dto.getFrameId()`.
            // This is because FrameDataDTO is a Java `record`.
            FrameData frame = new FrameData(dto.frameId(), dto.timestamp());

            // TA-76: the "ids seen" and "ball seen" memory of the checker is per frame.
            checker.startFrame();

            // Defensive programming: Make sure the entities list actually exists in the JSON
            if (dto.entities() != null) {

                // Position of the entity inside the frame (1, 2, 3...), only used to build a clear error message.
                int entityNumber = 0;

                // Loop through each entity DTO inside this frame
                for (EntityDTO entityDTO : dto.entities()) {
                    entityNumber++;

                    // Call the factory method to figure out if it's a Player, Ball, or Referee
                    Entity entity = mapEntity(entityDTO, sanitizer, dto.frameId(), entityNumber);

                    // Only add it if we successfully mapped it (unknown types and discarded positions give null)
                    // AND the structure checks accept it (valid team_id, at most one ball, unique id).
                    if (entity != null && checker.accept(dto.frameId(), entity)) {
                        frame.addEntity(entity);
                    }
                }
            }
            frames.add(frame);
        }

        // TA-76: one summary line per parse (and an ALERT when more than 5% of the readings needed fixing).
        sanitizer.logSummary();
        checker.logSummary();
        return frames;
    }

    // PURPOSE (TA-76):
    // Fails with a clear message when a REQUIRED field is `null`, meaning it was missing from the JSON.
    // It throws `IOException`, like the "missing frames" check in `parseJsonString`, so callers keep dealing
    // with the same exception type.
    private void requireField(Object value, String field, int frameId, int entityNumber) throws IOException {
        if (value == null) {
            throw new IOException("Entity " + entityNumber + " of frame " + frameId
                + " is missing the required field \"" + field + "\"");
        }
    }

    // OOP CONCEPT: Simple Factory Pattern
    // This method takes raw data and decides WHICH specific subclass (Player, Ball, Referee) to instantiate.
    private Entity mapEntity(EntityDTO dto, CoordinateSanitizer sanitizer, int frameId, int entityNumber) throws IOException {

        // TA-76: REQUIRED fields. In `EntityDTO` these are wrapper types, so a field missing from the JSON
        // arrives here as `null` (a primitive would have silently become 0). We reject the file with a clear
        // message instead of guessing a value.
        requireField(dto.type(), "type", frameId, entityNumber);
        requireField(dto.id(), "id", frameId, entityNumber);
        requireField(dto.x(), "x", frameId, entityNumber);
        requireField(dto.y(), "y", frameId, entityNumber);
        if ("player".equalsIgnoreCase(dto.type())) {
            requireField(dto.teamId(), "team_id", frameId, entityNumber);
        }

        // TA-76: validate the position first. An empty Optional means "too far outside the pitch":
        // we return null, which `convertToDomain` already treats as "do not add this entity".
        Optional<CoordinateSanitizer.Position> position = sanitizer.sanitize(dto.id(), dto.x(), dto.y());
        if (position.isEmpty()) {
            return null;
        }
        double x = position.get().x();
        double y = position.get().y();

        // SYNTAX: Safe String Comparison
        // `"player".equalsIgnoreCase(dto.type())` is safer than `dto.type().equalsIgnoreCase("player")`.
        // If `dto.type()` is null, the first one just returns false. The second one throws a NullPointerException and crashes.
        if ("player".equalsIgnoreCase(dto.type())) {

            // SYNTAX: Ternary Operator (`condition ? if_true : if_false`)
            // TA-76: `teamId` is an `Integer` (wrapper class), but for a player it was already checked as
            // required above, so it is safe to unbox it into an `int` here. (Before, a missing team_id became -1.)
            // Whether the value is a VALID team (1 or 2) is checked later, by the FrameIntegrityChecker.
            int team = dto.teamId();

            // Create and return the Domain Model
            return new Player(dto.id(), x, y, team);

        } else if ("ball".equalsIgnoreCase(dto.type())) {
            return new Ball(dto.id(), x, y);

        } else if ("referee".equalsIgnoreCase(dto.type())) {
            return new Referee(dto.id(), x, y);
        }

        // If the JSON had an entity type like "coach", we return null because we don't care about it.
        return null;
    }
}
