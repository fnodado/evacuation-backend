package com.evacuation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Resolve upload dir to absolute path so it works regardless of JVM working dir
        String absoluteUploadPath = Paths.get(uploadDir).toAbsolutePath().toString()
                .replace("\\", "/");

        // Map /uploads/** → {uploadDir}/
        // e.g. GET /uploads/blueprints/foo.png → {uploadDir}/blueprints/foo.png
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + absoluteUploadPath + "/");
    }
}
