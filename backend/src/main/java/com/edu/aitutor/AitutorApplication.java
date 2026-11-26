package com.edu.aitutor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class AitutorApplication {
	public static void main(String[] args) {
		SpringApplication.run(AitutorApplication.class, args);
	}
}
