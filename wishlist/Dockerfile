# Step 1: Base 이미지 설정
FROM openjdk:17-jdk-slim

# Step 2: 작업 디렉토리 생성
WORKDIR /app

# Step 3: 빌드된 JAR 파일 복사
COPY build/libs/wishlist-0.0.1-SNAPSHOT.jar app.jar


# Step 4: 컨테이너에서 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# Step 5: Eureka Server에서 사용하는 포트 노출
EXPOSE 8083