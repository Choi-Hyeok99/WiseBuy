package com.sparta.haengye_project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.sparta.haengye_project")
@EnableScheduling
public class HaengyeProjectApplication {

    public static void main(String[] args) {

        SpringApplication.run(HaengyeProjectApplication.class, args);
    }

}
