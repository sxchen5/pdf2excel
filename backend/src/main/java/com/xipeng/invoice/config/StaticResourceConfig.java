package com.xipeng.invoice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.ResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Serves the Vue SPA from classpath:/static/ and falls back to index.html for client routes.
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }

    private static final class SpaFallbackResolver implements ResourceResolver {

        @Override
        public Resource resolveResource(HttpServletRequest request, String requestPath,
                                        List<? extends Resource> locations, ResourceResolverChain chain) {
            Resource resolved = chain.resolveResource(request, requestPath, locations);
            if (resolved != null) {
                return resolved;
            }
            if (requestPath.startsWith("api/")) {
                return null;
            }
            return new ClassPathResource("/static/index.html");
        }

        @Override
        public String resolveUrlPath(String resourcePath, List<? extends Resource> locations,
                                     ResourceResolverChain chain) {
            return chain.resolveUrlPath(resourcePath, locations);
        }
    }
}
