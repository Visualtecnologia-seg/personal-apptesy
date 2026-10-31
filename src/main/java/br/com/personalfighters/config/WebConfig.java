package br.com.personalfighters.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Configura o serving do App Cliente (Expo web SPA) em /app/**
 * Se o ficheiro não existir, serve index.html (necessário para client-side routing).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/app/**")
                .addResourceLocations("classpath:/static/app/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource resource = super.getResource(resourcePath, location);
                        // Se o ficheiro existe, serve-o; senão, serve index.html (SPA fallback)
                        return (resource != null && resource.exists())
                                ? resource
                                : location.createRelative("index.html");
                    }
                });
    }
}
