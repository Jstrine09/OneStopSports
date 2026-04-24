package com.onestopsports.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Configures the auto-generated Swagger UI at /swagger-ui/index.html
// Springdoc scans all @RestController classes and builds the API spec automatically —
// this class just provides the title, description, and JWT auth configuration so
// you can call protected endpoints directly from the browser UI.
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        // "bearerAuth" is the name we give the security scheme — it's referenced below
        // in SecurityRequirement to tell Swagger "use this scheme for all endpoints".
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        // Title and description shown at the top of the Swagger UI page
                        .title("OneStopSports API")
                        .description("""
                                REST API for OneStopSports — live scores, standings, squads, and favourites.

                                **Auth:** Most GET endpoints are public. POST /auth/login returns a JWT token.
                                Click **Authorize** above and paste the token (without "Bearer ") to call protected endpoints.
                                """)
                        .version("1.0.0"))

                // Tell Swagger that protected endpoints need a Bearer JWT in the Authorization header.
                // This adds the "Authorize" padlock button to the Swagger UI so you can paste your token
                // and it will be included automatically in every request you make from the UI.
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP) // HTTP scheme, not API key or OAuth
                                .scheme("bearer")               // "bearer" tells Swagger the token prefix is "Bearer "
                                .bearerFormat("JWT")));         // Just a hint shown in the UI — not enforced
    }
}
