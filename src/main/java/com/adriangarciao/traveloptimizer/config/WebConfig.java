package com.adriangarciao.traveloptimizer.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final List<String> ALLOWED_ORIGINS =
            List.of(
                    "http://localhost:5173",
                    "https://*.up.railway.app",
                    "https://*.railway.app",
                    "https://*.vercel.app");

    private static final List<String> ALLOWED_METHODS =
            List.of("GET", "POST", "PUT", "DELETE", "OPTIONS");

    private static final List<String> ALLOWED_HEADERS =
            List.of("Content-Type", "Authorization", "X-Client-Id");

    /** Used by Spring Security's CorsFilter via cors(Customizer.withDefaults()). */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(ALLOWED_ORIGINS);
        config.setAllowedMethods(ALLOWED_METHODS);
        config.setAllowedHeaders(ALLOWED_HEADERS);
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /** Spring MVC CORS — covers any request that reaches DispatcherServlet directly. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(ALLOWED_ORIGINS.toArray(String[]::new))
                .allowedMethods(ALLOWED_METHODS.toArray(String[]::new))
                .allowedHeaders(ALLOWED_HEADERS.toArray(String[]::new))
                .allowCredentials(true);
    }
}
