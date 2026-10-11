package com.crossborderpayment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.jspecify.annotations.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig {
    // Only the configured frontend origins (app.security.allowed-origins) may call the API cross-origin;
    // a wildcard would let any site read API responses.
    @Bean
    public WebMvcConfigurer corsConfigurer(AuthProperties properties){
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@NonNull CorsRegistry registry){
                registry.addMapping("/**")
                        .allowedOrigins(properties.allowedOrigins().toArray(String[]::new))
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
            }
        };
    }
}
