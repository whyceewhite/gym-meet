package doubletuck.meet.advice;

import doubletuck.meet.exception.ConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflictException(ConflictException ex) {
        ErrorResponse errorResponse = ErrorResponse.builder(
                ex,
                HttpStatus.CONFLICT,
                ex.getMessage()
        ).build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }
}
