package com.taticanalytics.config;

// LIBRARY: Spring Context & Web Config
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// PURPOSE:
// This class configures CORS (Cross-Origin Resource Sharing) for the application.
// By default, web browsers block web pages from making requests to a different domain, 
// port, or protocol than the one that served the web page (Same-Origin Policy).
// This configuration tells the browser: "It is safe to let outside applications talk to this API."

// SPRING ANNOTATION: `@Configuration`
// Tells Spring Boot that this class contains application-level configuration.
// Spring reads this file during startup before establishing the web server rules.
@Configuration
public class WebConfig implements WebMvcConfigurer {

    // JAVA CONCEPT: Method Overriding
    // `WebMvcConfigurer` is an interface with many default methods for configuring Spring MVC.
    // By overriding `addCorsMappings`, we are injecting our custom CORS rules into the Spring framework.
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        
        // 1. WHICH endpoints are affected?
        // `/api/**` means: Apply this rule to any URL starting with "/api/". 
        // The `**` is a wildcard matching any nested path (e.g., /api/v1/analytics/player/1/stats).
        registry.addMapping("/api/**")
        
                // 2. WHO can call the API?
                // `*` means ALL origins are allowed. 
                // A front-end running on http://localhost:3000 (React/Vue) or a Python script 
                // can successfully request data. (Note: In production, you usually replace "*" 
                // with your actual front-end domain, like "https://meusite.com").
                .allowedOrigins("*")
                
                // 3. WHAT actions can they perform?
                // Explicitly listing the allowed HTTP methods. 
                // "OPTIONS" is critical here: browsers send an invisible "OPTIONS" request (a preflight check) 
                // before sending a POST/PUT/DELETE to ask the server for permission.
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                
                // 4. WHAT headers can they send?
                // `*` allows the client to send any HTTP headers (like Authorization tokens, Content-Type, etc.).
                .allowedHeaders("*")
                
                // 5. CACHING the permission
                // Tells the browser to remember this CORS permission for 3600 seconds (1 hour).
                // This improves performance because the browser won't have to send that invisible "OPTIONS" 
                // preflight request before every single actual request.
                .maxAge(3600);
    }
}