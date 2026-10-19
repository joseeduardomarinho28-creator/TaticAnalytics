package com.taticanalytics.exception;

// LIBRARY: Standard Java Time API (Introduced in Java 8)
import java.time.LocalDateTime;

// LIBRARY: Java Collections Framework
// We use LinkedHashMap instead of HashMap because LinkedHashMap preserves the exact order 
// in which we insert the keys. This ensures our JSON output always looks neat and predictable.
import java.util.LinkedHashMap;
import java.util.Map;

// LIBRARY: SLF4J (Simple Logging Facade for Java)
// SLF4J is the standard logging "interface" in the Java world. Spring Boot already ships with it
// (through `spring-boot-starter-web` -> `spring-boot-starter-logging`), so no new dependency is needed.
// `Logger` is what we call to write messages; `LoggerFactory` is what creates a Logger for a class.
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// LIBRARY: Spring Web & HTTP 
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

// PURPOSE:
// This class acts as a global "safety net" or "interceptor" for your entire API.
// Without it, if your code crashes, Spring returns a messy, hard-to-read HTML "Whitelabel Error Page" 
// or dumps the Java stack trace to the client. This class catches those crashes and converts them 
// into clean, standardized JSON error messages.

// SPRING ANNOTATION: `@ControllerAdvice`
// This tells Spring: "Wrap yourself around all `@RestController` classes in the project. 
// If any of them throw an exception, pause the execution and send the exception here first."
@ControllerAdvice
public class GlobalExceptionHandler {

    // LEARNING NOTE: Logging vs. responding to the client
    // These are two different audiences. The JSON body goes to the CLIENT (and must stay neutral,
    // so we never leak internal details). The log goes to the DEVELOPER (and must be detailed,
    // so we can find out why the API broke). Hiding an error from the client is a security choice;
    // hiding it from the developer is just a blind spot.
    //
    // OOP CONCEPT: `private static final`
    // `static` = one single Logger shared by the whole class (not one per object).
    // `final` = the reference never changes after being set.
    // We pass `GlobalExceptionHandler.class` so every log line is labeled with the class that wrote it.
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // SPRING ANNOTATION: `@ExceptionHandler`
    // Tells Spring exactly which exception this specific method is responsible for handling.
    
    // SCENARIO 1: The user types a letter instead of a number in the URL.
    // Example: GET /api/v1/analytics/player/ABC/stats (Expects an integer ID).
    // Spring tries to convert "ABC" to an int, fails, and throws MethodArgumentTypeMismatchException.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleTypeMismatch(MethodArgumentTypeMismatchException ex, WebRequest request) {
        
        // LEARNING NOTE: Log levels (WARN vs ERROR)
        // A 400 is the CLIENT's mistake, not a failure of the server, so we use WARN instead of ERROR.
        // We still log it: if a frontend keeps sending bad requests, this is how we find out.
        // We don't pass `ex` here on purpose: the cause is already fully described by the message,
        // and a full stack trace for a simple bad URL would only be noise.
        logger.warn("Parâmetro inválido em {}: '{}' deveria ser {}",
                request.getDescription(false), ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");

        // We build a custom JSON body using a Map.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        // BAD_REQUEST = HTTP 400 (Client error: The user sent invalid data)
        body.put("status", HttpStatus.BAD_REQUEST.value()); 
        body.put("error", "Invalid Parameter Type");
        
        // We dynamically build a helpful message telling the user exactly which parameter failed.
        // e.g., "Parameter 'id' should be of type int"
        body.put("message", String.format("Parameter '%s' should be of type %s", 
                ex.getName(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown"));

        // `ResponseEntity` is Spring's wrapper that contains both the JSON body and the HTTP Status Code.
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    // SCENARIO 2: A business rule is broken (e.g., passing a negative radius for possession).
    // If you manually write `throw new IllegalArgumentException("Radius cannot be negative");` 
    // anywhere in your services, it gets caught right here.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
        // Same reasoning as above: client error -> WARN, no stack trace.
        logger.warn("Requisição inválida em {}: {}", request.getDescription(false), ex.getMessage());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        // We extract the exact message you wrote when you threw the exception.
        body.put("message", ex.getMessage());

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    // SCENARIO 3: The Ultimate Safety Net (Catch-All)
    // If a NullPointerException, IOException, or any other completely unexpected crash happens, 
    // it falls back to this method because all exceptions inherit from the base `Exception` class.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneralException(Exception ex, WebRequest request) {

        // LEARNING NOTE: Passing the exception as the LAST argument
        // `{}` is a placeholder filled by the arguments in order. When the very last argument is a
        // Throwable (and there is no `{}` left for it), SLF4J understands it as "the exception" and
        // prints the full stack trace. If we wrote `ex.getMessage()` instead, we would lose the
        // stack trace (the exact line where it broke), which is the most valuable part.
        // `request.getDescription(false)` returns which endpoint was called (e.g., "uri=/api/v1/...").
        logger.error("Erro inesperado ao processar {}", request.getDescription(false), ex);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        
        // INTERNAL_SERVER_ERROR = HTTP 500 (Server error: Our code broke unexpectedly)
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal Server Error");
        
        // SECURITY/UX BEST PRACTICE: 
        // We DO NOT send `ex.getMessage()` or the stack trace to the user. 
        // We send a generic, polite message to prevent leaking sensitive system details to the outside world.
        body.put("message", "An internal error occurred while processing the analytics request.");

        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}