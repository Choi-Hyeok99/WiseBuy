package com.sparta.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
public class OrderApplication {

    public static void main(String[] args) {
        // 예전엔 dotenv 라이브러리로 .env를 직접 읽어 시스템 프로퍼티에 넣었지만,
        // 이 컴퓨터에만 있는 절대경로(/Users/hyeokchoi/...)라 다른 환경(도커 컨테이너 등)에서는 무조건 실패했다.
        // Spring Boot는 OS 환경변수를 application.yml의 ${DB_URL} 같은 플레이스홀더에 자동으로 바인딩해주므로,
        // 값 주입은 docker-compose(또는 로컬 셸의 export)가 맡고 이 코드는 Spring 부트스트랩만 담당한다.
        SpringApplication.run(OrderApplication.class, args);
    }

}
