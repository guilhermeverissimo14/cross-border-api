package com.guilherme.crossborderapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CrossborderApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(CrossborderApiApplication.class, args);
		System.out.println("Swagger UI: http://localhost:8080/swagger-ui/index.html");
	}

}
