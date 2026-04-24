package com.onestopsports.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

// A JWT (JSON Web Token) is a small, signed piece of text that proves who a user is.
// When a user logs in, we give them a token. They include it in every future request
// so the server knows who they are without needing to check the database each time.
// This class handles creating and reading those tokens.
@Component // Makes this available for Spring to inject wherever it's needed
public class JwtUtil {

    // The secret key used to sign tokens — loaded from application.yml
    // Must be kept private; if someone gets this, they can forge tokens
    @Value("${jwt.secret}")
    private String secret;

    // How long a token is valid before it expires (in milliseconds) — defaults to 24 hours
    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    // Creates a new JWT token for the given username.
    // The token is signed with our secret key so we can verify it later.
    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username)                                               // Who this token is for
                .issuedAt(new Date())                                            // When it was created
                .expiration(new Date(System.currentTimeMillis() + expirationMs)) // When it expires
                .signWith(getSigningKey())                                        // Sign it with our secret
                .compact();                                                       // Convert to a compact string
    }

    // Pulls the username out of a token (e.g. to load the user from the database)
    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    // Returns true if the token is properly signed and not expired.
    // Returns false if it's been tampered with, expired, or is just garbage.
    public boolean validateToken(String token) {
        try {
            getClaims(token); // If this throws, the token is invalid
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // Parses the token and returns its payload (the "claims" — username, expiry, etc.)
    // This will throw an exception if the token is invalid or expired.
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey()) // Check the signature using our secret
                .build()
                .parseSignedClaims(token)   // Parse and verify the token
                .getPayload();              // Return the data inside the token
    }

    // Converts our Base64-encoded secret string into a proper cryptographic signing key
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes); // HMAC-SHA key used for signing and verifying
    }
}
