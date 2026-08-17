package com.wrj.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WrjPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(WrjPlatformApplication.class, args);
    }
}
