package com.onestopsports.security;

// Thrown by AuthRateLimiter when a client has made too many requests to an auth
// endpoint (login/register) inside the allowed time window. It carries the number
// of seconds the client should wait before trying again — GlobalExceptionHandler
// reads that value to build the HTTP 429 response and the standard "Retry-After"
// header.
//
// We extend RuntimeException (unchecked) so we don't have to declare "throws"
// everywhere — Spring's GlobalExceptionHandler catches it centrally, exactly the
// same way it already handles BadCredentialsException and the others.
public class RateLimitExceededException extends RuntimeException {

    // How long (in whole seconds) the caller should back off before retrying.
    // Used as the value of the HTTP "Retry-After" response header.
    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        // The message is only used for server-side logging — the client gets our
        // clean JSON envelope from GlobalExceptionHandler, not this raw string.
        super("Rate limit exceeded; retry after " + retryAfterSeconds + "s");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
