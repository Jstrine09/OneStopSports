package com.onestopsports.controller;

import com.onestopsports.dto.ErrorResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Tests for GlobalExceptionHandler, the app-wide @RestControllerAdvice.
//
// Two kinds of test live here:
//   1. DISPATCH-ORDER tests (the mandatory part) — these fire real HTTP requests at a
//      throwaway controller through MockMvc's standaloneSetup, so Spring's actual
//      @ExceptionHandler resolution logic decides which handler method runs. This is the
//      ONLY way to prove that ResponseStatusException is routed to its own passthrough
//      handler and NOT swallowed by the generic Exception catch-all — calling the handler
//      methods directly would only prove each method's own body/status, not which one
//      Spring picks for a given thrown exception.
//   2. DIRECT-CALL tests — for the remaining handlers, calling `new GlobalExceptionHandler()
//      .handleX(ex)` and asserting on the returned ResponseEntity is sufficient, since those
//      handlers' status/body mapping isn't in question, only their own logic.
class GlobalExceptionHandlerTest {

    // A tiny, test-only controller whose sole job is to throw the exceptions we want to
    // dispatch through the real Spring MVC exception-resolution pipeline.
    @RestController
    static class ThrowingTestController {

        @GetMapping("/test/not-found")
        void notFound() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found: 999");
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new RuntimeException("some internal secret detail that must never reach the client");
        }
    }

    // standaloneSetup builds a minimal MVC dispatcher around just this one controller,
    // with GlobalExceptionHandler registered as its @ControllerAdvice — no full Spring
    // context, no security filters, no database. Fast and focused purely on dispatch order.
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ThrowingTestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    // ── DISPATCH ORDER (mandatory) ─────────────────────────────────────────────

    @Test
    void responseStatusException_dispatchesToPassthroughHandler_notCatchAll() throws Exception {
        // A ResponseStatusException carries its own status (404 here) and message. Spring
        // must route it to handleResponseStatus — proving the passthrough handler is picked
        // over handleGeneric (the catch-all), which would otherwise wrongly return 500.
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Player not found: 999"));
    }

    @Test
    void unhandledRuntimeException_dispatchesToCatchAll_withoutLeakingRawMessage() throws Exception {
        // A plain RuntimeException has no specific handler, so it must fall through to
        // handleGeneric (500) — and the client-facing message must be the generic constant,
        // never the raw exception message (which could leak internal details).
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret detail"))));
    }

    // ── DIRECT-CALL handler mapping tests ───────────────────────────────────────

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBadCredentials_returns401WithVagueMessage() {
        // Deliberately vague message — must not confirm whether the username exists.
        ResponseEntity<ErrorResponseDto> response =
                handler.handleBadCredentials(new BadCredentialsException("Bad credentials"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(401);
        assertThat(response.getBody().message()).isEqualTo("Invalid username or password");
    }

    @Test
    void handleAccessDenied_returns403() {
        ResponseEntity<ErrorResponseDto> response =
                handler.handleAccessDenied(new AccessDeniedException("denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(403);
    }

    @Test
    void handleMethodNotSupported_returns405WithVerbInMessage() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException(HttpMethod.POST.name());

        ResponseEntity<ErrorResponseDto> response = handler.handleMethodNotSupported(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(405);
        assertThat(response.getBody().message()).contains("POST");
    }

    @Test
    void handleMissingParam_returns400NamingTheMissingParameter() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("q", "String");

        ResponseEntity<ErrorResponseDto> response = handler.handleMissingParam(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().message()).contains("q");
    }

    @Test
    void handleUnreadable_returns400WithGenericMalformedBodyMessage() {
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("bad json", (org.springframework.http.HttpInputMessage) null);

        ResponseEntity<ErrorResponseDto> response = handler.handleUnreadable(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("Malformed or missing request body");
    }

    @Test
    void handleDataIntegrity_returns409WithoutLeakingRawSqlMessage() {
        // The raw SQL exception message (e.g. constraint name) must never reach the client —
        // only the generic "already exists" message.
        DataIntegrityViolationException ex =
                new DataIntegrityViolationException("duplicate key value violates unique constraint \"uk_username\"");

        ResponseEntity<ErrorResponseDto> response = handler.handleDataIntegrity(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().message()).doesNotContain("uk_username");
    }
}
