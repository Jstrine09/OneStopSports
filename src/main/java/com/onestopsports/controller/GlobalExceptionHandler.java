package com.onestopsports.controller;

import com.onestopsports.dto.ErrorResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

// @RestControllerAdvice makes this class a global error handler.
// Any exception thrown from any @RestController in the app will land here
// instead of bubbling up as an ugly 500 with a Java stack trace in the response.
//
// Each @ExceptionHandler method handles a specific exception type and returns
// a consistent ErrorResponseDto JSON so the frontend always gets the same shape.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── 400 Bad Request — validation failure ─────────────────────────────────
    // Thrown when a request body fails @Valid checks — e.g. blank username on register,
    // or a missing required field. We collect all field errors into one readable string.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidation(MethodArgumentNotValidException ex) {
        // Each FieldError tells us which field failed and why — e.g. "username: must not be blank"
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto.of(400, "Bad Request", message));
    }

    // ── 400 Bad Request — unreadable JSON ────────────────────────────────────
    // Thrown when the request body is not valid JSON at all — e.g. a missing closing brace,
    // or sending plain text where JSON is expected.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDto> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto.of(400, "Bad Request", "Malformed or missing request body"));
    }

    // ── 400 Bad Request — wrong type in a path/query param ───────────────────
    // Thrown when a path variable or query param can't be converted to the expected
    // type — e.g. GET /api/players/abc where {id} is a Long, or a malformed ?date=.
    // Without this it bubbles to the 500 catch-all.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDto> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value for '" + ex.getName() + "'";
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto.of(400, "Bad Request", message));
    }

    // ── 400 Bad Request — missing required query param ───────────────────────
    // Thrown when a required @RequestParam is absent — e.g. GET /api/search with no ?q=.
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponseDto> handleMissingParam(MissingServletRequestParameterException ex) {
        String message = "Missing required parameter '" + ex.getParameterName() + "'";
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto.of(400, "Bad Request", message));
    }

    // ── 405 Method Not Allowed — wrong HTTP verb ─────────────────────────────
    // Thrown when the path exists but the method doesn't — e.g. POST /api/sports.
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponseDto> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorResponseDto.of(405, "Method Not Allowed",
                        "The " + ex.getMethod() + " method is not supported for this endpoint"));
    }

    // ── 401 Unauthorized — wrong credentials ─────────────────────────────────
    // Thrown by Spring Security's AuthenticationManager when the username exists
    // but the password is wrong during login.
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponseDto> handleBadCredentials(BadCredentialsException ex) {
        // Deliberately vague — we don't want to confirm whether the username exists
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponseDto.of(401, "Unauthorized", "Invalid username or password"));
    }

    // ── 403 Forbidden — authenticated but not allowed ────────────────────────
    // Thrown when a logged-in user tries to access something they don't have permission for.
    // This is different from 401 (not logged in at all).
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDto> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ErrorResponseDto.of(403, "Forbidden", "You don't have permission to access this resource"));
    }

    // ── Passthrough — ResponseStatusException ────────────────────────────────
    // ResponseStatusException is Spring's own way of attaching an HTTP status to an exception.
    // UserService uses it e.g. new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found").
    // Without this handler, our generic Exception catch-all would intercept it and return 500.
    // Here we read the status code the exception already carries and pass it straight through.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponseDto> handleResponseStatus(ResponseStatusException ex) {
        int status = ex.getStatusCode().value();
        // getReason() is the short message set on the exception — e.g. "User not found: james"
        String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(ErrorResponseDto.of(status, HttpStatus.resolve(status) != null
                        ? HttpStatus.resolve(status).getReasonPhrase() : "Error", message));
    }

    // ── 409 Conflict — unique constraint violation ────────────────────────────
    // Thrown by the database when we try to insert a duplicate value into a unique column.
    // In this app the most common case is registering with an already-taken username or email.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDto> handleDataIntegrity(DataIntegrityViolationException ex) {
        // Don't expose the raw SQL error — just say the resource already exists
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponseDto.of(409, "Conflict", "A resource with that value already exists"));
    }

    // ── 404 Not Found — no route/resource matches the path ────────────────────
    // Spring Boot 3.2+ throws NoResourceFoundException when a request matches no
    // controller and no static file — e.g. GET /api/does-not-exist. In this app
    // SpaForwardingConfig deliberately returns null (instead of the SPA shell) for
    // api/, ws, swagger-ui and v3/api-docs paths, so those unknown BACKEND routes
    // land here. Without this handler they would fall through to the 500 catch-all
    // below and report a server error for what is really a missing route — a 404.
    // (Genuine client-side SPA routes never reach here: they are served index.html.)
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponseDto.of(404, "Not Found", "The requested resource was not found"));
    }

    // ── 500 Internal Server Error — unexpected failures ───────────────────────
    // Catch-all for anything we haven't specifically handled above.
    // We log the full stack trace on the server so we can debug it,
    // but only send a vague message to the client (never expose internal details).
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGeneric(Exception ex) {
        log.error("[GlobalExceptionHandler] Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponseDto.of(500, "Internal Server Error", "An unexpected error occurred"));
    }
}
