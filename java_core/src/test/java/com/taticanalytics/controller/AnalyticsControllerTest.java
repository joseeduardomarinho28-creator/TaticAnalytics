package com.taticanalytics.controller;

// LEARNING NOTE: `import static`
// A normal `import` brings in a CLASS. An `import static` brings in a single static METHOD
// of a class, so we can call it directly: `when(...)` instead of `Mockito.when(...)`.
// Python analogy: it works like `from module import function`.
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// LIBRARY: Java Collections Framework
// `List` is the interface (the "type" of the list) and `ArrayList` is the concrete implementation.
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// LIBRARY: JUnit 5 and Spring Boot Test
// `@Test` comes from JUnit (the same one used in `AnalyticsServiceTest`). The other imports come
// from `spring-boot-starter-test`, which is already declared in the `pom.xml` with test scope.
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

// Classes from our own project that this test uses.
import com.taticanalytics.io.JsonFrameParser;
import com.taticanalytics.model.FrameData;
import com.taticanalytics.model.HeatmapGrid;
import com.taticanalytics.model.PlayerStats;
import com.taticanalytics.service.AnalyticsService;
import com.taticanalytics.util.MatchConstants;

// PURPOSE:
// This class tests the WEB LAYER of the application: the `AnalyticsController` (routes, URL
// parameters, HTTP status codes and the JSON that goes back to the client).
// It does NOT test the business logic (distance, possession, heatmap). That is already covered by
// `AnalyticsServiceTest` and `AnalyticsServiceHeatmapTest`. Here the service is replaced by a fake.
//
// SPRING ANNOTATION: `@WebMvcTest`
// Normally, starting the application creates EVERYTHING (the server, every service, every parser).
// `@WebMvcTest` tells Spring: "For this test, start only the web layer": the controller we name,
// the `@ControllerAdvice` (`GlobalExceptionHandler`), `WebConfig`, and the machinery that converts
// URL parameters and Java objects to JSON. No real server and no file reading, so it runs fast.
//
// SYNTAX: `AnalyticsController.class`
// This is a reference to the class itself (not to an object). It means "the controller called
// AnalyticsController is the one under test".
@WebMvcTest(AnalyticsController.class)
public class AnalyticsControllerTest {

    // SPRING ANNOTATION: `@Autowired`
    // Tells Spring: "Fill this field with the matching object from your container of beans."
    // We never write `new MockMvc(...)`: `@WebMvcTest` creates one for us.
    //
    // LEARNING NOTE: `MockMvc`
    // It is a "fake browser". `mockMvc.perform(get("/some/route"))` sends an HTTP request straight
    // to the controller, with no network and no real server, and lets us inspect the response.
    @Autowired
    private MockMvc mockMvc;

    // SPRING CONCEPT: Beans and `@MockBean`
    // A "bean" is an object created and managed by Spring (like `AnalyticsService`, marked with
    // `@Service`). The `AnalyticsController` receives its dependencies through its constructor,
    // and Spring fetches them from its container.
    // But `@WebMvcTest` loads ONLY the web layer, so the real `AnalyticsService` and
    // `JsonFrameParser` are NOT in the container, and the controller could not be created.
    // `@MockBean` solves this: it creates a MOCK (a stand-in object with the same methods as the
    // real class, but that does nothing real) and puts it in the container in place of the real one.
    //
    // Why mock them? Isolation (if this test fails, the problem is in the controller, not in the
    // math), no dependency on `tracking_sample.json`, and control (we choose what they answer).
    @MockBean
    private AnalyticsService analyticsService;

    @MockBean
    private JsonFrameParser frameParser;

    // ANNOTATION: `@Test`
    // Marks the method as a test case. Without it, JUnit would ignore the method.
    //
    // SYNTAX: `throws Exception`
    // `frameParser.loadSampleData()` and `mockMvc.perform(...)` declare that they may throw
    // checked exceptions. Instead of catching them, the test just passes them up: if one is
    // thrown, the test fails.
    @Test
    public void shouldReturnPlayerStatsAsJson() throws Exception {

        // TESTING: "Arrange, Act, Assert" Pattern (AAA)
        // STEP 1: ARRANGE - Teach the mocks what to answer.
        //
        // The list can be empty: the service is fake and will not calculate anything with it.
        // We reuse the same variable below so it is clear that it is the same list on both sides.
        List<FrameData> frames = new ArrayList<>();

        // LEARNING NOTE: `when(...).thenReturn(...)`
        // Read it as a sentence: "WHEN this method is called on the mock, THEN return this value."
        // The controller always calls the parser first, so we teach the fake parser to answer.
        when(frameParser.loadSampleData()).thenReturn(frames);

        // Same idea for the service: "when it is called with these frames and player id 7, return
        // stats with a total distance of 12.5 and a max speed of 5.0".
        when(analyticsService.calculatePlayerStats(frames, 7)).thenReturn(new PlayerStats(12.5, 5.0));

        // STEP 2: ACT - Send the fake HTTP request.
        // The path is the junction of two annotations in the controller:
        // `@RequestMapping("/api/v1/analytics")` on the class + `@GetMapping("/player/{id}/stats")`
        // on the method, where `{id}` becomes 7.
        mockMvc.perform(get("/api/v1/analytics/player/7/stats"))

                // STEP 3: ASSERT - Verify the response.
                // SYNTAX: Method chaining
                // Each `.andExpect(...)` is one check, and they are chained with dots (the line
                // breaks are only formatting). If any check fails, the test fails and the message
                // says which one.
                //
                // `status().isOk()` checks that the HTTP status code is 200.
                .andExpect(status().isOk())

                // LEARNING NOTE: `jsonPath`
                // `$` is the root of the JSON, and `.totalDistance` is a field inside it.
                // The field names come from the getters of `PlayerStats`: Jackson removes the
                // `get` and lowercases the first letter (`getTotalDistance()` -> `totalDistance`).
                .andExpect(jsonPath("$.totalDistance").value(12.5))
                .andExpect(jsonPath("$.maxSpeed").value(5.0))

                // 5.0 m/s * 3.6 = 18.0 km/h (the conversion done by `getMaxSpeedKmh()`).
                .andExpect(jsonPath("$.maxSpeedKmh").value(18.0));
    }

    // TESTING: Error path (unhappy path)
    // The first test covered the "everything goes well" scenario. A good test suite also checks
    // what happens when the client sends something invalid. Here the id in the URL is "abc"
    // instead of a number, but the controller expects an `int`.
    //
    // What happens: Spring tries to convert "abc" to `int`, fails and throws a
    // `MethodArgumentTypeMismatchException` BEFORE the controller method even runs. The
    // `GlobalExceptionHandler` (a `@ControllerAdvice`, loaded by `@WebMvcTest`) catches it and
    // builds the 400 JSON. So this test checks the controller and the exception handler together.
    @Test
    public void shouldReturnBadRequestWhenPlayerIdIsNotANumber() throws Exception {

        // No ARRANGE step here: the request is rejected before the controller calls the parser or
        // the service, so there is nothing to teach the mocks with `when(...)`.

        // ACT: Send the invalid request ("abc" where a number is expected).
        mockMvc.perform(get("/api/v1/analytics/player/abc/stats"))

                // ASSERT: `isBadRequest()` checks that the HTTP status code is 400.
                .andExpect(status().isBadRequest())

                // The JSON body is built by `handleTypeMismatch` in `GlobalExceptionHandler`.
                // We check the three fixed fields: the numeric status, the error title and the
                // message that names the wrong parameter and the expected type.
                // LEARNING NOTE: We do NOT check `$.timestamp`, because it changes at every run
                // (`LocalDateTime.now()`) and the test would fail randomly.
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Invalid Parameter Type"))
                .andExpect(jsonPath("$.message").value("Parameter 'id' should be of type int"));

        // LEARNING NOTE: `verify(mock, never())`
        // `when(...)` teaches a mock what to answer; `verify(...)` asks it afterwards: "were you
        // called?". With `never()` we assert that the service was NOT called, which proves that
        // the invalid request was blocked before reaching the business logic.
        //
        // `any()` and `anyInt()` are wildcards ("any list", "any int"). We use them because we do
        // not care WHICH arguments would have been used, only that there was no call at all.
        // MOCKITO RULE: if one argument is a wildcard, all of them must be (no mixing with `7`).
        verify(analyticsService, never()).calculatePlayerStats(any(), anyInt());
    }

    // =====================================================================
    // POSSESSION PER TEAM (`/possession/teams`)
    // =====================================================================

    // TESTING: Testing a branch of the controller
    // `getPossessionPerTeam` has an `if (radius != null)`. That means TWO paths exist, and each one
    // needs its own test. This is the path where the client sends NO radius.
    //
    // Expected behavior: the controller calls the overloaded service method WITHOUT a radius
    // (`calculatePossessionTimePerTeam(frames)`), so the service uses its default constant.
    @Test
    public void shouldUseDefaultRadiusWhenRadiusIsNotProvidedForTeams() throws Exception {

        // ARRANGE: Teach the mocks. Team 10 held the ball for 2.0s and team 20 for 3.0s.
        List<FrameData> frames = new ArrayList<>();
        when(frameParser.loadSampleData()).thenReturn(frames);
        when(analyticsService.calculatePossessionTimePerTeam(frames))
                .thenReturn(Map.of(10, 2.0, 20, 3.0));

        // ACT + ASSERT: No `?radius=...` in the URL.
        // LEARNING NOTE: Map keys become JSON TEXT
        // A Java `Map<Integer, Double>` is serialized as `{"10": 2.0, "20": 3.0}`: JSON keys are
        // always strings. That is why we use the bracket notation `$['10']` instead of `$.10`.
        mockMvc.perform(get("/api/v1/analytics/possession/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['10']").value(2.0))
                .andExpect(jsonPath("$['20']").value(3.0));

        // VERIFY: The one-argument overload was used, and the two-argument one was NOT.
        // `anyDouble()` is the wildcard for a `double` argument (like `anyInt()` for an `int`).
        verify(analyticsService).calculatePossessionTimePerTeam(frames);
        verify(analyticsService, never()).calculatePossessionTimePerTeam(any(), anyDouble());
    }

    // This is the other path: the client sends `?radius=3.5`, so the controller must forward that
    // value to the two-argument method and must NOT use the default overload.
    @Test
    public void shouldUseCustomRadiusWhenRadiusIsProvidedForTeams() throws Exception {

        // ARRANGE: Here we stub the TWO-argument method (frames + radius 3.5).
        // With a bigger radius, team 10 would get more possession time (4.0s).
        List<FrameData> frames = new ArrayList<>();
        when(frameParser.loadSampleData()).thenReturn(frames);
        when(analyticsService.calculatePossessionTimePerTeam(frames, 3.5))
                .thenReturn(Map.of(10, 4.0));

        // ACT + ASSERT: The query string is written directly in the URL.
        // Spring converts the text "3.5" into the `Double radius` parameter of the controller.
        mockMvc.perform(get("/api/v1/analytics/possession/teams?radius=3.5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['10']").value(4.0));

        // VERIFY: Mirror of the previous test. The two-argument method received exactly
        // (frames, 3.5), and the one-argument overload was never called.
        verify(analyticsService).calculatePossessionTimePerTeam(frames, 3.5);
        verify(analyticsService, never()).calculatePossessionTimePerTeam(any());
    }

    // =====================================================================
    // POSSESSION PER PLAYER (`/possession/players`)
    // =====================================================================

    // Same two paths as the teams endpoint, now for `getPossessionPerPlayer`.
    // The keys of the JSON are player ids instead of team ids.
    @Test
    public void shouldUseDefaultRadiusWhenRadiusIsNotProvidedForPlayers() throws Exception {

        // ARRANGE: Player 1 held the ball for 4.5s and player 2 for 1.5s.
        List<FrameData> frames = new ArrayList<>();
        when(frameParser.loadSampleData()).thenReturn(frames);
        when(analyticsService.calculatePossessionTimePerPlayer(frames))
                .thenReturn(Map.of(1, 4.5, 2, 1.5));

        // ACT + ASSERT
        mockMvc.perform(get("/api/v1/analytics/possession/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['1']").value(4.5))
                .andExpect(jsonPath("$['2']").value(1.5));

        // VERIFY: Default overload used, custom-radius method untouched.
        verify(analyticsService).calculatePossessionTimePerPlayer(frames);
        verify(analyticsService, never()).calculatePossessionTimePerPlayer(any(), anyDouble());
    }

    @Test
    public void shouldUseCustomRadiusWhenRadiusIsProvidedForPlayers() throws Exception {

        // ARRANGE: Stub the two-argument method with radius 3.5.
        List<FrameData> frames = new ArrayList<>();
        when(frameParser.loadSampleData()).thenReturn(frames);
        when(analyticsService.calculatePossessionTimePerPlayer(frames, 3.5))
                .thenReturn(Map.of(1, 6.0));

        // ACT + ASSERT
        mockMvc.perform(get("/api/v1/analytics/possession/players?radius=3.5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['1']").value(6.0));

        // VERIFY: Custom radius forwarded, default overload never called.
        verify(analyticsService).calculatePossessionTimePerPlayer(frames, 3.5);
        verify(analyticsService, never()).calculatePossessionTimePerPlayer(any());
    }

    // =====================================================================
    // PLAYER HEATMAP (`/player/{id}/heatmap`)
    // =====================================================================

    // The heatmap endpoint has the most complex branching: 4 optional parameters (`rows`, `cols`,
    // `fieldWidth`, `fieldHeight`). The rule in the controller is:
    //   - NO parameter at all -> the simple overload `generatePlayerHeatmap(frames, id)`.
    //   - ANY parameter       -> the full method, filling the missing ones with `MatchConstants`.
    // The three tests below cover: none, only one, and all of them.

    // Path 1: no query parameters.
    @Test
    public void shouldUseDefaultHeatmapWhenNoParametersAreProvided() throws Exception {

        // ARRANGE: The fake service returns a tiny 2x3 grid, enough to check the JSON structure.
        // LEARNING NOTE: We build a REAL `HeatmapGrid` here (it is just a data holder, not a
        // service), so it is fine to use it instead of mocking it.
        List<FrameData> frames = new ArrayList<>();
        when(frameParser.loadSampleData()).thenReturn(frames);
        when(analyticsService.generatePlayerHeatmap(frames, 7))
                .thenReturn(new HeatmapGrid(2, 3, 105.0, 68.0));

        // ACT + ASSERT
        // The JSON fields come from the getters of `HeatmapGrid`: `getRows()` -> `rows`,
        // `getCols()` -> `cols` and `getGrid()` -> `grid`.
        //
        // LEARNING NOTE: `hasSize(2)` (Hamcrest matcher)
        // `jsonPath` accepts either a plain value or a "matcher". `hasSize(2)` means "this JSON
        // array has exactly 2 elements". The grid has one inner array per row, so 2 rows = size 2.
        // Hamcrest comes bundled with `spring-boot-starter-test`.
        mockMvc.perform(get("/api/v1/analytics/player/7/heatmap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").value(2))
                .andExpect(jsonPath("$.cols").value(3))
                .andExpect(jsonPath("$.grid", hasSize(2)));

        // VERIFY: Simple overload used, full method never called.
        // The full method has 6 parameters, so the wildcard needs 6 as well:
        // (list, id, rows, cols, fieldWidth, fieldHeight).
        verify(analyticsService).generatePlayerHeatmap(frames, 7);
        verify(analyticsService, never())
                .generatePlayerHeatmap(any(), anyInt(), anyInt(), anyInt(), anyDouble(), anyDouble());
    }

    // Path 2: only ONE parameter (`rows=20`). The controller must fill the other three with the
    // constants from `MatchConstants`. This is the "graceful fallback" logic with ternary operators.
    @Test
    public void shouldFillMissingHeatmapParametersWithDefaultsWhenOnlyRowsIsProvided() throws Exception {

        // ARRANGE: We stub the FULL method with exactly the values we EXPECT the controller to
        // build: rows = 20 (from the URL) and the other three taken from `MatchConstants`.
        // If the controller used a different fallback, the stub would not match and the response
        // would be empty, so the test would fail.
        List<FrameData> frames = new ArrayList<>();
        when(frameParser.loadSampleData()).thenReturn(frames);
        when(analyticsService.generatePlayerHeatmap(frames, 7, 20,
                MatchConstants.DEFAULT_GRID_COLS, MatchConstants.FIELD_LENGTH, MatchConstants.FIELD_WIDTH))
                .thenReturn(new HeatmapGrid(20, MatchConstants.DEFAULT_GRID_COLS,
                        MatchConstants.FIELD_LENGTH, MatchConstants.FIELD_WIDTH));

        // ACT + ASSERT: The URL has only `rows`. The columns must come back as the default (15).
        mockMvc.perform(get("/api/v1/analytics/player/7/heatmap?rows=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").value(20))
                .andExpect(jsonPath("$.cols").value(MatchConstants.DEFAULT_GRID_COLS));

        // VERIFY: The full method received the mixed values, and the simple overload was skipped.
        verify(analyticsService).generatePlayerHeatmap(frames, 7, 20,
                MatchConstants.DEFAULT_GRID_COLS, MatchConstants.FIELD_LENGTH, MatchConstants.FIELD_WIDTH);
        verify(analyticsService, never()).generatePlayerHeatmap(any(), anyInt());
    }

    // Path 3: ALL four parameters. Nothing should fall back to the constants, every value comes
    // from the URL. This also proves that `fieldWidth` and `fieldHeight` are bound correctly.
    @Test
    public void shouldForwardAllHeatmapParametersWhenAllAreProvided() throws Exception {

        // ARRANGE
        List<FrameData> frames = new ArrayList<>();
        when(frameParser.loadSampleData()).thenReturn(frames);
        when(analyticsService.generatePlayerHeatmap(frames, 7, 5, 8, 100.0, 60.0))
                .thenReturn(new HeatmapGrid(5, 8, 100.0, 60.0));

        // ACT + ASSERT
        // SYNTAX: Several query parameters are joined with `&`.
        mockMvc.perform(get("/api/v1/analytics/player/7/heatmap?rows=5&cols=8&fieldWidth=100.0&fieldHeight=60.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").value(5))
                .andExpect(jsonPath("$.cols").value(8))
                .andExpect(jsonPath("$.grid", hasSize(5)));

        // VERIFY: Every value from the URL reached the service.
        verify(analyticsService).generatePlayerHeatmap(frames, 7, 5, 8, 100.0, 60.0);
    }

    // =====================================================================
    // INTERNAL ERROR (500)
    // =====================================================================

    // TESTING: Simulating a failure with `thenThrow`
    // `when(...).thenReturn(...)` teaches a mock to answer normally. `when(...).thenThrow(...)`
    // teaches it to FAIL. Here we simulate the sample file being unreadable (for example, missing
    // from disk), which makes `frameParser.loadSampleData()` throw an `IOException`.
    //
    // The controller does not catch it (it just declares `throws IOException`), so the exception
    // goes up to the catch-all `handleGeneralException` in `GlobalExceptionHandler`, which must
    // answer HTTP 500 with a generic message.
    //
    // LEARNING NOTE: The test output will show an ERROR log with a stack trace for this test.
    // That is expected: the handler logs unexpected errors with `logger.error` (TA-67), and this
    // test deliberately triggers one. It does not mean the test failed.
    @Test
    public void shouldReturnInternalServerErrorWhenSampleDataCannotBeLoaded() throws Exception {

        // ARRANGE: Make the fake parser fail.
        // Mockito only accepts a checked exception here if the real method declares it
        // (`loadSampleData() throws IOException`), which protects us from impossible scenarios.
        when(frameParser.loadSampleData()).thenThrow(new IOException("sample file not found"));

        // ACT + ASSERT: `isInternalServerError()` checks that the HTTP status code is 500.
        // SECURITY NOTE: The message is the generic text from the handler. We also prove that the
        // real reason ("sample file not found") is NOT leaked to the client.
        mockMvc.perform(get("/api/v1/analytics/player/7/stats"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message")
                        .value("An internal error occurred while processing the analytics request."));

        // VERIFY: The parser failed before the service could be reached.
        verify(analyticsService, never()).calculatePlayerStats(any(), anyInt());
    }
}
