package com.sparta.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UserApplication {

	public static void main(String[] args) {
		// 환경변수는 docker-compose(또는 셸 export)가 주입하고 Spring Boot가 application.yml의 ${...}에 바인딩한다.
		SpringApplication.run(UserApplication.class, args);
	}

}
