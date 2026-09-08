# Step 1: Base 이미지 설정
# openjdk:17-jdk-slim은 2022년에 OpenJDK 프로젝트가 공식 이미지를 deprecated 처리하면서 Docker Hub에서 내려갔다(더는 pull 불가).
# eclipse-temurin은 그 후속으로 업계 표준이 된 유지보수 중인 이미지다. jar만 실행하면 되므로 JDK 전체 대신 더 가벼운 JRE로 충분하다.
# alpine 태그는 amd64만 지원해서 arm64(애플 실리콘)에서는 못 받아온다. 멀티 아키텍처를 지원하는 일반(Debian 기반) 태그를 쓴다.
FROM eclipse-temurin:17-jre

# Step 2: 작업 디렉토리 생성
WORKDIR /app

# Step 3: 빌드된 JAR 파일 복사
COPY build/libs/wishlist-0.0.1-SNAPSHOT.jar app.jar


# Step 4: 컨테이너에서 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# Step 5: Eureka Server에서 사용하는 포트 노출
EXPOSE 8083