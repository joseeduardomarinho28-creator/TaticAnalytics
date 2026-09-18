package com.taticanalytics.exception;

// LIBRARY: Standard Java Time API (Introduced in Java 8)
import java.time.LocalDateTime;

// LIBRARY: Java Collections Framework
// We use LinkedHashMap instead of HashMap because LinkedHashMap preserves the exact order 
// in which we insert the keys. This ensures our JSON output always looks neat and predictable.
import java.util.LinkedHashMap;
import java.util.Map;

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

    // SPRING ANNOTATION: `@ExceptionHandler`
    // Tells Spring exactly which exception this specific method is responsible for handling.
    
    // SCENARIO 1: The user types a letter instead of a number in the URL.
    // Example: GET /api/v1/analytics/player/ABC/stats (Expects an integer ID).
    // Spring tries to convert "ABC" to an int, fails, and throws MethodArgumentTypeMismatchException.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleTypeMismatch(MethodArgumentTypeMismatchException ex, WebRequest request) {
        
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