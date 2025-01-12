package com.sparta.product;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ProductApplication {

	public static void main(String[] args) {

		// .env 파일 경로 명시
		Dotenv dotenv = Dotenv.configure()
							  .directory("/Users/hyeokchoi/sparta/haengye_project/haengye_project/product/src/main/resources") // .env 파일 경로
							  .filename(".env")                 // .env 파일 이름
							  .load();

		// 환경 변수 값을 시스템 속성으로 설정
		dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

		SpringApplication.run(ProductApplication.class, args);
	}

}
