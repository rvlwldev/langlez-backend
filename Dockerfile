FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY . .
# Gradle Wrapper 실행 권한 부여
RUN chmod +x gradlew
# 테스트 제외하고 빌드
RUN ./gradlew :app:api:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/app/api/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
