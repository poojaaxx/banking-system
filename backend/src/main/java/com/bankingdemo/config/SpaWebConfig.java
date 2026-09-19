package com.bankingdemo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the built React SPA from the classpath (packaged into this jar by
 * the Docker build) at the same origin as the API. Real static files (JS,
 * CSS, images -- anything with a file extension) are served as-is, with
 * Spring's normal content-type detection, and 404 cleanly if missing. Any
 * other GET (no file extension -- a client-side route like /accounts/42)
 * falls back to index.html so React Router can take over.
 *
 * The mapped patterns use a regex path variable to exclude "api" and
 * "actuator" as a first path segment, so this handler's mapping never even
 * matches those prefixes -- for any other HTTP method (POST/PUT/PATCH/DELETE)
 * on an unmapped /api/** path, ResourceHttpRequestHandler would otherwise
 * "find" index.html and then reject the method with a 500 rather than a 404
 * (it only checks GET/HEAD support after resolving a resource). A plain "/**"
 * pattern was tried first and hit exactly this bug during manual testing.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/", "/{spring:^(?!api|actuator).*$}", "/{spring:^(?!api|actuator).*$}/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        boolean looksLikeFileRequest = resourcePath.contains(".");
                        if (looksLikeFileRequest) {
                            return null;
                        }
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
