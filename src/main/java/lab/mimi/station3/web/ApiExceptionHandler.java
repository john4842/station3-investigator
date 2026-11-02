package lab.mimi.station3.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, IOException.class})
    public ResponseEntity<Map<String, Object>> badInput(Exception error) {
        return ResponseEntity.badRequest().body(Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "invalid_input",
                "message", safeMessage(error),
                "timestamp", Instant.now()));
    }

    @ExceptionHandler(DataAvailability.Unavailable.class)
    public ResponseEntity<Map<String, Object>> unavailable(DataAvailability.Unavailable error) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "status", 503, "error", "data_unavailable",
                "message", error.getMessage(), "timestamp", Instant.now()));
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
