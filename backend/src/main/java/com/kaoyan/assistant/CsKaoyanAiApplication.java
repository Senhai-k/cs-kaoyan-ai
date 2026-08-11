package com.kaoyan.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CsKaoyanAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CsKaoyanAiApplication.class, args);
    }
}
