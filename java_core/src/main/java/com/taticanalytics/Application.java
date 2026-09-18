package com.taticanalytics;

// SPRING BOOT IMPORTS
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// SPRING BOOT CONCEPT: @SpringBootApplication
// This is a "meta-annotation" that combines three crucial Spring Boot annotations under the hood:
// 1. @Configuration: Tags this class as a source of bean definitions for the Spring IoC container.
// 2. @EnableAutoConfiguration: Tells Spring Boot to start adding beans based on classpath settings,
//    other beans, and property settings (e.g., auto-configuring an embedded Tomcat web server).
// 3. @ComponentScan: Tells Spring to scan for other components, services, and REST controllers 
//    in the com.taticanalytics package hierarchy.
@SpringBootApplication
public class Application {

    // STANDARD JAVA ENTRY POINT
    // Every standalone Java application starts execution here. 
    // Spring Boot leverages this standard lifecycle to bootstrap the framework.
    public static void main(String[] args) {
        
        // BOOTSTRAP METHOD
        // SpringApplication.run() initializes the ApplicationContext (IoC container),
        // registers components like @Service and @Component classes, and spins up 
        // the embedded web server (Tomcat on port 8080).
        SpringApplication.run(Application.class, args);
    }
}