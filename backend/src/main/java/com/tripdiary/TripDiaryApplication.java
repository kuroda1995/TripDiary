package com.tripdiary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TripDiaryApplication {

    public static void main(String[] args) {
        SpringApplication.run(TripDiaryApplication.class, args);
    }
}
