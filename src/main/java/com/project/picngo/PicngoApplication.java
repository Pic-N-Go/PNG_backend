package com.project.picngo;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class PicngoApplication {

	public static void main(String[] args) {

		SpringApplication.run(PicngoApplication.class, args);
	}

}
