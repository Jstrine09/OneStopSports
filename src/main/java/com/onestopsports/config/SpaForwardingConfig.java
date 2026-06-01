package com.onestopsports.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

// Serves the React single-page app and makes client-side routing work.
//
// The frontend is a SPA: routes like "/leagues", "/live", or "/team/42" exist
// only in React Router on the client — there are no matching files on the server.
// By default Spring Boot returns 404 for those paths (and for a hard refresh on
// them). This resolver fixes that: if the requested path is a real static file
// (index.html, /assets/*, icons, manifest) it serves it; otherwise it falls back
// to index.html so React Router can take over. API and WebSocket paths are left
// untouched so they 404 normally when wrong, rather than returning HTML.
@Configuration
public class SpaForwardingConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(@NonNull String resourcePath,
                                                   @NonNull Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);

                        // A real static asset (JS/CSS bundle, icon, manifest, index.html itself)
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }

                        // Never hijack backend routes — let them resolve/404 on their own.
                        if (resourcePath.startsWith("api/")
                                || resourcePath.startsWith("ws")
                                || resourcePath.startsWith("swagger-ui")
                                || resourcePath.startsWith("v3/api-docs")) {
                            return null;
                        }

                        // Otherwise it's a client-side route — serve the SPA shell.
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
