package com.sparta.order;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
public class OrderApplication {

    public static void main(String[] args) {

        // .env 파일 경로 명시
        Dotenv dotenv = Dotenv.configure()
                              .directory("/Users/hyeokchoi/sparta/haengye_project/haengye_project/order/src/main/resources") // .env 파일 경로
                              .filename(".env")                 // .env 파일 이름
                              .load();

        // 환경 변수 값을 시스템 속성으로 설정
        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

        SpringApplication.run(OrderApplication.class, args);
    }

}
