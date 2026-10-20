# syntax=docker/dockerfile:1

# ===== 1단계: 빌드 =====
# $BUILDPLATFORM: Mac(ARM)에서 네이티브로 실행되어 빠름 (jar는 플랫폼 무관)
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# 의존성 레이어 캐시: 빌드 설정 파일만 먼저 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle* settings.gradle* ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null 2>&1 || true

# 소스 복사 후 실행 가능한 jar 빌드 (테스트는 생략)
COPY src src
RUN ./gradlew bootJar --no-daemon -x test \
 && find build/libs -name "*.jar" ! -name "*-plain.jar" -exec cp {} app.jar \;

# ===== 2단계: 실행 =====
# --platform 값에 맞는 JRE 이미지가 자동 선택됨 (linux/arm64 → t4g, linux/amd64 → t3)
FROM eclipse-temurin:25-jre
WORKDIR /app

# root가 아닌 전용 사용자로 실행 (로그 디렉터리는 미리 만들고 소유권 부여)
RUN groupadd --system spring && useradd --system --gid spring spring \
 && mkdir -p /app/logs && chown -R spring:spring /app
USER spring

COPY --from=build /workspace/app.jar app.jar

EXPOSE 8080

# 힙을 고정값 대신 메모리 비율로 지정 (t3.small/medium 어디서든 동작)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70.0"

# exec: 종료 신호(SIGTERM)가 java 프로세스에 직접 전달되도록
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]