package com.adaptivefit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AdaptiveFitApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdaptiveFitApplication.class, args);
    }
}
