package com.onestopsports;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

// This is the main entry point of the entire Spring Boot application.
// When you run the app, Java calls main() which hands everything over to Spring.
@SpringBootApplication  // Tells Spring to auto-detect and wire up all the components in this package
@EnableCaching          // Turns on caching support (used by Redis to store live match data)
@EnableScheduling       // Allows methods to run automatically on a timer (e.g. refreshing live scores every 30s)
public class OneStopSportsApplication {

    public static void main(String[] args) {
        // Boots up the entire Spring application — starts the web server, connects to the DB, etc.
        SpringApplication.run(OneStopSportsApplication.class, args);
    }
}
