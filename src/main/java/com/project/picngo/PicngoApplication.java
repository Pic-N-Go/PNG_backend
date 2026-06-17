package com.project.picngo;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class PicngoApplication {

	public static void main(String[] args) {

		// TODO: 하드코딩된 .env 경로를 제거하고 공통 환경변수 로딩 방식으로 대체 고려
		Dotenv dotenv = Dotenv.configure()
				.directory("C:/picngo/picngo")
				.ignoreIfMissing()
				.load();

		dotenv.entries().forEach(entry ->
				System.setProperty(entry.getKey(), entry.getValue())
		);

		SpringApplication.run(PicngoApplication.class, args);
	}

}
