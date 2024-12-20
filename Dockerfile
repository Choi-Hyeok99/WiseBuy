# Base image로 OpenJDK 17 사용
FROM openjdk:17-jdk-slim

# 작업 디렉토리 설정
WORKDIR /app

# 빌드된 JAR 파일을 컨테이너로 복사
COPY ./build/libs/haengye_project-0.0.1-SNAPSHOT.jar /app/haengye_project.jar

RUN apt-get update && apt-get install -y iputils-ping netcat default-mysql-client

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "haengye_project.jar"]

# 컨테이너가 사용하는 포트 설정
EXPOSE 8090
