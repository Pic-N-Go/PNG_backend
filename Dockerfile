# 멀티스테이지 빌드. 최종 이미지에는 JDK·Gradle 캐시가 남지 않고 JRE + jar만 남는다.
#
# eclipse-temurin은 공식 멀티아치 이미지라, 이 Dockerfile은 로컬(x86)에서든
# 오라클 클라우드 Ampere(ARM64) 서버에서든 별도 설정 없이 `docker build`만으로
# 해당 아키텍처에 맞는 레이어를 알아서 받는다.

# ---- Build stage ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# 의존성 레이어를 소스 레이어보다 먼저 캐싱한다. build.gradle이 안 바뀌면
# 소스만 고쳐도 의존성을 다시 안 받는다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# root로 돌리지 않는다. 컨테이너가 뚫려도 프로세스 권한을 최소화한다.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080

# JAVA_OPTS는 이미지 재빌드 없이 docker-compose 쪽에서 덮어쓸 수 있게 비워둔다.
# 컨테이너 메모리 제한을 인식하도록 MaxRAMPercentage를 기본값으로 준다
# (오라클 A1.Flex 1~2 OCPU 소형 인스턴스라 힙을 과하게 잡으면 다른 컨테이너가 OOM 킬된다).
# alpine 기본값은 UTC다. 콘테스트 발표 시각(오전 9시)처럼 벽시계로 약속한 값이
# 9시간 어긋나고, 스케줄러·EXIF 파싱도 같은 영향을 받는다.
ENV TZ=Asia/Seoul
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70.0"

# Actuator health 엔드포인트로 생존 확인. 로컬 검증 중 swagger-ui를 써봤다가
# application-prod.yaml에서 Swagger 자체를 꺼버려(springdoc.*.enabled: false) 헬스체크가
# 항상 실패하는 자기모순을 실제로 겪었다. Actuator는 그 설정과 무관하게 항상 떠 있다.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
