package com.tripdiary;

import org.springframework.boot.SpringApplication;

/** Testcontainers の PostgreSQL を使ってアプリをローカル起動する（./gradlew bootTestRun）。 */
public class TestTripDiaryApplication {

    public static void main(String[] args) {
        SpringApplication.from(TripDiaryApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
