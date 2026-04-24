package com.onestopsports.dto;

import java.time.Instant;

// Standard error envelope returned by GlobalExceptionHandler for every non-2xx response.
// Using a record keeps it immutable and concise — no setters, no boilerplate.
//
// Example JSON output:
//   {
//     "status": 400,
//     "error": "Bad Request",
//     "message": "username: must not be blank",
//     "timestamp": "2025-04-24T14:30:00Z"
//   }
public record ErrorResponseDto(
        int status,       // HTTP status code — e.g. 400, 401, 404, 500
        String error,     // Short human-readable label for the status — e.g. "Bad Request"
        String message,   // Specific detail about what went wrong
        Instant timestamp // When the error occurred — useful for log correlation
) {
    // Convenience factory so callers don't have to pass Instant.now() every time
    public static ErrorResponseDto of(int status, String error, String message) {
        return new ErrorResponseDto(status, error, message, Instant.now());
    }
}
