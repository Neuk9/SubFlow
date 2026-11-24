# 1단계: Gradle 빌드
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

# 의존성 캐시를 먼저 받아 소스 변경 시 재빌드를 줄인다.
COPY gradle/ gradle/
COPY gradlew settings.gradle build.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon >/dev/null

COPY src/ src/
RUN ./gradlew bootJar --no-daemon

# 2단계: JRE 실행 이미지
FROM eclipse-temurin:21-jre

# 이미지도 non-root(UID/GID 1000)로 실행한다.
# Temurin JRE 이미지에 기본 제공되는 ubuntu(1000) 계정을 제거하고 전용 계정을 만든다.
RUN userdel --remove ubuntu \
    && groupadd --gid 1000 subflow \
    && useradd --uid 1000 --gid 1000 --no-create-home --shell /usr/sbin/nologin subflow \
    && mkdir -p /uploads \
    && chown 1000:1000 /uploads

WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar

USER 1000
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
