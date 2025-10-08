package com.prosper.prospermentor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ProsperMentorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProsperMentorApplication.class, args);
    }

}
