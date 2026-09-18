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
 * falls back to index.html so React Router can take over. This mapping never
 * intercepts /api/** or /actuator/**, since Spring MVC always tries
 * controller (@RequestMapping) and actuator endpoint mappings before falling
 * back to a resource handler mapped on "/**".
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
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
